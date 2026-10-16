import 'dart:io';

import 'package:flutter/foundation.dart';

import '../../core/constants.dart';
import '../../core/failures.dart';
import '../../data/note_repository.dart';
import '../../data/settings.dart';
import '../../domain/audio_source.dart';
import '../../domain/note.dart';
import '../../domain/processing.dart';
import '../audio/media_tools.dart';
import '../summary/summary_service.dart';
import '../transcription/job_store.dart';
import '../transcription/model_manager.dart';
import '../transcription/transcription_service.dart';

/// Esegue l'intera elaborazione di un audio e ne pubblica lo stato.
class ProcessingController extends ChangeNotifier {
  ProcessingController({
    required this.settings,
    required this.notes,
    required this.transcription,
    required this.summaries,
    this.validator = const AudioValidator(),
  });

  final SettingsRepository settings;
  final NoteRepository notes;
  final TranscriptionService transcription;
  final SummaryService summaries;
  final AudioValidator validator;

  ProcessingUpdate _update =
      const ProcessingUpdate(stage: ProcessingStage.inAttesa);
  CancellationToken? _token;
  Note? _result;
  AppFailure? _failure;
  String? _notice;
  AudioSource? _current;

  ProcessingUpdate get update => _update;
  Note? get result => _result;
  AppFailure? get failure => _failure;

  /// Messaggio non bloccante (es. ripiego sul riassunto locale).
  String? get notice => _notice;
  AudioSource? get current => _current;
  bool get isRunning => _token != null && !_update.stage.isTerminal;

  void _emit(ProcessingUpdate u) {
    _update = u;
    notifyListeners();
  }

  void cancel() {
    _token?.cancel();
    _emit(const ProcessingUpdate(
      stage: ProcessingStage.annullato,
      detail: 'Elaborazione annullata',
    ));
  }

  void reset() {
    _token = null;
    _result = null;
    _failure = null;
    _notice = null;
    _current = null;
    _update = const ProcessingUpdate(stage: ProcessingStage.inAttesa);
    notifyListeners();
  }

  /// Avvia l'elaborazione completa: validazione, trascrizione, riassunto,
  /// salvataggio in cronologia.
  Future<Note?> run(
    AudioSource source, {
    TranscriptionJob? resume,
  }) async {
    if (isRunning) return null;

    final CancellationToken token = CancellationToken();
    _token = token;
    _result = null;
    _failure = null;
    _notice = null;
    _current = source;
    _emit(const ProcessingUpdate(stage: ProcessingStage.preparazione));

    final AppSettings s = settings.value;
    try {
      final AudioSource validated = source.duration > Duration.zero
          ? source
          : await validator.validate(source);
      _current = validated;
      token.throwIfCancelled();

      final TranscriptionOutcome transcript = await transcription.transcribe(
        source: validated,
        model: ModelOption.byId(s.modelName),
        language: s.language,
        token: token,
        onUpdate: _emit,
        // Riprende i blocchi già trascritti di un lavoro interrotto.
        resume: resume,
      );
      token.throwIfCancelled();

      _emit(ProcessingUpdate(
        stage: ProcessingStage.riassunto,
        progress: null,
        detail: s.cloudAllowed
            ? 'Invio del testo al servizio di riassunto'
            : 'Riassunto locale in corso',
        partialTranscript: transcript.text,
      ));

      final SummaryOutcome summary = await summaries.summarize(
        transcript: transcript.text,
        style: s.summaryStyle,
        cloudAllowed: s.cloudAllowed,
        language: s.language,
      );
      _notice = summary.fallbackReason;
      token.throwIfCancelled();

      final bool keepAudio = s.keepAudio;
      Note note = Note(
        title: validated.title,
        createdAt: DateTime.now(),
        sourceName: validated.displayName,
        durationMs: validated.duration.inMilliseconds,
        sizeBytes: validated.sizeBytes,
        language: s.language,
        modelName: transcript.modelName,
        transcript: transcript.text,
        cloudUsed: summary.usedCloud,
        engine: s.transcriptionEngine,
        summary: summary.bundle,
        audioPath: keepAudio ? validated.path : null,
      );

      if (s.historyEnabled) {
        final int id = await notes.insert(note);
        note = note.copyWith(id: id);
      }
      if (!keepAudio) {
        await _deleteSource(validated.path);
      }

      _result = note;
      _emit(ProcessingUpdate(
        stage: ProcessingStage.completato,
        progress: 1,
        detail: 'Completato in ${transcript.elapsed.inSeconds}s',
        partialTranscript: transcript.text,
      ));
      return note;
    } on CancelledException {
      _emit(const ProcessingUpdate(
        stage: ProcessingStage.annullato,
        detail: 'Elaborazione annullata. Il lavoro svolto è stato salvato: '
            'puoi riprenderlo.',
      ));
      return null;
    } on AppFailure catch (e) {
      _failure = e;
      _emit(ProcessingUpdate(
        stage: ProcessingStage.errore,
        detail: e.message,
      ));
      return null;
    } on Object catch (e, st) {
      debugPrint('Errore non previsto: $e\n$st');
      _failure = AppFailure(
        'Si è verificato un errore imprevisto durante l\'elaborazione.',
        cause: e,
      );
      _emit(ProcessingUpdate(
        stage: ProcessingStage.errore,
        detail: _failure!.message,
      ));
      return null;
    } finally {
      _token = null;
    }
  }

  /// Rigenera il riassunto di una nota già trascritta (es. cambiando stile
  /// o passando dal locale al cloud) senza rifare la trascrizione.
  Future<Note?> regenerateSummary(
    Note note, {
    required SummaryStyle style,
  }) async {
    final AppSettings s = settings.value;
    _notice = null;
    _emit(ProcessingUpdate(
      stage: ProcessingStage.riassunto,
      detail: 'Nuovo riassunto (${style.label.toLowerCase()})',
    ));
    try {
      final SummaryOutcome outcome = await summaries.summarize(
        transcript: note.transcript,
        style: style,
        cloudAllowed: s.cloudAllowed,
        language: note.language,
      );
      _notice = outcome.fallbackReason;
      final Note updated = note.copyWith(summary: outcome.bundle);
      if (updated.id != null) await notes.updateNote(updated);
      _result = updated;
      _emit(const ProcessingUpdate(
        stage: ProcessingStage.completato,
        progress: 1,
      ));
      return updated;
    } on AppFailure catch (e) {
      _failure = e;
      _emit(ProcessingUpdate(stage: ProcessingStage.errore, detail: e.message));
      return null;
    }
  }

  Future<void> _deleteSource(String path) async {
    try {
      final File f = File(path);
      if (f.existsSync()) await f.delete();
    } on FileSystemException catch (e) {
      debugPrint('Impossibile eliminare l\'audio di origine: $e');
    }
  }
}
