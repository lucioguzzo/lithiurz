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
    required this.downloadUrl,
  });

  /// Da dove scaricarlo. Non si usa l'URL del pacchetto perché BRIEF
  /// preferisce la versione quantizzata, che pesa e impiega meno.
  final String downloadUrl;

  final String id;
  final WhisperModel model;
  final String label;
  final int approxBytes;

  /// RAM consigliata sul dispositivo per usarlo senza affanni.
  final int ramHintMb;
  final String description;

  /// Unico modello offerto.
  ///
  /// Tiny e Base erano stati inclusi per i telefoni più modesti, ma sul
  /// parlato italiano reale (voce al telefono, rumore di fondo, nomi propri)
  /// sbagliavano troppo per essere utili: un riassunto costruito su una
  /// trascrizione sbagliata è peggio di nessun riassunto. Resta Small.
  static const ModelOption small = ModelOption(
    id: 'small',
    model: WhisperModel.small,
    label: 'Small quantizzato (multilingua)',
    // La versione q5_1 pesa 181 MB invece di 465 ed è circa due volte più
    // rapida, con una perdita di precisione trascurabile sul parlato.
    approxBytes: 181 * 1024 * 1024,
    ramHintMb: 2500,
    description: 'Trascrive l\'italiano parlato anche con rumore di fondo. '
        'Versione compressa: 181 MB invece di 465 e circa il doppio più '
        'veloce. Si scarica una volta sola e poi funziona offline.',
    downloadUrl: 'https://huggingface.co/ggerganov/whisper.cpp/resolve/main/'
        'ggml-small-q5_1.bin',
  );

  /// Oltre questa dimensione il file scaricato è il modello pieno di una
  /// versione precedente: va sostituito con quello compresso.
  static const int legacyFullModelBytes = 300 * 1024 * 1024;

  static const List<ModelOption> all = <ModelOption>[small];

  /// Rilevatore del parlato (Silero), circa 1 MB.
  ///
  /// Toglie silenzi e rumore prima della decodifica: è lì che whisper
  /// entra negli anelli e ripete la stessa frase per righe intere.
  static const String vadFileName = 'ggml-silero-v5.1.2.bin';
  static final Uri vadUri = Uri.parse(
    'https://huggingface.co/ggml-org/whisper-vad/resolve/main/$vadFileName',
  );

  /// Modelli non più offerti: se sono rimasti sul telefono da una versione
  /// precedente vanno cancellati, occupano spazio per niente.
  static const List<String> legacyFileNames = <String>[
    'ggml-tiny.bin',
    'ggml-base.bin',
    'ggml-tiny.en.bin',
    'ggml-base.en.bin',
  ];

  /// Un identificativo sconosciuto (per esempio 'base', salvato da una
  /// versione precedente) ricade sull'unico modello disponibile.
  static ModelOption byId(String id) => all.firstWhere(
        (ModelOption o) => o.id == id,
        orElse: () => all.first,
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
    if (!f.existsSync()) return false;
    final int size = f.lengthSync();
    // Un file troncato da un download interrotto non è valido.
    if (size < option.approxBytes * 0.6) return false;
    // Né lo è il modello pieno scaricato da una versione precedente: è
    // quello lento, va sostituito con il compresso.
    if (size > ModelOption.legacyFullModelBytes) return false;
    return true;
  }

  /// `true` se sul telefono c'è il modello pieno di una versione
  /// precedente, da sostituire con quello compresso.
  Future<bool> hasLegacyFullModel(ModelOption option) async {
    final File f = File(await pathFor(option));
    return f.existsSync() &&
        f.lengthSync() > ModelOption.legacyFullModelBytes;
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

    // Via il modello pieno della versione precedente: occupa 465 MB e
    // sarebbe comunque sostituito da quello compresso.
    if (await hasLegacyFullModel(option)) {
      await File(target).delete();
    }

    final File tmp = File('$target.part');
    await tmp.parent.create(recursive: true);
    if (tmp.existsSync()) await tmp.delete();

    final HttpClient client = _client ??= HttpClient()
      ..connectionTimeout = const Duration(seconds: 30);

    IOSink? sink;
    try {
      final HttpClientRequest request =
          await client.getUrl(Uri.parse(option.downloadUrl));
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

  /// Percorso locale del rilevatore del parlato.
  Future<String> vadPath() async =>
      '${await WhisperController.getModelDir()}/${ModelOption.vadFileName}';

  /// `null` se il rilevatore non è stato scaricato: la trascrizione
  /// funziona lo stesso, solo senza la pulizia dei silenzi.
  Future<String?> installedVadPath() async {
    final String path = await vadPath();
    final File f = File(path);
    return f.existsSync() && f.lengthSync() > 100 * 1024 ? path : null;
  }

  /// Scarica il rilevatore del parlato. Pesa poco e un errore non è
  /// bloccante: in caso di problemi si prosegue senza.
  Future<bool> downloadVad({CancellationToken? token}) async {
    if (await installedVadPath() != null) return true;
    final String target = await vadPath();
    final File tmp = File('$target.part');
    IOSink? sink;
    try {
      final HttpClient client = _client ??= HttpClient()
        ..connectionTimeout = const Duration(seconds: 30);
      final HttpClientRequest request = await client.getUrl(ModelOption.vadUri);
      final HttpClientResponse response = await request.close();
      if (response.statusCode != 200) return false;
      sink = tmp.openWrite();
      await for (final List<int> chunk in response) {
        if (token?.isCancelled ?? false) throw const CancelledException();
        sink.add(chunk);
      }
      await sink.flush();
      await sink.close();
      sink = null;
      await tmp.rename(target);
      return true;
    } on Object catch (e) {
      debugPrint('Rilevatore del parlato non scaricato: $e');
      await sink?.close();
      if (tmp.existsSync()) await tmp.delete();
      return false;
    }
  }

  Future<void> delete(ModelOption option) async {
    final File f = File(await pathFor(option));
    if (f.existsSync()) await f.delete();
    final File part = File('${f.path}.part');
    if (part.existsSync()) await part.delete();
  }

  /// C'è un solo modello: resta qui per chiarezza nei punti che lo chiedono.
  Future<ModelOption> recommended() async => ModelOption.small;

  /// `true` quando il dispositivo ha poca memoria per il modello scelto.
  /// Non impedisce nulla: serve ad avvisare che potrebbe andare a rilento.
  Future<bool> isTightOnMemory() async {
    final int? ram = await _ramMb();
    if (ram == null) return false;
    return ram < ModelOption.small.ramHintMb;
  }

  /// Elimina i modelli delle versioni precedenti (Tiny e Base), che non
  /// vengono più offerti. Restituisce i byte liberati.
  Future<int> deleteLegacyModels() async {
    int freed = 0;
    try {
      final String dir = await WhisperController.getModelDir();
      for (final String name in ModelOption.legacyFileNames) {
        final File f = File('$dir/$name');
        if (f.existsSync()) {
          freed += f.lengthSync();
          await f.delete();
        }
        final File part = File('$dir/$name.part');
        if (part.existsSync()) await part.delete();
      }
    } on Object catch (e) {
      debugPrint('Pulizia dei modelli non più usati non riuscita: $e');
    }
    return freed;
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
