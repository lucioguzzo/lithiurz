import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:sqflite_common_ffi/sqflite_ffi.dart';
import 'package:vocenota/core/constants.dart';
import 'package:vocenota/core/failures.dart';
import 'package:vocenota/data/note_repository.dart';
import 'package:vocenota/data/settings.dart';
import 'package:vocenota/domain/audio_source.dart';
import 'package:vocenota/domain/note.dart';
import 'package:vocenota/domain/processing.dart';
import 'package:vocenota/services/audio/chunk_planner.dart';
import 'package:vocenota/services/audio/media_tools.dart';
import 'package:vocenota/services/pipeline/processing_controller.dart';
import 'package:vocenota/services/summary/summary_service.dart';
import 'package:vocenota/services/transcription/job_store.dart';
import 'package:vocenota/services/transcription/model_manager.dart';
import 'package:vocenota/services/transcription/transcription_service.dart';

/// Validatore che non tocca ffprobe.
class FakeValidator extends AudioValidator {
  const FakeValidator();

  @override
  Future<AudioSource> validate(AudioSource candidate) async => candidate;
}

/// Trascrittore finto: restituisce un testo prefissato o solleva un errore.
class FakeTranscription extends TranscriptionService {
  FakeTranscription({this.text = 'Trascrizione finta.', this.failure});

  final String text;
  final AppFailure? failure;
  int calls = 0;
  bool sawCancel = false;
  TranscriptionJob? lastResume;

  @override
  Future<TranscriptionOutcome> transcribe({
    required AudioSource source,
    required ModelOption model,
    required String language,
    required void Function(ProcessingUpdate) onUpdate,
    required CancellationToken token,
    TranscriptionJob? resume,
  }) async {
    calls++;
    lastResume = resume;
    onUpdate(const ProcessingUpdate(
      stage: ProcessingStage.trascrizione,
      progress: 0.5,
    ));
    if (failure != null) throw failure!;
    if (token.isCancelled) {
      sawCancel = true;
      throw const CancelledException();
    }
    return TranscriptionOutcome(
      text: text,
      chunks: 1,
      elapsed: const Duration(seconds: 3),
      modelName: model.id,
    );
  }
}

Future<AudioSource> fakeAudio(Directory dir, {String name = 'nota.opus'}) async {
  final File f = File('${dir.path}/$name')..writeAsStringSync('audio finto');
  return AudioSource(
    path: f.path,
    displayName: name,
    sizeBytes: f.lengthSync(),
    duration: const Duration(minutes: 2),
    origin: AudioOrigin.condivisione,
  );
}

void main() {
  sqfliteFfiInit();
  databaseFactory = databaseFactoryFfi;
  TestWidgetsFlutterBinding.ensureInitialized();

  late Directory tempDir;
  late Database db;
  late NoteRepository notes;
  late SettingsRepository settings;

  setUp(() async {
    tempDir = await Directory.systemTemp.createTemp('vocenota_pipeline');
    db = await databaseFactory.openDatabase(inMemoryDatabasePath);
    await NoteRepository.createSchema(db);
    notes = NoteRepository(db);
    SharedPreferences.setMockInitialValues(<String, Object>{});
    settings = await SettingsRepository.open();
  });

  tearDown(() async {
    await db.close();
    await tempDir.delete(recursive: true);
  });

  ProcessingController build(FakeTranscription transcription) =>
      ProcessingController(
        settings: settings,
        notes: notes,
        transcription: transcription,
        summaries: SummaryService(client: null),
        validator: const FakeValidator(),
      );

  test('elaborazione completa: salva la nota e cancella l\'audio', () async {
    final AudioSource source = await fakeAudio(tempDir);
    final FakeTranscription transcription = FakeTranscription(
      text: 'Dobbiamo richiamare il cliente entro domani. '
          'Il preventivo va rivisto.',
    );
    final ProcessingController controller = build(transcription);

    final Note? note = await controller.run(source);

    expect(note, isNotNull);
    expect(controller.update.stage, ProcessingStage.completato);
    expect(note!.transcript, contains('richiamare il cliente'));
    expect(note.summary, isNotNull);
    expect(note.cloudUsed, isFalse);
    expect(await notes.count(), 1);
    // Senza "conserva l'audio" il file di origine sparisce.
    expect(File(source.path).existsSync(), isFalse);
    expect(note.audioPath, isNull);
  });

  test('con cronologia disattivata non salva nulla nel database', () async {
    await settings.update(settings.value.copyWith(historyEnabled: false));
    final ProcessingController controller = build(FakeTranscription());

    final Note? note = await controller.run(await fakeAudio(tempDir));

    expect(note, isNotNull);
    expect(note!.id, isNull);
    expect(await notes.count(), 0);
  });

  test('con "conserva audio" il file resta e viene collegato alla nota',
      () async {
    await settings.update(settings.value.copyWith(keepAudio: true));
    final AudioSource source = await fakeAudio(tempDir);
    final ProcessingController controller = build(FakeTranscription());

    final Note? note = await controller.run(source);

    expect(File(source.path).existsSync(), isTrue);
    expect(note!.audioPath, source.path);
  });

  test('un errore di trascrizione viene esposto senza eccezioni', () async {
    final ProcessingController controller = build(
      FakeTranscription(
        failure: const AppFailure('Modello non installato.',
            recoverable: false),
      ),
    );

    final Note? note = await controller.run(await fakeAudio(tempDir));

    expect(note, isNull);
    expect(controller.update.stage, ProcessingStage.errore);
    expect(controller.failure?.message, 'Modello non installato.');
    expect(await notes.count(), 0);
  });

  test('l\'annullamento porta allo stato annullato, non a un errore',
      () async {
    final FakeTranscription transcription = FakeTranscription();
    final ProcessingController controller = build(transcription);
    controller.addListener(() {
      if (controller.update.stage == ProcessingStage.trascrizione) {
        controller.cancel();
      }
    });

    final Note? note = await controller.run(await fakeAudio(tempDir));

    expect(note, isNull);
    expect(transcription.sawCancel, isTrue);
    expect(controller.update.stage, ProcessingStage.annullato);
  });

  test('non avvia due elaborazioni contemporanee', () async {
    final ProcessingController controller = build(FakeTranscription());
    final Future<Note?> first = controller.run(await fakeAudio(tempDir));
    final Note? second =
        await controller.run(await fakeAudio(tempDir, name: 'altra.opus'));
    expect(second, isNull);
    expect(await first, isNotNull);
  });

  test('rigenerare il riassunto aggiorna la nota salvata', () async {
    final ProcessingController controller = build(
      FakeTranscription(
        text: 'Il contratto scade a novembre. Dobbiamo preparare la '
            'controproposta entro venerdì. Servono i consumi degli ultimi mesi.',
      ),
    );
    final Note note = (await controller.run(await fakeAudio(tempDir)))!;
    expect(note.summary!.style, SummaryStyle.standard);

    final Note? updated =
        await controller.regenerateSummary(note, style: SummaryStyle.breve);

    expect(updated!.summary!.style, SummaryStyle.breve);
    final Note fromDb = (await notes.byId(note.id!))!;
    expect(fromDb.summary!.style, SummaryStyle.breve);
    expect(await notes.count(), 1);
  });

  test('il lavoro parziale viene passato al trascrittore', () async {
    final AudioSource source = await fakeAudio(tempDir);
    final TranscriptionJob job = TranscriptionJob(
      audioPath: source.path,
      displayName: source.displayName,
      modelName: 'base',
      language: 'it',
      totalMs: 600000,
      chunks: const <AudioChunk>[
        AudioChunk(index: 0, start: Duration.zero, end: Duration(minutes: 5)),
        AudioChunk(
            index: 1, start: Duration(minutes: 5), end: Duration(minutes: 10)),
      ],
      done: <int, String>{0: 'Primo blocco.'},
    );
    final FakeTranscription transcription = FakeTranscription();
    final ProcessingController controller = build(transcription);

    await controller.run(source, resume: job);

    expect(transcription.lastResume, isNotNull);
    expect(transcription.lastResume!.done[0], 'Primo blocco.');
    expect(transcription.lastResume!.remaining, 1);
  });

  test('una ripresa con modello diverso non viene usata', () async {
    // Il controller inoltra comunque il lavoro: è il servizio di
    // trascrizione a scartarlo se modello o lingua non coincidono.
    final AudioSource source = await fakeAudio(tempDir);
    final TranscriptionJob job = TranscriptionJob(
      audioPath: source.path,
      displayName: source.displayName,
      modelName: 'tiny',
      language: 'en',
      totalMs: 600000,
      chunks: const <AudioChunk>[
        AudioChunk(index: 0, start: Duration.zero, end: Duration(minutes: 5)),
      ],
      done: <int, String>{0: 'vecchio blocco'},
    );
    final FakeTranscription transcription = FakeTranscription();
    final ProcessingController controller = build(transcription);

    await controller.run(source, resume: job);

    expect(transcription.lastResume!.modelName, 'tiny');
    expect(controller.update.stage, ProcessingStage.completato);
  });

  test('reset riporta il controller allo stato iniziale', () async {
    final ProcessingController controller = build(FakeTranscription());
    await controller.run(await fakeAudio(tempDir));
    controller.reset();
    expect(controller.update.stage, ProcessingStage.inAttesa);
    expect(controller.result, isNull);
    expect(controller.failure, isNull);
  });
}
