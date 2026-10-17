import 'dart:async';
import 'dart:convert';
import 'dart:io';

import 'package:http/http.dart' as http;

import '../../core/constants.dart';
import '../../core/failures.dart';
import 'engine.dart';

/// Trascrizione eseguita dal backend di BRIEF, che a sua volta interroga
/// il fornitore configurato (oggi Gemini).
///
/// È l'unico motore che fa uscire l'audio dal telefono, quindi è spento
/// di default e si accende solo con un consenso esplicito: la scelta la
/// fa l'utente nelle impostazioni, questa classe si limita a eseguirla.
///
/// Viaggia un blocco alla volta, come per gli altri motori: l'utente vede
/// il testo crescere, un errore di rete costa un blocco e non l'intera
/// registrazione, e nessuna richiesta sfiora i limiti di dimensione del
/// fornitore.
class CloudTranscriptionEngine implements TranscriptionEngine {
  CloudTranscriptionEngine({
    required this.baseUrl,
    required this.deviceId,
    http.Client? client,
    this.appToken = BuildConfig.appToken,
    this.timeout = const Duration(seconds: 180),
  }) : _client = client ?? http.Client();

  /// Letto a ogni richiesta: cambiando server nelle impostazioni, la
  /// trascrizione successiva usa già quello nuovo.
  final String Function() baseUrl;
  final String deviceId;
  final String appToken;
  final Duration timeout;
  final http.Client _client;

  @override
  TranscriptionEngineId get id => TranscriptionEngineId.cloud;

  /// Blocchi più lunghi degli altri motori: ogni blocco è una richiesta di
  /// rete, e spezzettare troppo moltiplica i tempi morti. Restano però
  /// abbastanza brevi da non superare i limiti di dimensione del
  /// fornitore: tre minuti di WAV a 16 kHz mono sono circa 5,5 MB.
  @override
  Duration get preferredChunkLength => const Duration(minutes: 3);

  bool get isConfigured => baseUrl().trim().isNotEmpty;

  @override
  Future<bool> isAvailable() async => isConfigured;

  /// Perché il motore non è utilizzabile, da mostrare nelle impostazioni.
  String? unavailableReason() => isConfigured
      ? null
      : 'Manca l\'indirizzo del server: impostalo qui sotto, in "Server dei '
          'riassunti".';

  Uri _uri(String path) {
    final String base = baseUrl().trim();
    return Uri.parse(base.endsWith('/') ? base : '$base/').resolve(path);
  }

  @override
  Future<String> transcribeFile({
    required String wavPath,
    required String language,
    void Function(int percent)? onProgress,
  }) async {
    if (!isConfigured) {
      throw const AppFailure(
        'Nessun server configurato per la trascrizione nel cloud. Imposta '
        'l\'indirizzo nelle impostazioni oppure scegli Whisper.',
        recoverable: false,
      );
    }

    // Il fornitore non riporta un avanzamento interno: si segnala la
    // partenza, poi è la successione dei blocchi a scandire la barra.
    onProgress?.call(5);

    final http.MultipartRequest request =
        http.MultipartRequest('POST', _uri('v1/transcribe'))
          ..headers['x-device-id'] = deviceId
          ..fields['language'] = language
          ..files.add(await http.MultipartFile.fromPath('file', wavPath));
    if (appToken.isNotEmpty) {
      request.headers['x-app-token'] = appToken;
    }

    try {
      final http.StreamedResponse streamed =
          await _client.send(request).timeout(timeout);
      final http.Response res = await http.Response.fromStream(streamed);
      onProgress?.call(100);

      if (res.statusCode >= 400) throw _failureFor(res);

      final Object? decoded = jsonDecode(utf8.decode(res.bodyBytes));
      if (decoded is! Map<String, dynamic> || decoded['text'] is! String) {
        throw const AppFailure('Risposta del server non valida.');
      }
      return (decoded['text'] as String).trim();
    } on TimeoutException {
      throw const AppFailure(
        'Il server non ha risposto in tempo. Riprova, oppure passa a '
        'Whisper per trascrivere senza connessione.',
      );
    } on SocketException catch (e) {
      throw AppFailure(
        'Connessione assente: la trascrizione nel cloud richiede internet.',
        cause: e,
      );
    } on FormatException catch (e) {
      throw AppFailure('Risposta del server illeggibile.', cause: e);
    }
  }

  AppFailure _failureFor(http.Response res) => switch (res.statusCode) {
        429 => const AppFailure(
            'Hai raggiunto il limite giornaliero di trascrizioni nel cloud. '
            'Riprova domani oppure passa a Whisper.',
            recoverable: false,
          ),
        503 => const AppFailure(
            'Il servizio di trascrizione è temporaneamente sospeso. Puoi '
            'continuare con Whisper.',
          ),
        401 || 403 => const AppFailure(
            'Questa versione dell\'app non è autorizzata al servizio cloud.',
            recoverable: false,
          ),
        413 => const AppFailure(
            'Il blocco audio è troppo grande per il server.',
          ),
        415 => const AppFailure(
            'Il server non ha riconosciuto il formato dell\'audio.',
          ),
        _ => AppFailure('Il server ha risposto con un errore (${res.statusCode}).'),
      };

  @override
  Future<void> release() async {}

  void dispose() => _client.close();
}
