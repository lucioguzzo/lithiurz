import 'dart:async';
import 'dart:io';

import 'package:device_info_plus/device_info_plus.dart';
import 'package:flutter/foundation.dart';
import 'package:whisper_ggml/whisper_ggml.dart';

import '../../core/failures.dart';

/// Descrizione di un modello Whisper proposto all'utente.
class ModelOption {
  const ModelOption({
    required this.id,
    required this.model,
    required this.label,
    required this.approxBytes,
    required this.ramHintMb,
    required this.description,
  });

  final String id;
  final WhisperModel model;
  final String label;
  final int approxBytes;

  /// RAM consigliata sul dispositivo per usarlo senza affanni.
  final int ramHintMb;
  final String description;

  static const List<ModelOption> all = <ModelOption>[
    ModelOption(
      id: 'tiny',
      model: WhisperModel.tiny,
      label: 'Tiny (multilingua)',
      approxBytes: 77 * 1024 * 1024,
      ramHintMb: 1500,
      description: 'Il più veloce e leggero. Qualità sufficiente per note '
          'brevi e parlato chiaro.',
    ),
    ModelOption(
      id: 'base',
      model: WhisperModel.base,
      label: 'Base (multilingua)',
      approxBytes: 148 * 1024 * 1024,
      ramHintMb: 2500,
      description: 'Buon compromesso fra velocità e precisione in italiano. '
          'Consigliato come predefinito.',
    ),
    ModelOption(
      id: 'small',
      model: WhisperModel.small,
      label: 'Small (multilingua)',
      approxBytes: 488 * 1024 * 1024,
      ramHintMb: 4000,
      description: 'Più preciso su audio rumorosi o con più voci. Richiede un '
          'telefono recente: è 3-4 volte più lento di Base.',
    ),
  ];

  static ModelOption byId(String id) => all.firstWhere(
        (ModelOption o) => o.id == id,
        orElse: () => all[1],
      );
}

/// Avanzamento del download di un modello.
class ModelDownloadProgress {
  const ModelDownloadProgress(this.received, this.total);
  final int received;
  final int total;

  double? get fraction => total > 0 ? received / total : null;
}

/// Scarica, elenca ed elimina i modelli ggml.
class ModelManager {
  ModelManager({WhisperController? controller})
      : _controller = controller ?? WhisperController();

  final WhisperController _controller;
  HttpClient? _client;

  Future<String> pathFor(ModelOption option) =>
      _controller.getPath(option.model);

  Future<bool> isInstalled(ModelOption option) async {
    final File f = File(await pathFor(option));
    // Un file troncato da un download interrotto non va considerato valido.
    return f.existsSync() && f.lengthSync() > option.approxBytes * 0.6;
  }

  Future<int> installedBytes() async {
    int total = 0;
    for (final ModelOption o in ModelOption.all) {
      final File f = File(await pathFor(o));
      if (f.existsSync()) total += f.lengthSync();
    }
    return total;
  }

  /// Scarica il modello in un file temporaneo e lo rinomina solo al termine,
  /// così un'interruzione non lascia un modello corrotto.
  Future<String> download(
    ModelOption option, {
    void Function(ModelDownloadProgress)? onProgress,
    CancellationToken? token,
  }) async {
    final String target = await pathFor(option);
    if (await isInstalled(option)) return target;

    final File tmp = File('$target.part');
    await tmp.parent.create(recursive: true);
    if (tmp.existsSync()) await tmp.delete();

    final HttpClient client = _client ??= HttpClient()
      ..connectionTimeout = const Duration(seconds: 30);

    IOSink? sink;
    try {
      final HttpClientRequest request =
          await client.getUrl(option.model.modelUri);
      final HttpClientResponse response = await request.close();
      if (response.statusCode != 200) {
        throw AppFailure(
          'Download del modello non riuscito (codice ${response.statusCode}).',
        );
      }
      final int total = response.contentLength > 0
          ? response.contentLength
          : option.approxBytes;

      sink = tmp.openWrite();
      int received = 0;
      await for (final List<int> chunk in response) {
        if (token?.isCancelled ?? false) {
          throw const CancelledException();
        }
        sink.add(chunk);
        received += chunk.length;
        onProgress?.call(ModelDownloadProgress(received, total));
      }
      await sink.flush();
      await sink.close();
      sink = null;

      await tmp.rename(target);
      return target;
    } on CancelledException {
      await sink?.close();
      if (tmp.existsSync()) await tmp.delete();
      rethrow;
    } on SocketException catch (e) {
      await sink?.close();
      if (tmp.existsSync()) await tmp.delete();
      throw AppFailure(
        'Connessione assente o interrotta durante il download del modello.',
        cause: e,
      );
    } on Object catch (e) {
      await sink?.close();
      if (tmp.existsSync()) await tmp.delete();
      if (e is AppFailure) rethrow;
      throw AppFailure('Download del modello non riuscito.', cause: e);
    }
  }

  Future<void> delete(ModelOption option) async {
    final File f = File(await pathFor(option));
    if (f.existsSync()) await f.delete();
    final File part = File('${f.path}.part');
    if (part.existsSync()) await part.delete();
  }

  /// Modello consigliato in base alla RAM del dispositivo.
  Future<ModelOption> recommended() async {
    final int? ram = await _ramMb();
    if (ram == null) return ModelOption.byId('base');
    if (ram >= 5800) return ModelOption.byId('small');
    if (ram >= 2800) return ModelOption.byId('base');
    return ModelOption.byId('tiny');
  }

  Future<int?> _ramMb() async {
    try {
      final DeviceInfoPlugin info = DeviceInfoPlugin();
      // device_info_plus espone la RAM già in megabyte.
      if (Platform.isAndroid) {
        final AndroidDeviceInfo a = await info.androidInfo;
        return a.physicalRamSize > 0 ? a.physicalRamSize : null;
      }
      if (Platform.isIOS) {
        final IosDeviceInfo i = await info.iosInfo;
        return i.physicalRamSize > 0 ? i.physicalRamSize : null;
      }
    } on Object catch (e) {
      debugPrint('Lettura RAM non riuscita: $e');
    }
    return null;
  }

  void dispose() {
    _client?.close(force: true);
    _client = null;
  }
}
