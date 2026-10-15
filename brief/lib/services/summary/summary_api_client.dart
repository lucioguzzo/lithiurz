import 'dart:async';
import 'dart:convert';
import 'dart:io';

import 'package:http/http.dart' as http;

import '../../core/constants.dart';
import '../../core/failures.dart';
import '../../domain/summary.dart';

/// Client del backend dei riassunti.
///
/// Al server viene inviato **solo il testo** della trascrizione: mai l'audio.
/// Le chiavi dei provider IA restano sul server.
class SummaryApiClient {
  SummaryApiClient({
    required String baseUrl,
    required this.deviceId,
    http.Client? client,
    this.appToken = const String.fromEnvironment('BRIEF_APP_TOKEN'),
    this.timeout = const Duration(seconds: 120),
  })  : _baseUrl = baseUrl,
        _client = client ?? http.Client();

  /// URL del backend, es. `https://api.example.com`. Modificabile a caldo:
  /// cambiandolo nelle impostazioni la richiesta successiva lo usa già.
  String _baseUrl;
  String get baseUrl => _baseUrl;
  set baseUrl(String value) => _baseUrl = value.trim();

  /// Identificativo anonimo usato dal backend per le quote.
  final String deviceId;

  /// Token pubblico dell'app (non è un segreto: serve solo a scremare il
  /// traffico. Le quote reali sono per dispositivo).
  final String appToken;
  final Duration timeout;
  final http.Client _client;

  bool get isConfigured => baseUrl.trim().isNotEmpty;

  Uri _uri(String path) {
    final String base =
        baseUrl.trim().endsWith('/') ? baseUrl.trim() : '${baseUrl.trim()}/';
    return Uri.parse(base).resolve(path);
  }

  Future<SummaryBundle> summarize({
    required String transcript,
    required SummaryStyle style,
    String language = 'it',
  }) async {
    if (!isConfigured) {
      throw const AppFailure(
        'Nessun server dei riassunti configurato. Imposta l\'indirizzo nelle '
        'impostazioni oppure usa la modalità solo locale.',
        recoverable: false,
      );
    }

    try {
      final http.Response res = await _client
          .post(
            _uri('v1/summarize'),
            headers: <String, String>{
              'content-type': 'application/json; charset=utf-8',
              'x-device-id': deviceId,
              if (appToken.isNotEmpty) 'x-app-token': appToken,
            },
            body: jsonEncode(<String, Object?>{
              'text': transcript,
              'style': style.id,
              'language': language,
            }),
          )
          .timeout(timeout);

      if (res.statusCode == 429) {
        throw const AppFailure(
          'Hai raggiunto il limite di riassunti cloud per oggi. Riprova '
          'domani oppure usa il riassunto locale.',
          recoverable: false,
        );
      }
      if (res.statusCode == 503) {
        throw const AppFailure(
          'Il servizio di riassunto è temporaneamente sospeso. Puoi usare il '
          'riassunto locale.',
        );
      }
      if (res.statusCode == 401 || res.statusCode == 403) {
        throw const AppFailure(
          'Questa versione dell\'app non è autorizzata al servizio cloud.',
          recoverable: false,
        );
      }
      if (res.statusCode >= 400) {
        throw AppFailure(
          'Il server ha risposto con un errore (${res.statusCode}).',
        );
      }

      final Object? decoded = jsonDecode(utf8.decode(res.bodyBytes));
      if (decoded is! Map<String, dynamic>) {
        throw const AppFailure('Risposta del server non valida.');
      }
      final Map<String, dynamic> json = decoded;
      json['style'] = style.id;
      json['engine'] = SummaryEngine.cloud.id;
      return SummaryBundle.fromJson(json);
    } on TimeoutException {
      throw const AppFailure(
        'Il server non ha risposto in tempo. Riprova o usa il riassunto '
        'locale.',
      );
    } on SocketException catch (e) {
      throw AppFailure(
        'Connessione assente: non riesco a raggiungere il server dei '
        'riassunti.',
        cause: e,
      );
    } on FormatException catch (e) {
      throw AppFailure('Risposta del server illeggibile.', cause: e);
    }
  }

  /// Stato del servizio e quota residua del dispositivo.
  Future<Map<String, dynamic>?> status() async {
    if (!isConfigured) return null;
    try {
      final http.Response res = await _client.get(
        _uri('v1/status'),
        headers: <String, String>{
          'x-device-id': deviceId,
          if (appToken.isNotEmpty) 'x-app-token': appToken,
        },
      ).timeout(const Duration(seconds: 15));
      if (res.statusCode != 200) return null;
      final Object? decoded = jsonDecode(utf8.decode(res.bodyBytes));
      return decoded is Map<String, dynamic> ? decoded : null;
    } on Object {
      return null;
    }
  }

  void dispose() => _client.close();
}
