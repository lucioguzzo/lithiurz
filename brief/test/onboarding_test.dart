import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:brief/core/constants.dart';
import 'package:brief/data/settings.dart';
import 'package:brief/services/transcription/engine.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  group('primo avvio', () {
    test('un\'installazione nuova non è ancora passata dal benvenuto',
        () async {
      SharedPreferences.setMockInitialValues(<String, Object>{});
      final SettingsRepository repo = await SettingsRepository.open();
      expect(repo.value.onboardedAt, isNull);
    });

    test('la scelta fatta al benvenuto viene ricordata', () async {
      SharedPreferences.setMockInitialValues(<String, Object>{});
      final SettingsRepository repo = await SettingsRepository.open();
      final DateTime now = DateTime.now();
      await repo.update(repo.value.copyWith(
        transcriptionEngine: TranscriptionEngineId.cloud,
        onboardedAt: now,
        audioCloudConsentAt: now,
      ));

      final SettingsRepository riaperto = await SettingsRepository.open();
      expect(riaperto.value.onboardedAt, isNotNull);
      expect(riaperto.value.transcriptionEngine, TranscriptionEngineId.cloud);
      // Scegliere il cloud al benvenuto vale come consenso: il testo
      // mostrato lì dice cosa viene inviato.
      expect(riaperto.value.audioCloudAllowed, isTrue);
    });

    test('scegliendo il locale non resta nessun consenso all\'invio',
        () async {
      SharedPreferences.setMockInitialValues(<String, Object>{});
      final SettingsRepository repo = await SettingsRepository.open();
      await repo.update(repo.value.copyWith(
        transcriptionEngine: TranscriptionEngineId.whisper,
        onboardedAt: DateTime.now(),
      ));
      final SettingsRepository riaperto = await SettingsRepository.open();
      expect(riaperto.value.audioCloudConsentAt, isNull);
      expect(riaperto.value.audioCloudAllowed, isFalse);
    });

    test('chi aggiorna da una versione precedente non rivede il benvenuto '
        'per sbaglio… lo vede una volta sola', () async {
      // Una vecchia installazione non ha la chiave: il benvenuto compare
      // una volta e poi non torna più.
      SharedPreferences.setMockInitialValues(<String, Object>{
        'transcription_engine': 'whisper',
      });
      final SettingsRepository repo = await SettingsRepository.open();
      expect(repo.value.onboardedAt, isNull);

      await repo.update(repo.value.copyWith(onboardedAt: DateTime.now()));
      final SettingsRepository riaperto = await SettingsRepository.open();
      expect(riaperto.value.onboardedAt, isNotNull);
    });
  });

  group('configurazione di build', () {
    test('l\'indirizzo del server arriva dalla compilazione', () {
      // Due casi, entrambi verificati davvero eseguendo i test con e
      // senza il parametro:
      //   flutter test                      → nessun server, app locale
      //   flutter test --dart-define=BRIEF_BACKEND_URL=https://…
      //                                     → hasBackend vero
      // È questo che rende possibile consegnare un APK già pronto:
      // l'indirizzo è cucito nella build, non chiesto all'utente.
      expect(BuildConfig.hasBackend, BuildConfig.backendUrl.trim().isNotEmpty);
      if (BuildConfig.hasBackend) {
        expect(BuildConfig.backendUrl, startsWith('http'));
      }
    });
  });
}
