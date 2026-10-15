import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:brief/services/audio/chunk_planner.dart';
import 'package:brief/services/transcription/job_store.dart';

void main() {
  late Directory tempDir;
  late JobStore store;
  late File audio;

  setUp(() async {
    tempDir = await Directory.systemTemp.createTemp('brief_job');
    store = JobStore(directory: tempDir);
    audio = File('${tempDir.path}/audio.opus')..writeAsStringSync('finto audio');
  });

  tearDown(() async => tempDir.delete(recursive: true));

  TranscriptionJob buildJob() => TranscriptionJob(
        audioPath: audio.path,
        displayName: 'audio.opus',
        modelName: 'base',
        language: 'it',
        totalMs: 900000,
        chunks: const <AudioChunk>[
          AudioChunk(index: 0, start: Duration.zero, end: Duration(minutes: 5)),
          AudioChunk(
              index: 1,
              start: Duration(minutes: 5),
              end: Duration(minutes: 10)),
          AudioChunk(
              index: 2,
              start: Duration(minutes: 10),
              end: Duration(minutes: 15)),
        ],
      );

  test('senza lavoro salvato non restituisce nulla', () async {
    expect(await store.load(), isNull);
  });

  test('salva e ricarica un lavoro parziale', () async {
    final TranscriptionJob job = buildJob();
    job.done[0] = 'Primo blocco trascritto.';
    await store.save(job);

    final TranscriptionJob? loaded = await store.load();
    expect(loaded, isNotNull);
    expect(loaded!.chunks, hasLength(3));
    expect(loaded.done[0], 'Primo blocco trascritto.');
    expect(loaded.remaining, 2);
    expect(loaded.fraction, closeTo(1 / 3, 0.001));
    expect(loaded.language, 'it');
    expect(loaded.chunks[1].start, const Duration(minutes: 5));
  });

  test('un lavoro completato non viene riproposto', () async {
    final TranscriptionJob job = buildJob();
    for (int i = 0; i < 3; i++) {
      job.done[i] = 'blocco $i';
    }
    await store.save(job);
    expect(job.isComplete, isTrue);
    expect(await store.load(), isNull);
  });

  test('se l\'audio non esiste più il lavoro viene scartato', () async {
    final TranscriptionJob job = buildJob();
    job.done[0] = 'testo';
    await store.save(job);
    await audio.delete();

    expect(await store.load(), isNull);
    // Il file di stato viene ripulito.
    expect(File('${tempDir.path}/job_in_corso.json').existsSync(), isFalse);
  });

  test('un file di stato corrotto non blocca l\'app', () async {
    File('${tempDir.path}/job_in_corso.json').writeAsStringSync('{rotto');
    expect(await store.load(), isNull);
  });

  test('clear rimuove il lavoro salvato', () async {
    final TranscriptionJob job = buildJob();
    job.done[0] = 'testo';
    await store.save(job);
    await store.clear();
    expect(await store.load(), isNull);
  });
}
