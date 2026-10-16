import 'dart:io';

import 'package:flutter/foundation.dart';
import 'package:path/path.dart' as p;
import 'package:path_provider/path_provider.dart';
import '../../core/failures.dart';
import '../../domain/audio_source.dart';
import '../../domain/processing.dart';
import '../audio/chunk_planner.dart';
import '../audio/media_tools.dart';
import '../audio/transcript_merger.dart';
import 'engine.dart';
import 'job_store.dart';
import 'model_manager.dart';

/// Esito della trascrizione.
class TranscriptionOutcome {
  const TranscriptionOutcome({
    required this.text,
    required this.chunks,
    required this.elapsed,
    required this.modelName,
  });

  final String text;
  final int chunks;
  final Duration elapsed;
  final String modelName;
}

/// Orchestra la trascrizione on-device: preparazione audio, suddivisione in
/// blocchi per i file lunghi, avanzamento, annullamento e ripresa.
class TranscriptionService {
  TranscriptionService({
    MediaTools? tools,
    JobStore? jobStore,
    ModelManager? models,
    TranscriptionEngine? engine,
  })  : _tools = tools ?? const MediaTools(),
        _jobs = jobStore ?? JobStore(),
        _models = models ?? ModelManager() {
    this.engine = engine ?? WhisperEngine(models: _models);
  }

  final MediaTools _tools;
  final JobStore _jobs;
  final ModelManager _models;
  /// Motore in uso. Si cambia dalle impostazioni, anche fra
  /// un'elaborazione e l'altra.
  late TranscriptionEngine engine;
  static const TranscriptMerger _merger = TranscriptMerger();

  JobStore get jobs => _jobs;

  /// Trascrive [source]. Se [resume] è valorizzato, riprende i blocchi
  /// mancanti di un lavoro interrotto.
  Future<TranscriptionOutcome> transcribe({
    required AudioSource source,
    required ModelOption model,
    required String language,
    required void Function(ProcessingUpdate) onUpdate,
    required CancellationToken token,
    TranscriptionJob? resume,
  }) async {
    final TranscriptionEngine engine = this.engine;
    final DateTime started = DateTime.now();
    final Directory workDir = await _workDirectory();

    onUpdate(const ProcessingUpdate(
      stage: ProcessingStage.preparazione,
      detail: 'Analisi del file audio',
    ));

    // Il lavoro salvato si riprende solo se riguarda lo stesso file con lo
    // stesso modello e la stessa lingua: altrimenti la trascrizione
    // risulterebbe metà in un modo e metà nell'altro.
    final bool canResume = resume != null &&
        resume.audioPath == source.path &&
        resume.modelName == model.id &&
        resume.language == language &&
        !resume.isComplete;

    TranscriptionJob job;
    if (canResume) {
      job = resume;
      onUpdate(ProcessingUpdate(
        stage: ProcessingStage.preparazione,
        progress: job.fraction,
        detail: 'Ripresa: ${job.remaining} blocchi mancanti',
      ));
    } else {
      final List<AudioChunk> chunks =
          await _planFor(source, token, onUpdate, engine);
      job = TranscriptionJob(
        audioPath: source.path,
        displayName: source.displayName,
        modelName: model.id,
        language: language,
        totalMs: source.duration.inMilliseconds,
        chunks: chunks,
      );
      await _jobs.save(job);
    }

    token.throwIfCancelled();

    // Il motore deve essere pronto: per whisper significa modello
    // scaricato, per quello di sistema che il telefono lo offra.
    if (!await engine.isAvailable()) {
      throw AppFailure(
        engine.id == TranscriptionEngineId.whisper
            ? 'Il modello ${model.label} non è installato. Scaricalo dalle '
                'impostazioni prima di procedere.'
            : 'Il motore del telefono non è disponibile. Scegli Whisper '
                'nelle impostazioni.',
        recoverable: false,
      );
    }

    final int totalChunks = job.chunks.length;
    try {
      for (final AudioChunk chunk in job.chunks) {
        token.throwIfCancelled();
        if (job.done.containsKey(chunk.index)) continue;

        final double base = chunk.index / totalChunks;
        final double span = 1 / totalChunks;
        final String label = totalChunks > 1
            ? 'Blocco ${chunk.index + 1} di $totalChunks'
            : 'Trascrizione';

        onUpdate(ProcessingUpdate(
          stage: ProcessingStage.trascrizione,
          progress: base,
          detail: '$label · preparazione',
          partialTranscript: _merger.merge(_orderedTexts(job)),
        ));

        final String wavPath =
            p.join(workDir.path, 'chunk_${chunk.index.toString().padLeft(3, '0')}.wav');
        await _tools.extractWav(
          inputPath: source.path,
          outputPath: wavPath,
          start: totalChunks > 1 ? chunk.start : null,
          end: totalChunks > 1 ? chunk.end : null,
        );

        token.throwIfCancelled();

        final String chunkText = await engine.transcribeFile(
          wavPath: wavPath,
          language: language,
          onProgress: (int percent) {
            onUpdate(ProcessingUpdate(
              stage: ProcessingStage.trascrizione,
              progress: (base + span * (percent / 100)).clamp(0.0, 1.0),
              detail: '$label · $percent%',
            ));
          },
        );

        await _safeDelete(wavPath);

        job.done[chunk.index] = chunkText;
        await _jobs.save(job);

        onUpdate(ProcessingUpdate(
          stage: ProcessingStage.trascrizione,
          progress: (chunk.index + 1) / totalChunks,
          detail: '$label · completato',
          partialTranscript: _merger.merge(_orderedTexts(job)),
        ));
      }
    } finally {
      // Libera quello che il motore tiene fra un blocco e l'altro (per
      // whisper il modello caricato in RAM).
      await engine.release();
      await _cleanWorkDir(workDir);
    }

    final String text = _merger.merge(_orderedTexts(job));
    await _jobs.clear();

    if (text.trim().isEmpty) {
      throw const AppFailure(
        'Non ho riconosciuto parlato in questo audio.',
        recoverable: false,
      );
    }

    return TranscriptionOutcome(
      text: text,
      chunks: totalChunks,
      elapsed: DateTime.now().difference(started),
      modelName: model.id,
    );
  }

  Future<List<AudioChunk>> _planFor(
    AudioSource source,
    CancellationToken token,
    void Function(ProcessingUpdate) onUpdate,
    TranscriptionEngine engine,
  ) async {
    final Duration target = engine.preferredChunkLength;
    // Sopra il doppio del blocco preferito conviene dividere: sotto, il
    // ritaglio costerebbe più di quanto renda.
    if (source.duration <= target * 2) {
      return planChunks(total: source.duration, target: target);
    }
    onUpdate(const ProcessingUpdate(
      stage: ProcessingStage.preparazione,
      detail: 'Audio lungo: cerco le pause per dividerlo senza tagliare le '
          'parole',
    ));
    final List<SilenceWindow> silences =
        await _tools.detectSilences(source.path);
    token.throwIfCancelled();
    return planChunks(
      total: source.duration,
      target: target,
      // Con blocchi corti il confine non può spostarsi di molto.
      drift: target ~/ 6,
      threshold: target,
      minTail: target ~/ 4,
      silences: silences,
    );
  }

  List<String> _orderedTexts(TranscriptionJob job) {
    final List<int> keys = job.done.keys.toList()..sort();
    return <String>[for (final int k in keys) job.done[k]!];
  }

  Future<Directory> _workDirectory() async {
    final Directory base = await getTemporaryDirectory();
    final Directory dir = Directory(p.join(base.path, 'brief_work'));
    await dir.create(recursive: true);
    return dir;
  }

  Future<void> _cleanWorkDir(Directory dir) async {
    try {
      if (!dir.existsSync()) return;
      for (final FileSystemEntity e in dir.listSync()) {
        if (e is File && e.path.endsWith('.wav')) await e.delete();
      }
    } on FileSystemException catch (e) {
      debugPrint('Pulizia file temporanei non riuscita: $e');
    }
  }

  Future<void> _safeDelete(String path) async {
    try {
      final File f = File(path);
      if (f.existsSync()) await f.delete();
    } on FileSystemException catch (e) {
      debugPrint('Impossibile eliminare $path: $e');
    }
  }
}
