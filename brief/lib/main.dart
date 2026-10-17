import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import 'app_services.dart';
import 'core/constants.dart';
import 'data/note_repository.dart';
import 'data/settings.dart';
import 'services/share/share_intake_service.dart';
import 'services/transcription/model_manager.dart';
import 'ui/screens/home_screen.dart';
import 'ui/screens/onboarding_screen.dart';
import 'ui/theme.dart';

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();
  await SystemChrome.setPreferredOrientations(<DeviceOrientation>[
    DeviceOrientation.portraitUp,
    DeviceOrientation.portraitDown,
  ]);

  final SettingsRepository settings = await SettingsRepository.open();
  final NoteRepository notes = await NoteRepository.open();
  final AppServices services =
      await AppServices.bootstrap(settings: settings, notes: notes);

  // Minimizzazione dei dati: gli audio importati e non più necessari
  // vengono eliminati all'avvio.
  unawaitedCleanup(notes);

  runApp(BRIEFApp(services: services));
}

/// Pulizia di avvio, senza bloccare l'apertura dell'app: audio importati e
/// non più serviti, e modelli delle versioni precedenti (Tiny e Base non
/// vengono più usati e occupano centinaia di megabyte).
void unawaitedCleanup(NoteRepository notes) {
  Future<void>(() async {
    final Set<String> keep = <String>{
      for (final note in await notes.list(limit: 1000))
        if (note.audioPath != null) note.audioPath!,
    };
    await ShareIntakeService.cleanupInbox(keepPaths: keep);
    final int freed = await ModelManager().deleteLegacyModels();
    if (freed > 0) {
      debugPrint('Liberati $freed byte di modelli non più usati.');
    }
  }).ignore();
}

class BRIEFApp extends StatelessWidget {
  const BRIEFApp({super.key, required this.services});

  final AppServices services;

  @override
  Widget build(BuildContext context) {
    return ServicesScope(
      services: services,
      child: ValueListenableBuilder<AppSettings>(
        valueListenable: services.settings,
        builder: (BuildContext context, AppSettings settings, _) {
          return MaterialApp(
            title: AppInfo.fullName,
            debugShowCheckedModeBanner: false,
            theme: AppTheme.light(),
            darkTheme: AppTheme.dark(),
            themeMode: switch (settings.themeMode) {
              ThemeModeSetting.chiaro => ThemeMode.light,
              ThemeModeSetting.scuro => ThemeMode.dark,
              ThemeModeSetting.sistema => ThemeMode.system,
            },
            // Primo avvio: si sceglie una volta come trascrivere, poi
            // non si vede più.
            home: settings.onboardedAt == null
                ? const OnboardingScreen()
                : const HomeScreen(),
          );
        },
      ),
    );
  }
}
