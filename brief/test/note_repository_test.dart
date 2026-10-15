import 'package:flutter_test/flutter_test.dart';
import 'package:sqflite_common_ffi/sqflite_ffi.dart';
import 'package:brief/core/constants.dart';
import 'package:brief/data/note_repository.dart';
import 'package:brief/domain/note.dart';
import 'package:brief/domain/summary.dart';

Note buildNote({
  required String title,
  String transcript = 'Testo della trascrizione.',
  DateTime? createdAt,
  SummaryBundle? summary,
  bool cloudUsed = false,
}) =>
    Note(
      title: title,
      createdAt: createdAt ?? DateTime(2026, 10, 15, 12),
      sourceName: '$title.opus',
      durationMs: 65000,
      sizeBytes: 123456,
      language: 'it',
      modelName: 'base',
      transcript: transcript,
      cloudUsed: cloudUsed,
      summary: summary,
    );

void main() {
  sqfliteFfiInit();
  databaseFactory = databaseFactoryFfi;

  late Database db;
  late NoteRepository repo;

  setUp(() async {
    db = await databaseFactory.openDatabase(inMemoryDatabasePath);
    await NoteRepository.createSchema(db);
    repo = NoteRepository(db);
  });

  tearDown(() async => db.close());

  test('salva e rilegge una nota completa', () async {
    final SummaryBundle summary = SummaryBundle(
      style: SummaryStyle.standard,
      text: 'Riassunto di prova.',
      keyPoints: const <String>['Punto uno', 'Punto due'],
      actions: const <ActionItem>[ActionItem(text: 'Chiamare', owner: 'Anna')],
      engine: SummaryEngine.cloud,
      generatedAt: DateTime(2026, 10, 15, 12, 30),
    );
    final int id = await repo.insert(
      buildNote(title: 'Riunione', summary: summary, cloudUsed: true),
    );

    final Note? saved = await repo.byId(id);
    expect(saved, isNotNull);
    expect(saved!.title, 'Riunione');
    expect(saved.cloudUsed, isTrue);
    expect(saved.summary?.text, 'Riassunto di prova.');
    expect(saved.summary?.keyPoints, hasLength(2));
    expect(saved.summary?.actions.single.owner, 'Anna');
    expect(saved.duration, const Duration(milliseconds: 65000));
  });

  test('elenca in ordine dalla più recente', () async {
    await repo.insert(
        buildNote(title: 'Vecchia', createdAt: DateTime(2026, 1, 1)));
    await repo.insert(
        buildNote(title: 'Nuova', createdAt: DateTime(2026, 10, 1)));

    final List<Note> notes = await repo.list();
    expect(notes.map((Note n) => n.title), <String>['Nuova', 'Vecchia']);
  });

  test('la ricerca filtra su titolo e trascrizione', () async {
    await repo.insert(buildNote(
        title: 'Preventivo', transcript: 'Parliamo del contratto.'));
    await repo.insert(
        buildNote(title: 'Spesa', transcript: 'Comprare il pane.'));

    expect((await repo.list(query: 'contratto')).single.title, 'Preventivo');
    expect((await repo.list(query: 'Spesa')).single.title, 'Spesa');
    expect(await repo.list(query: 'inesistente'), isEmpty);
    // Una ricerca vuota non filtra.
    expect(await repo.list(query: '  '), hasLength(2));
  });

  test('aggiorna il riassunto di una nota esistente', () async {
    final int id = await repo.insert(buildNote(title: 'Nota'));
    final Note saved = (await repo.byId(id))!;

    final SummaryBundle nuovo = SummaryBundle(
      style: SummaryStyle.breve,
      text: 'Nuovo riassunto.',
      keyPoints: const <String>[],
      actions: const <ActionItem>[],
      engine: SummaryEngine.locale,
      generatedAt: DateTime(2026, 10, 16),
    );
    await repo.updateNote(saved.copyWith(summary: nuovo));

    final Note? updated = await repo.byId(id);
    expect(updated!.summary?.text, 'Nuovo riassunto.');
    expect(updated.summary?.style, SummaryStyle.breve);
    expect(await repo.count(), 1);
  });

  test('elimina una singola nota', () async {
    final int id = await repo.insert(buildNote(title: 'Da eliminare'));
    await repo.insert(buildNote(title: 'Da tenere'));

    await repo.delete(id);
    expect(await repo.byId(id), isNull);
    expect(await repo.count(), 1);
  });

  test('cancella tutta la cronologia', () async {
    for (int i = 0; i < 5; i++) {
      await repo.insert(buildNote(title: 'Nota $i'));
    }
    expect(await repo.count(), 5);
    await repo.clear();
    expect(await repo.count(), 0);
    expect(await repo.list(), isEmpty);
  });

  test('una nota senza riassunto resta leggibile', () async {
    final int id = await repo.insert(buildNote(title: 'Solo trascrizione'));
    final Note note = (await repo.byId(id))!;
    expect(note.summary, isNull);
    expect(note.preview, contains('Testo della trascrizione'));
  });
}
