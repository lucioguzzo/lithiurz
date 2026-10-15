import 'dart:async';
import 'dart:io';

import 'package:file_picker/file_picker.dart';
import 'package:flutter/foundation.dart';
import 'package:path/path.dart' as p;
import 'package:path_provider/path_provider.dart';
import 'package:receive_sharing_intent/receive_sharing_intent.dart';

import '../../core/constants.dart';
import '../../core/failures.dart';
import '../../domain/audio_source.dart';

/// Riceve i file dal menu "Condividi" di sistema (Android e iOS) e
/// dall'importazione manuale, copiandoli nella sandbox dell'app.
///
/// Copiare è necessario: gli URI condivisi e i file nella cache di sistema
/// possono sparire appena l'app che li ha inviati viene chiusa.
class ShareIntakeService {
  ShareIntakeService();

  final StreamController<List<AudioSource>> _controller =
      StreamController<List<AudioSource>>.broadcast();
  StreamSubscription<List<SharedMediaFile>>? _subscription;
  bool _started = false;

  /// Audio arrivati dalla condivisione, pronti per la validazione.
  Stream<List<AudioSource>> get incoming => _controller.stream;

  /// Avvia l'ascolto. Gestisce sia l'app aperta che l'avvio a freddo.
  Future<List<AudioSource>> start() async {
    if (_started) return const <AudioSource>[];
    _started = true;

    _subscription =
        ReceiveSharingIntent.instance.getMediaStream().listen(
      (List<SharedMediaFile> files) async {
        final List<AudioSource> sources = await _adopt(files);
        if (sources.isNotEmpty) _controller.add(sources);
      },
      onError: (Object e) => debugPrint('Stream condivisione: $e'),
    );

    try {
      final List<SharedMediaFile> initial =
          await ReceiveSharingIntent.instance.getInitialMedia();
      final List<AudioSource> sources = await _adopt(initial);
      // Evita che lo stesso file venga riproposto alla ripresa dell'app.
      await ReceiveSharingIntent.instance.reset();
      return sources;
    } on Object catch (e) {
      debugPrint('Lettura condivisione iniziale non riuscita: $e');
      return const <AudioSource>[];
    }
  }

  /// Importazione manuale dal file manager.
  Future<List<AudioSource>> pickManually() async {
    final List<PlatformFile> picked = await FilePicker.pickFiles(
      type: FileType.custom,
      allowedExtensions: AudioLimits.knownExtensions.toList(),
      dialogTitle: 'Scegli un audio',
    );
    if (picked.isEmpty) return const <AudioSource>[];

    final List<AudioSource> out = <AudioSource>[];
    for (final PlatformFile f in picked) {
      final String? path = f.path;
      if (path == null) continue;
      final AudioSource? adopted = await adoptFile(path, name: f.name);
      if (adopted != null) out.add(adopted);
    }
    if (out.isEmpty) {
      throw const AppFailure(
        'Nessun file audio utilizzabile fra quelli selezionati.',
        recoverable: false,
      );
    }
    return out;
  }

  Future<List<AudioSource>> _adopt(List<SharedMediaFile> files) async {
    final List<AudioSource> out = <AudioSource>[];
    for (final SharedMediaFile f in files) {
      if (!_looksLikeAudio(f)) continue;
      final AudioSource? adopted = await adoptFile(
        _cleanPath(f.path),
        origin: AudioOrigin.condivisione,
      );
      if (adopted != null) out.add(adopted);
    }
    return out;
  }

  /// Copia il file nella cartella `inbox` dell'app e ne descrive i metadati
  /// di base (durata e codec vengono letti più avanti con ffprobe).
  Future<AudioSource?> adoptFile(
    String sourcePath, {
    String? name,
    AudioOrigin origin = AudioOrigin.manuale,
  }) async {
    try {
      final File src = File(sourcePath);
      if (!src.existsSync()) {
        debugPrint('File condiviso non trovato: $sourcePath');
        return null;
      }
      final String fileName = name ?? p.basename(sourcePath);
      final Directory inbox = await inboxDirectory();
      final String stamp = DateTime.now().millisecondsSinceEpoch.toString();
      final String safeName = fileName
          .replaceAll(RegExp(r'[^\w\s.\-àèéìòùÀÈÉÌÒÙ]'), '_')
          .replaceAll(RegExp(r'\s+'), ' ')
          .trim();
      final File dest = File(p.join(inbox.path, '${stamp}_$safeName'));
      await src.copy(dest.path);

      return AudioSource(
        path: dest.path,
        displayName: safeName,
        sizeBytes: dest.lengthSync(),
        duration: Duration.zero,
        origin: origin,
      );
    } on FileSystemException catch (e) {
      throw AppFailure(
        'Non riesco a leggere il file condiviso. Riprova a condividerlo.',
        cause: e,
      );
    }
  }

  bool _looksLikeAudio(SharedMediaFile f) {
    final String? mime = f.mimeType?.toLowerCase();
    if (mime != null && mime.startsWith('audio/')) return true;
    // WhatsApp e alcuni client inviano gli audio con mime generico.
    final String ext =
        p.extension(_cleanPath(f.path)).replaceFirst('.', '').toLowerCase();
    if (AudioLimits.knownExtensions.contains(ext)) return true;
    if (mime != null && mime.startsWith('video/')) {
      // Un .mp4 con sola traccia audio è comune: lo accettiamo e sarà
      // ffprobe a dire se contiene audio.
      return true;
    }
    return false;
  }

  /// Su iOS il path può arrivare come `file:///…`.
  String _cleanPath(String raw) {
    if (raw.startsWith('file://')) {
      return Uri.parse(raw).toFilePath();
    }
    return raw;
  }

  static Future<Directory> inboxDirectory() async {
    final Directory base = await getApplicationSupportDirectory();
    final Directory inbox = Directory(p.join(base.path, 'inbox'));
    await inbox.create(recursive: true);
    return inbox;
  }

  /// Elimina gli audio importati e non più serviti.
  ///
  /// Chiamata all'avvio: i file più vecchi di [maxAge] e quelli non
  /// referenziati da una nota vengono rimossi (minimizzazione dei dati).
  static Future<int> cleanupInbox({
    Duration maxAge = const Duration(days: 2),
    Set<String> keepPaths = const <String>{},
  }) async {
    int removed = 0;
    try {
      final Directory inbox = await inboxDirectory();
      final DateTime limit = DateTime.now().subtract(maxAge);
      for (final FileSystemEntity e in inbox.listSync()) {
        if (e is! File) continue;
        if (keepPaths.contains(e.path)) continue;
        if (e.statSync().modified.isBefore(limit)) {
          await e.delete();
          removed++;
        }
      }
    } on FileSystemException catch (e) {
      debugPrint('Pulizia inbox non riuscita: $e');
    }
    return removed;
  }

  Future<void> dispose() async {
    await _subscription?.cancel();
    await _controller.close();
  }
}
