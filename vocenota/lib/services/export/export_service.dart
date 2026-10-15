import 'dart:io';

import 'package:flutter/services.dart';
import 'package:path/path.dart' as p;
import 'package:path_provider/path_provider.dart';
import 'package:share_plus/share_plus.dart';

import '../../domain/note.dart';

/// Copia, condivisione ed esportazione dei risultati.
class ExportService {
  const ExportService();

  Future<void> copy(String text) =>
      Clipboard.setData(ClipboardData(text: text));

  Future<void> shareText(String text, {String? subject}) =>
      SharePlus.instance.share(ShareParams(text: text, subject: subject));

  /// Scrive un file di testo nella cartella temporanea e apre il menu di
  /// condivisione del sistema, da cui si può salvare dove si vuole.
  Future<String> exportAsFile(
    Note note, {
    bool includeTranscript = true,
    bool markdown = false,
  }) async {
    final String body = markdown
        ? _markdown(note, includeTranscript: includeTranscript)
        : note.summary?.toPlainText(
              title: note.title,
              transcript: includeTranscript ? note.transcript : null,
            ) ??
            note.transcript;

    final Directory dir = await getTemporaryDirectory();
    final String safe = note.title
        .replaceAll(RegExp(r'[^\w\s\-àèéìòùÀÈÉÌÒÙ]'), '')
        .replaceAll(RegExp(r'\s+'), '_');
    final String name =
        '${safe.isEmpty ? 'vocenota' : safe}.${markdown ? 'md' : 'txt'}';
    final File file = File(p.join(dir.path, name));
    await file.writeAsString(body);

    await SharePlus.instance.share(
      ShareParams(
        files: <XFile>[XFile(file.path, mimeType: 'text/plain')],
        subject: note.title,
      ),
    );
    return file.path;
  }

  String _markdown(Note note, {required bool includeTranscript}) {
    final StringBuffer b = StringBuffer()
      ..writeln('# ${note.title}')
      ..writeln();
    final summary = note.summary;
    if (summary != null) {
      if (summary.text.trim().isNotEmpty) {
        b
          ..writeln('## Riassunto')
          ..writeln()
          ..writeln(summary.text.trim())
          ..writeln();
      }
      if (summary.keyPoints.isNotEmpty) {
        b
          ..writeln('## Punti principali')
          ..writeln();
        for (final String k in summary.keyPoints) {
          b.writeln('- $k');
        }
        b.writeln();
      }
      if (summary.actions.isNotEmpty) {
        b
          ..writeln('## Azioni da svolgere')
          ..writeln();
        for (final a in summary.actions) {
          b.writeln('- [ ] ${a.display}');
        }
        b.writeln();
      }
    }
    if (includeTranscript && note.transcript.trim().isNotEmpty) {
      b
        ..writeln('## Trascrizione')
        ..writeln()
        ..writeln(note.transcript.trim())
        ..writeln();
    }
    return b.toString();
  }
}
