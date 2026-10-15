import 'dart:io';

import 'package:flutter/foundation.dart';
import 'package:path/path.dart' as p;
import 'package:sqflite/sqflite.dart';

import '../domain/note.dart';

/// Cronologia locale (SQLite). Nessun dato lascia il dispositivo.
class NoteRepository {
  NoteRepository(this._db);

  final Database _db;
  static const String table = 'notes';
  static const int schemaVersion = 1;

  /// Apre (o crea) il database nel percorso standard dell'app.
  static Future<NoteRepository> open({String? directory}) async {
    final String dir = directory ?? await getDatabasesPath();
    final Database db = await openDatabase(
      p.join(dir, 'vocenota.db'),
      version: schemaVersion,
      onCreate: (Database db, int version) async => createSchema(db),
      onUpgrade: (Database db, int from, int to) async {
        // Le migrazioni future si aggiungono qui, in ordine di versione.
      },
    );
    return NoteRepository(db);
  }

  static Future<void> createSchema(DatabaseExecutor db) async {
    await db.execute('''
      CREATE TABLE IF NOT EXISTS $table (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        title TEXT NOT NULL,
        created_at INTEGER NOT NULL,
        source_name TEXT NOT NULL,
        duration_ms INTEGER NOT NULL DEFAULT 0,
        size_bytes INTEGER NOT NULL DEFAULT 0,
        language TEXT NOT NULL DEFAULT 'it',
        model_name TEXT NOT NULL DEFAULT '',
        transcript TEXT NOT NULL DEFAULT '',
        summary_json TEXT,
        cloud_used INTEGER NOT NULL DEFAULT 0,
        audio_path TEXT
      )
    ''');
    await db.execute(
      'CREATE INDEX IF NOT EXISTS idx_notes_created ON $table(created_at DESC)',
    );
  }

  Future<int> insert(Note note) =>
      _db.insert(table, note.toRow()..remove('id'));

  Future<void> updateNote(Note note) async {
    if (note.id == null) return;
    await _db.update(table, note.toRow(),
        where: 'id = ?', whereArgs: <Object?>[note.id]);
  }

  Future<List<Note>> list({int limit = 200, String? query}) async {
    final bool filtered = query != null && query.trim().isNotEmpty;
    final List<Map<String, Object?>> rows = await _db.query(
      table,
      where: filtered ? '(title LIKE ? OR transcript LIKE ?)' : null,
      whereArgs: filtered
          ? <Object?>['%${query.trim()}%', '%${query.trim()}%']
          : null,
      orderBy: 'created_at DESC',
      limit: limit,
    );
    return rows.map(Note.fromRow).toList();
  }

  Future<Note?> byId(int id) async {
    final List<Map<String, Object?>> rows = await _db.query(
      table,
      where: 'id = ?',
      whereArgs: <Object?>[id],
      limit: 1,
    );
    return rows.isEmpty ? null : Note.fromRow(rows.first);
  }

  Future<int> count() async =>
      Sqflite.firstIntValue(await _db.rawQuery('SELECT COUNT(*) FROM $table')) ??
      0;

  /// Elimina la nota e l'eventuale audio conservato.
  Future<void> delete(int id) async {
    final Note? note = await byId(id);
    await _db.delete(table, where: 'id = ?', whereArgs: <Object?>[id]);
    await _deleteAudio(note);
  }

  Future<void> clear() async {
    final List<Note> all = await list(limit: 100000);
    await _db.delete(table);
    for (final Note n in all) {
      await _deleteAudio(n);
    }
  }

  Future<void> _deleteAudio(Note? note) async {
    final String? path = note?.audioPath;
    if (path == null) return;
    try {
      final File f = File(path);
      if (f.existsSync()) await f.delete();
    } on FileSystemException catch (e) {
      debugPrint('Impossibile eliminare $path: $e');
    }
  }

  Future<void> close() => _db.close();
}
