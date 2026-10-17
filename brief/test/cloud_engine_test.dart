import 'dart:convert';
import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:brief/core/constants.dart';
import 'package:brief/core/failures.dart';
import 'package:brief/data/settings.dart';
import 'package:brief/services/transcription/cloud_engine.dart';
import 'package:brief/services/transcription/engine.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  late Directory temp;
  late String wavPath;

  setUp(() {
    temp = Directory.systemTemp.createTempSync('brief_cloud');
    wavPath = '${temp.path}/blocco.wav';
    File(wavPath).writeAsBytesSync(List<int>.filled(2048, 7));
  });

  tearDown(() => temp.deleteSync(recursive: true));

  CloudTranscriptionEngine engineWith(
    Future<http.Response> Function(http.Request request) handler, {
    String url = 'https://api.example.com',
  }) =>
      CloudTranscriptionEngine(
        baseUrl: () => url,
        deviceId: 'dispositivo-di-prova',
        client: MockClient(handler),
      );

  group('trascrizione nel cloud', () {
    test('invia il blocco al server e restituisce il testo', () async {
      late http.Request visto;
      final CloudTranscriptionEngine engine = engineWith((req) async {
        visto = req;
        return http.Response(
          jsonEncode(<String, Object?>{
            'text': '  Ciao a tutti  ',
            'provider': 'gemini',
            'model': 'gemini-3.8-flash',
            'seconds': 1.0,
          }),
          200,
          headers: <String, String>{'content-type': 'application/json'},
        );
      });

      final String text = await engine.transcribeFile(
        wavPath: wavPath,
        language: 'it',
      );

      expect(text, 'Ciao a tutti');
      expect(visto.url.path, endsWith('/v1/transcribe'));
      expect(visto.headers['x-device-id'], 'dispositivo-di-prova');
      // Il corpo è multipart: deve contenere il file e la lingua.
      expect(visto.body, contains('blocco.wav'));
      expect(visto.body, contains('it'));
    });

    test('l\'avanzamento parte e arriva a cento', () async {
      final List<int> steps = <int>[];
      final CloudTranscriptionEngine engine = engineWith(
        (req) async => http.Response(jsonEncode(<String, Object?>{'text': 'ok'}), 200),
      );
      await engine.transcribeFile(
        wavPath: wavPath,
        language: 'it',
        onProgress: steps.add,
      );
      expect(steps.first, lessThan(100));
      expect(steps.last, 100);
    });

    test('senza indirizzo del server non è disponibile', () async {
      final CloudTranscriptionEngine engine = engineWith(
        (req) async => http.Response('', 200),
        url: '',
      );
      expect(engine.isConfigured, isFalse);
      expect(await engine.isAvailable(), isFalse);
      expect(engine.unavailableReason(), isNotNull);
      expect(
        () => engine.transcribeFile(wavPath: wavPath, language: 'it'),
        throwsA(isA<AppFailure>()),
      );
    });

    test('la quota esaurita è un errore non recuperabile', () async {
      final CloudTranscriptionEngine engine = engineWith(
        (req) async => http.Response('{"detail":"limite"}', 429),
      );
      await expectLater(
        engine.transcribeFile(wavPath: wavPath, language: 'it'),
        throwsA(isA<AppFailure>()
            .having((AppFailure f) => f.recoverable, 'recoverable', isFalse)),
      );
    });

    test('il servizio sospeso resta un errore ritentabile', () async {
      final CloudTranscriptionEngine engine = engineWith(
        (req) async => http.Response('{"detail":"sospeso"}', 503),
      );
      await expectLater(
        engine.transcribeFile(wavPath: wavPath, language: 'it'),
        throwsA(isA<AppFailure>()
            .having((AppFailure f) => f.recoverable, 'recoverable', isTrue)),
      );
    });

    test('una risposta senza campo testo non passa per buona', () async {
      final CloudTranscriptionEngine engine = engineWith(
        (req) async => http.Response('{"qualcosa":"altro"}', 200),
      );
      await expectLater(
        engine.transcribeFile(wavPath: wavPath, language: 'it'),
        throwsA(isA<AppFailure>()),
      );
    });

    test('usa blocchi più lunghi degli altri motori', () {
      // Ogni blocco è una richiesta di rete: spezzettare troppo
      // moltiplicherebbe i tempi morti.
      final CloudTranscriptionEngine engine = engineWith(
        (req) async => http.Response('{"text":""}', 200),
      );
      expect(engine.preferredChunkLength,
          greaterThan(const Duration(minutes: 1)));
      expect(engine.id, TranscriptionEngineId.cloud);
    });
  });

  group('consenso all\'invio dell\'audio', () {
    test('è separato da quello del riassunto', () async {
      SharedPreferences.setMockInitialValues(<String, Object>{});
      final SettingsRepository repo = await SettingsRepository.open();

      // Consenso al testo: non basta a mandare l'audio.
      await repo.update(repo.value.copyWith(
        engine: SummaryEngine.cloud,
        cloudConsentAt: DateTime.now(),
        transcriptionEngine: TranscriptionEngineId.cloud,
      ));
      expect(repo.value.cloudAllowed, isTrue);
      expect(repo.value.audioCloudAllowed, isFalse);

      await repo.update(
          repo.value.copyWith(audioCloudConsentAt: DateTime.now()));
      expect(repo.value.audioCloudAllowed, isTrue);
    });

    test('viene salvato e riletto, e si può revocare', () async {
      SharedPreferences.setMockInitialValues(<String, Object>{});
      final SettingsRepository repo = await SettingsRepository.open();
      await repo.update(repo.value.copyWith(
        transcriptionEngine: TranscriptionEngineId.cloud,
        audioCloudConsentAt: DateTime.now(),
      ));

      final SettingsRepository riaperto = await SettingsRepository.open();
      expect(riaperto.value.audioCloudConsentAt, isNotNull);
      expect(riaperto.value.transcriptionEngine, TranscriptionEngineId.cloud);

      await riaperto.update(riaperto.value.copyWith(
        transcriptionEngine: TranscriptionEngineId.whisper,
        clearAudioConsent: true,
      ));
      final SettingsRepository terzaApertura = await SettingsRepository.open();
      expect(terzaApertura.value.audioCloudConsentAt, isNull);
      expect(terzaApertura.value.audioCloudAllowed, isFalse);
    });

    test('l\'identificativo del motore cloud sopravvive al salvataggio', () {
      expect(TranscriptionEngineId.fromId('cloud'),
          TranscriptionEngineId.cloud);
      expect(TranscriptionEngineId.cloud.experimental, isFalse);
    });
  });
}
