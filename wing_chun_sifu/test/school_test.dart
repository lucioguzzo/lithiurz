// L'app segue una linea di trasmissione precisa. Questi test controllano che
// resti tale nei dati, e che non ricompaia un collegamento sbagliato.
import 'dart:convert';
import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:provider/provider.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:wing_chun_sifu/core/theme.dart';
import 'package:wing_chun_sifu/data/content_repository.dart';
import 'package:wing_chun_sifu/data/progress_service.dart';
import 'package:wing_chun_sifu/features/school/school_screen.dart';

/// Pagine indicate come non pertinenti: erano finite nell'app per errore,
/// e un test e' il modo piu' economico per non rimetterle.
const _linkErrati = <String>[
  'IDPA-Wing-Chun-Napoli-100063759681288',
  '100063759681288',
];

String _tuttiIDati() {
  final buffer = StringBuffer();
  for (final f in Directory('assets/data').listSync().whereType<File>()) {
    buffer.write(f.readAsStringSync());
  }
  return buffer.toString();
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  test('nessun collegamento a pagine non pertinenti', () {
    final dati = _tuttiIDati();
    for (final errato in _linkErrati) {
      expect(dati.contains(errato), isFalse,
          reason: 'ricomparso un collegamento sbagliato: $errato');
    }
  });

  test('i collegamenti ufficiali della scuola ci sono tutti', () {
    final school =
        jsonDecode(File('assets/data/school.json').readAsStringSync())
            as Map<String, dynamic>;
    final urls = (school['links'] as List)
        .map((l) => (l as Map<String, dynamic>)['url'] as String)
        .toList();

    for (final atteso in [
      'youtube.com/@idpakombat8358',
      'youtube.com/@idpatube',
      'facebook.com/IDPAKOMBAT',
      'instagram.com/idpakombat',
      'facebook.com/100057520717123',
    ]) {
      expect(urls.any((u) => u.contains(atteso)), isTrue,
          reason: 'manca il collegamento ufficiale: $atteso');
    }
    expect(school['sifu']['name'], contains('Fiorentini'));
    expect((school['seniors']['names'] as List), isNotEmpty);
  });

  test('la scuola apre le sezioni video e forme', () {
    final videos = jsonDecode(File('assets/data/videos.json').readAsStringSync())
        as Map<String, dynamic>;
    final prima = (videos['categories'] as List).first as Map<String, dynamic>;
    expect(prima['subtitle'].toString().contains('Fiorentini'), isTrue,
        reason: 'la prima categoria video deve essere quella dell\'IDPA');

    final forms = jsonDecode(File('assets/data/forms.json').readAsStringSync())
        as Map<String, dynamic>;
    final ordinate = (forms['forms'] as List).cast<Map<String, dynamic>>()
      ..sort((a, b) => (a['order'] as int).compareTo(b['order'] as int));
    expect(ordinate.first['lineage'], 'wengchun',
        reason: 'la linea tecnica della scuola viene per prima');
  });

  test('la maggior parte dei video viene dai canali della scuola', () {
    final videos = jsonDecode(File('assets/data/videos.json').readAsStringSync())
        as Map<String, dynamic>;
    var totale = 0, idpa = 0;
    for (final c in videos['categories'] as List) {
      for (final v in (c as Map<String, dynamic>)['videos'] as List) {
        totale++;
        if ((v as Map<String, dynamic>)['author'].toString().contains('IDPA')) {
          idpa++;
        }
      }
    }
    expect(idpa * 2, greaterThan(totale),
        reason: 'i canali IDPA devono essere la fonte prevalente '
            '($idpa su $totale)');
  });

  testWidgets('la schermata della scuola mostra Maestro e collegamenti',
      (tester) async {
    SharedPreferences.setMockInitialValues({});
    await ContentRepository.instance.load();
    await ProgressService.instance.load();

    final view = TestWidgetsFlutterBinding.instance.platformDispatcher.views.first;
    view.physicalSize = const Size(1000, 3200);
    view.devicePixelRatio = 1.0;
    addTearDown(() {
      view.resetPhysicalSize();
      view.resetDevicePixelRatio();
    });

    await tester.pumpWidget(ChangeNotifierProvider<ProgressService>.value(
      value: ProgressService.instance,
      child: MaterialApp(theme: AppTheme.build(), home: const SchoolScreen()),
    ));
    await tester.pump(const Duration(seconds: 2));

    final s = ContentRepository.instance.school;
    expect(find.text(s.sifuName), findsOneWidget);
    expect(find.text(s.fullName), findsOneWidget);
    for (final n in s.seniors) {
      expect(find.text(n), findsOneWidget);
    }
    expect(find.text(s.links.first.label), findsOneWidget);
  });
}
