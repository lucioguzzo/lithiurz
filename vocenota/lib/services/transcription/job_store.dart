import 'dart:convert';
import 'dart:io';

import 'package:flutter/foundation.dart';
import 'package:path/path.dart' as p;
import 'package:path_provider/path_provider.dart';

import '../audio/chunk_planner.dart';

/// Stato di una trascrizione lunga, salvato su disco per poterla riprendere
/// dopo una chiusura dell'app o un'interruzione di sistema.
class TranscriptionJob {
  TranscriptionJob({
    required this.audioPath,
    required this.displayName,
    required this.modelName,
    required this.language,
    required this.totalMs,
    required this.chunks,
    Map<int, String>? done,
    DateTime? updatedAt,
  })  : done = done ?? <int, String>{},
        updatedAt = updatedAt ?? DateTime.now();

  final String audioPath;
  final String displayName;
  final String modelName;
  final String language;
  final int totalMs;
  final List<AudioChunk> chunks;

  /// Testo già trascritto, per indice di blocco.
  final Map<int, String> done;
  DateTime updatedAt;

  bool get isComplete => done.length >= chunks.length;
  int get remaining => chunks.length - done.length;

  double get fraction =>
      chunks.isEmpty ? 0 : done.length / chunks.length;

  Map<String, Object?> toJson() => <String, Object?>{
        'audio_path': audioPath,
        'display_name': displayName,
        'model_name': modelName,
        'language': language,
        'total_ms': totalMs,
        'chunks': <Map<String, Object?>>[
          for (final AudioChunk c in chunks) c.toJson(),
        ],
        'done': done.map((int k, String v) => MapEntry<String, String>('$k', v)),
        'updated_at': updatedAt.toIso8601String(),
      };

  static TranscriptionJob fromJson(Map<String, Object?> json) =>
      TranscriptionJob(
        audioPath: json['audio_path'] as String,
        displayName: (json['display_name'] ?? '') as String,
        modelName: (json['model_name'] ?? 'base') as String,
        language: (json['language'] ?? 'it') as String,
        totalMs: (json['total_ms'] as num?)?.toInt() ?? 0,
        chunks: <AudioChunk>[
          for (final Object? c in (json['chunks'] as List<Object?>? ?? const <Object?>[]))
            AudioChunk.fromJson((c as Map<Object?, Object?>).cast<String, Object?>()),
        ],
        done: <int, String>{
          for (final MapEntry<Object?, Object?> e
              in ((json['done'] as Map<Object?, Object?>?) ??
                      const <Object?, Object?>{})
                  .entries)
            int.parse(e.key.toString()): e.value.toString(),
        },
        updatedAt:
            DateTime.tryParse(json['updated_at']?.toString() ?? '') ??
                DateTime.now(),
      );
}

/// Persistenza del lavoro in corso (un solo job alla volta).
class JobStore {
  JobStore({Directory? directory}) : _directory = directory;

  Directory? _directory;

  Future<File> _file() async {
    _directory ??= await getApplicationSupportDirectory();
    await _directory!.create(recursive: true);
    return File(p.join(_directory!.path, 'job_in_corso.json'));
  }

  Future<void> save(TranscriptionJob job) async {
    job.updatedAt = DateTime.now();
    try {
      await (await _file()).writeAsString(jsonEncode(job.toJson()));
    } on FileSystemException catch (e) {
      debugPrint('Salvataggio job non riuscito: $e');
    }
  }

  /// Restituisce il job salvato solo se l'audio di origine esiste ancora.
  Future<TranscriptionJob?> load() async {
    try {
      final File f = await _file();
      if (!f.existsSync()) return null;
      final Object? decoded = jsonDecode(await f.readAsString());
      if (decoded is! Map<String, Object?>) return null;
      final TranscriptionJob job = TranscriptionJob.fromJson(decoded);
      if (!File(job.audioPath).existsSync()) {
        await clear();
        return null;
      }
      if (job.isComplete || job.chunks.isEmpty) {
        await clear();
        return null;
      }
      return job;
    } on Object catch (e) {
      debugPrint('Job salvato illeggibile: $e');
      await clear();
      return null;
    }
  }

  Future<void> clear() async {
    try {
      final File f = await _file();
      if (f.existsSync()) await f.delete();
    } on FileSystemException catch (e) {
      debugPrint('Pulizia job non riuscita: $e');
    }
  }
}
