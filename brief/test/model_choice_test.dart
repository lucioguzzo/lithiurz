import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:brief/data/settings.dart';
import 'package:brief/services/transcription/model_manager.dart';
import 'package:whisper_ggml/whisper_ggml.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  group('modelli offerti', () {
    test('resta solo Small', () {
      expect(ModelOption.all, hasLength(1));
      expect(ModelOption.all.single.id, 'small');
      expect(ModelOption.all.single.model, WhisperModel.small);
    });

    test('un identificativo di una versione precedente ricade su Small', () {
      // Un'installazione aggiornata può avere 'base' nelle preferenze.
      expect(ModelOption.byId('base').id, 'small');
      expect(ModelOption.byId('tiny').id, 'small');
      expect(ModelOption.byId('').id, 'small');
    });

    test('i modelli da eliminare sono quelli non più offerti', () {
      expect(ModelOption.legacyFileNames, contains('ggml-tiny.bin'));
      expect(ModelOption.legacyFileNames, contains('ggml-base.bin'));
      expect(ModelOption.legacyFileNames,
          isNot(contains('ggml-small.bin')));
    });
  });

  group('impostazioni', () {
    test('il modello predefinito è Small', () async {
      SharedPreferences.setMockInitialValues(<String, Object>{});
      final SettingsRepository repo = await SettingsRepository.open();
      expect(repo.value.modelName, 'small');
      expect(repo.value.whisperModel, WhisperModel.small);
    });

    test('un modello salvato e non più disponibile diventa Small', () async {
      SharedPreferences.setMockInitialValues(<String, Object>{
        'model_name': 'base',
      });
      final SettingsRepository repo = await SettingsRepository.open();
      expect(repo.value.modelName, 'small');
    });

    test('la scelta salvata valida viene conservata', () async {
      SharedPreferences.setMockInitialValues(<String, Object>{
        'model_name': 'small',
      });
      final SettingsRepository repo = await SettingsRepository.open();
      expect(repo.value.modelName, 'small');
    });
  });
}
