// Verifiche sui contenuti: sono la parte dell'app che cambia piu' spesso, ed
// e' quella in cui un errore (un JSON malformato, un riferimento a una lezione
// che non esiste) si nota solo a runtime, dentro la schermata sbagliata.
import 'dart:convert';
import 'dart:io';

import 'package:flutter_test/flutter_test.dart';

Map<String, dynamic> _obj(String path) =>
    jsonDecode(File(path).readAsStringSync()) as Map<String, dynamic>;

List<dynamic> _list(String path) =>
    jsonDecode(File(path).readAsStringSync()) as List<dynamic>;

void main() {
  test('tutti i file di contenuto sono JSON validi e non vuoti', () {
    final principles = _obj('assets/data/principles.json');
    final forms = _obj('assets/data/forms.json');
    final videos = _obj('assets/data/videos.json');
    final history = _obj('assets/data/history.json');
    final lessons = _list('assets/data/lessons.json');

    expect((principles['groups'] as List), isNotEmpty);
    expect((forms['forms'] as List), isNotEmpty);
    expect((videos['categories'] as List), isNotEmpty);
    expect((history['chapters'] as List), isNotEmpty);
    expect(lessons, isNotEmpty);
  });

  test('ogni rimando a una lezione 3D punta a una lezione esistente', () {
    final lessonIds = _list('assets/data/lessons.json')
        .map((l) => (l as Map<String, dynamic>)['id'] as String)
        .toSet();

    final missing = <String>{};

    for (final g in _obj('assets/data/principles.json')['groups'] as List) {
      for (final item in (g as Map<String, dynamic>)['items'] as List) {
        final id = (item as Map<String, dynamic>)['lessonId'] as String?;
        if (id != null && !lessonIds.contains(id)) missing.add(id);
      }
    }
    for (final f in _obj('assets/data/forms.json')['forms'] as List) {
      for (final id in ((f as Map<String, dynamic>)['lessonIds'] as List)) {
        if (!lessonIds.contains(id as String)) missing.add(id);
      }
    }

    expect(missing, isEmpty, reason: 'lezioni citate ma inesistenti: $missing');
  });

  test('ogni rimando a un video punta a un video del catalogo', () {
    final videoIds = <String>{
      for (final c in _obj('assets/data/videos.json')['categories'] as List)
        for (final v in (c as Map<String, dynamic>)['videos'] as List)
          (v as Map<String, dynamic>)['id'] as String,
    };

    final missing = <String>{};
    for (final f in _obj('assets/data/forms.json')['forms'] as List) {
      for (final id in ((f as Map<String, dynamic>)['videoIds'] as List)) {
        if (!videoIds.contains(id as String)) missing.add(id);
      }
    }
    expect(missing, isEmpty, reason: 'video citati ma non in catalogo: $missing');
  });

  test('gli identificativi dei video YouTube hanno un formato plausibile', () {
    final re = RegExp(r'^[A-Za-z0-9_-]{11}$');
    for (final c in _obj('assets/data/videos.json')['categories'] as List) {
      for (final v in (c as Map<String, dynamic>)['videos'] as List) {
        final m = v as Map<String, dynamic>;
        expect(re.hasMatch(m['id'] as String), isTrue,
            reason: 'id YouTube non valido: ${m['id']} (${m['title']})');
        expect((m['title'] as String).trim(), isNotEmpty);
        expect((m['author'] as String).trim(), isNotEmpty);
      }
    }
  });

  test('ogni lezione ha titolo, descrizione, durata e inquadratura', () {
    for (final l in _list('assets/data/lessons.json')) {
      final m = l as Map<String, dynamic>;
      expect((m['title'] as String).trim(), isNotEmpty);
      expect((m['description'] as String).trim(), isNotEmpty);
      expect(m['duration'] as num, greaterThan(0));
      final cam = m['camera'] as Map<String, dynamic>;
      for (final k in ['theta', 'phi', 'radius', 'targetY', 'targetZ']) {
        expect(cam[k], isA<num>(), reason: 'camera.$k mancante in ${m['id']}');
      }
      expect(cam['radius'] as num, greaterThan(0));
    }
  });

  test('gli identificativi delle forme e dei principi sono unici', () {
    final formIds = (_obj('assets/data/forms.json')['forms'] as List)
        .map((f) => (f as Map<String, dynamic>)['id'] as String)
        .toList();
    expect(formIds.toSet().length, formIds.length);

    final principleIds = <String>[
      for (final g in _obj('assets/data/principles.json')['groups'] as List)
        for (final i in (g as Map<String, dynamic>)['items'] as List)
          (i as Map<String, dynamic>)['id'] as String,
    ];
    expect(principleIds.toSet().length, principleIds.length);
  });

  test('ogni forma dichiara un lignaggio noto', () {
    final forms = _obj('assets/data/forms.json');
    final known = (forms['lineages'] as Map<String, dynamic>).keys.toSet();
    for (final f in forms['forms'] as List) {
      final m = f as Map<String, dynamic>;
      expect(known, contains(m['lineage']),
          reason: 'lignaggio sconosciuto in ${m['id']}');
    }
  });
}
