import '../core/formatters.dart';
import '../services/transcription/engine.dart';
import 'summary.dart';

/// Una elaborazione completata, salvata nella cronologia locale.
class Note {
  const Note({
    this.id,
    required this.title,
    required this.createdAt,
    required this.sourceName,
    required this.durationMs,
    required this.sizeBytes,
    required this.language,
    required this.modelName,
    required this.transcript,
    required this.cloudUsed,
    this.engine = TranscriptionEngineId.whisper,
    this.summary,
    this.audioPath,
  });

  final int? id;
  final String title;
  final DateTime createdAt;
  final String sourceName;
  final int durationMs;
  final int sizeBytes;
  final String language;
  final String modelName;
  final String transcript;
  final bool cloudUsed;

  /// Motore che ha prodotto la trascrizione: la schermata dei risultati
  /// lo dichiara, invece di dare per scontato whisper.
  final TranscriptionEngineId engine;
  final SummaryBundle? summary;

  /// Valorizzato solo se l'utente ha scelto di conservare l'audio.
  final String? audioPath;

  Duration get duration => Duration(milliseconds: durationMs);

  String get preview => Fmt.preview(
        summary?.text.isNotEmpty == true ? summary!.text : transcript,
      );

  Note copyWith({
    int? id,
    String? title,
    SummaryBundle? summary,
    String? transcript,
    String? audioPath,
    bool clearAudioPath = false,
  }) =>
      Note(
        id: id ?? this.id,
        title: title ?? this.title,
        createdAt: createdAt,
        sourceName: sourceName,
        durationMs: durationMs,
        sizeBytes: sizeBytes,
        language: language,
        modelName: modelName,
        transcript: transcript ?? this.transcript,
        cloudUsed: cloudUsed,
        engine: engine,
        summary: summary ?? this.summary,
        audioPath: clearAudioPath ? null : (audioPath ?? this.audioPath),
      );

  Map<String, Object?> toRow() => <String, Object?>{
        if (id != null) 'id': id,
        'title': title,
        'created_at': createdAt.millisecondsSinceEpoch,
        'source_name': sourceName,
        'duration_ms': durationMs,
        'size_bytes': sizeBytes,
        'language': language,
        'model_name': modelName,
        'transcript': transcript,
        'summary_json': summary?.encode(),
        'cloud_used': cloudUsed ? 1 : 0,
        'engine': engine.id,
        'audio_path': audioPath,
      };

  factory Note.fromRow(Map<String, Object?> row) => Note(
        id: row['id'] as int?,
        title: (row['title'] ?? '') as String,
        createdAt: DateTime.fromMillisecondsSinceEpoch(
          (row['created_at'] as int?) ?? 0,
        ),
        sourceName: (row['source_name'] ?? '') as String,
        durationMs: (row['duration_ms'] as int?) ?? 0,
        sizeBytes: (row['size_bytes'] as int?) ?? 0,
        language: (row['language'] ?? 'it') as String,
        modelName: (row['model_name'] ?? '') as String,
        transcript: (row['transcript'] ?? '') as String,
        cloudUsed: ((row['cloud_used'] as int?) ?? 0) == 1,
        engine: TranscriptionEngineId.fromId(row['engine'] as String?),
        summary: SummaryBundle.decode(row['summary_json'] as String?),
        audioPath: row['audio_path'] as String?,
      );
}
