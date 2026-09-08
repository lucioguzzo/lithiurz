// Monta le schermate vere. I test sui contenuti verificano che i dati siano
// coerenti; questi verificano che l'app li sappia mostrare - che e' l'altra
// meta' del problema, e quella che l'utente vede.
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:provider/provider.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:wing_chun_sifu/core/theme.dart';
import 'package:wing_chun_sifu/data/content_repository.dart';
import 'package:wing_chun_sifu/data/progress_service.dart';
import 'package:wing_chun_sifu/features/forms/forms_screen.dart';
import 'package:wing_chun_sifu/features/history/history_screen.dart';
import 'package:wing_chun_sifu/features/home/home_screen.dart';
import 'package:wing_chun_sifu/features/principles/principles_screen.dart';
import 'package:wing_chun_sifu/features/videos/videos_screen.dart';

Widget _wrap(Widget child) => ChangeNotifierProvider<ProgressService>.value(
      value: ProgressService.instance,
      child: MaterialApp(theme: AppTheme.build(), home: child),
    );

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  // Il riquadro predefinito dei test e' 800x600: le liste sono pigre e non
  // costruirebbero le voci sotto la piega. Uno schermo alto le rende tutte.
  setUp(() {
    final view = TestWidgetsFlutterBinding.instance.platformDispatcher.views.first;
    view.physicalSize = const Size(1000, 3200);
    view.devicePixelRatio = 1.0;
    addTearDown(() {
      view.resetPhysicalSize();
      view.resetDevicePixelRatio();
    });
  });

  setUpAll(() async {
    SharedPreferences.setMockInitialValues({});
    await ContentRepository.instance.load();
    await ProgressService.instance.load();
  });

  testWidgets('il menu principale elenca tutte e cinque le sezioni',
      (tester) async {
    await tester.pumpWidget(_wrap(const HomeScreen()));
    await tester.pump(const Duration(seconds: 2));

    expect(find.text('Storia del Wing Chun'), findsOneWidget);
    expect(find.text('Principi e tecniche di base'), findsOneWidget);
    expect(find.text('Forme'), findsOneWidget);
    expect(find.text('3D SiFu — Lessons'), findsOneWidget);
    expect(find.text('Video selection'), findsOneWidget);
  });

  testWidgets('la storia mostra i capitoli e dichiara i contenuti provvisori',
      (tester) async {
    await tester.pumpWidget(_wrap(const HistoryScreen()));
    await tester.pump(const Duration(seconds: 2));

    final repo = ContentRepository.instance;
    expect(find.text(repo.historyChapters.first.title), findsOneWidget);
    if (repo.historyStatus == 'provvisorio') {
      expect(find.textContaining('provvisori'), findsWidgets);
    }
  });

  testWidgets('i principi mostrano i gruppi e le voci', (tester) async {
    await tester.pumpWidget(_wrap(const PrinciplesScreen()));
    await tester.pump(const Duration(seconds: 2));

    expect(find.text('La struttura'), findsOneWidget);
    expect(find.text('La linea centrale'), findsOneWidget);
  });

  testWidgets('le forme sono raggruppate per lignaggio', (tester) async {
    await tester.pumpWidget(_wrap(const FormsScreen()));
    await tester.pump(const Duration(seconds: 2));

    expect(find.text('Siu Nim Tau'), findsOneWidget);
    expect(find.text('Linea Ip Man'), findsOneWidget);
  });

  testWidgets('la selezione video mostra categorie e canali ufficiali',
      (tester) async {
    await tester.pumpWidget(_wrap(const VideosScreen()));
    await tester.pump(const Duration(seconds: 2));

    expect(find.text('Il Wing Chun interno'), findsOneWidget);
    expect(find.textContaining('Eternal Spring Institute'), findsWidgets);
  });

  testWidgets('segnare una voce come studiata aggiorna il percorso',
      (tester) async {
    final repo = ContentRepository.instance;
    final id = repo.allPrinciples.first.id;

    await ProgressService.instance.reset();
    expect(ProgressService.instance.isStudied(id), isFalse);

    await ProgressService.instance.markStudied(id);
    expect(ProgressService.instance.isStudied(id), isTrue);

    await tester.pumpWidget(_wrap(const HomeScreen()));
    await tester.pump(const Duration(seconds: 2));
    expect(find.text('Il tuo percorso'.toUpperCase()), findsOneWidget);

    await ProgressService.instance.reset();
  });
}
