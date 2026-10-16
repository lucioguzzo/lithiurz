import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:brief/core/failures.dart';
import 'package:brief/data/settings.dart';
import 'package:brief/services/transcription/engine.dart';
import 'package:brief/services/transcription/model_manager.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  group('scelta del motore', () {
    test('whisper è il motore predefinito', () async {
      SharedPreferences.setMockInitialValues(<String, Object>{});
      final SettingsRepository repo = await SettingsRepository.open();
      expect(repo.value.transcriptionEngine, TranscriptionEngineId.whisper);
    });

    test('la scelta viene salvata e riletta', () async {
      SharedPreferences.setMockInitialValues(<String, Object>{});
      final SettingsRepository repo = await SettingsRepository.open();
      await repo.update(repo.value
          .copyWith(transcriptionEngine: TranscriptionEngineId.sistema));

      final SettingsRepository riaperto = await SettingsRepository.open();
      expect(riaperto.value.transcriptionEngine, TranscriptionEngineId.sistema);
    });

    test('un identificativo sconosciuto ricade su whisper', () {
      expect(TranscriptionEngineId.fromId('inesistente'),
          TranscriptionEngineId.whisper);
      expect(TranscriptionEngineId.fromId(null),
          TranscriptionEngineId.whisper);
      expect(TranscriptionEngineId.fromId('sistema'),
          TranscriptionEngineId.sistema);
    });

    test('cambiare motore non tocca quello del riassunto', () async {
      SharedPreferences.setMockInitialValues(<String, Object>{});
      final SettingsRepository repo = await SettingsRepository.open();
      final engineRiassunto = repo.value.engine;
      await repo.update(repo.value
          .copyWith(transcriptionEngine: TranscriptionEngineId.sistema));
      expect(repo.value.engine, engineRiassunto);
    });
  });

  group('lunghezza dei blocchi', () {
    test('il motore di sistema vuole spezzoni brevi', () {
      // Una sessione di riconoscimento per spezzone: se chiude in anticipo
      // su una pausa si perdono pochi secondi, non metà registrazione.
      final SystemSpeechEngine sistema = SystemSpeechEngine();
      expect(sistema.preferredChunkLength,
          lessThanOrEqualTo(const Duration(seconds: 30)));
    });

    test('whisper preferisce blocchi più lunghi', () {
      final WhisperEngine whisper = WhisperEngine(models: ModelManager());
      expect(whisper.preferredChunkLength,
          greaterThan(const Duration(seconds: 30)));
    });
  });

  group('motore di sistema', () {
    const MethodChannel channel = MethodChannel('brief/system_speech_test');

    void mock(Future<Object?>? Function(MethodCall call)? handler) {
      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
          .setMockMethodCallHandler(channel, handler);
    }

    tearDown(() => mock(null));

    test('restituisce il testo trascritto', () async {
      mock((MethodCall call) async {
        expect(call.method, 'transcribeFile');
        expect(call.arguments['path'], '/tmp/blocco.wav');
        // La lingua va passata in forma completa.
        expect(call.arguments['language'], 'it-IT');
        return '  Ciao, ti confermo la riunione.  ';
      });

      final SystemSpeechEngine engine = SystemSpeechEngine(channel: channel);
      final String text = await engine.transcribeFile(
        wavPath: '/tmp/blocco.wav',
        language: 'it',
      );
      expect(text, 'Ciao, ti confermo la riunione.');
    });

    test('un errore nativo diventa un messaggio per l\'utente', () async {
      mock((MethodCall call) async {
        throw PlatformException(
          code: 'NON_DISPONIBILE',
          message: 'Lingua non scaricata.',
        );
      });

      final SystemSpeechEngine engine = SystemSpeechEngine(channel: channel);
      await expectLater(
        engine.transcribeFile(wavPath: '/tmp/a.wav', language: 'it'),
        throwsA(isA<AppFailure>().having(
          (AppFailure f) => f.message,
          'messaggio',
          'Lingua non scaricata.',
        )),
      );
    });

    test('se il canale non esiste il motore risulta non disponibile',
        () async {
      mock(null);
      final SystemSpeechEngine engine = SystemSpeechEngine(channel: channel);
      expect(await engine.isAvailable(), isFalse);
    });

    test('la disponibilità viene chiesta una volta sola', () async {
      int chiamate = 0;
      mock((MethodCall call) async {
        chiamate++;
        return true;
      });

      final SystemSpeechEngine engine = SystemSpeechEngine(channel: channel);
      expect(await engine.isAvailable(), isTrue);
      expect(await engine.isAvailable(), isTrue);
      expect(chiamate, 1);
    });

    test('segnala inizio e fine alla barra di avanzamento', () async {
      mock((MethodCall call) async => 'testo');
      final List<int> avanzamento = <int>[];
      await SystemSpeechEngine(channel: channel).transcribeFile(
        wavPath: '/tmp/a.wav',
        language: 'it',
        onProgress: avanzamento.add,
      );
      expect(avanzamento.first, lessThan(100));
      expect(avanzamento.last, 100);
    });
  });
}
