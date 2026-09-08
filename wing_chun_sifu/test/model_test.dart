// Verifiche sul modello 3D generato: se il file non c'e' o e' malformato,
// la sezione 3D SiFu non funziona, e nessun test di interfaccia se ne accorge.
import 'dart:convert';
import 'dart:io';
import 'dart:typed_data';

import 'package:flutter_test/flutter_test.dart';

void main() {
  final glb = File('assets/models/sifu.glb');

  test('il modello del SiFu esiste ed e\' un GLB valido', () {
    expect(glb.existsSync(), isTrue, reason: 'assets/models/sifu.glb mancante');
    final bytes = glb.readAsBytesSync();
    final data = ByteData.sublistView(bytes);
    expect(data.getUint32(0, Endian.little), 0x46546C67, reason: 'magic glTF');
    expect(data.getUint32(4, Endian.little), 2, reason: 'versione glTF 2.0');
    expect(data.getUint32(8, Endian.little), bytes.length,
        reason: 'lunghezza dichiarata coerente con il file');
  });

  test('il modello contiene tutte le animazioni dichiarate in lessons.json', () {
    final bytes = glb.readAsBytesSync();
    final data = ByteData.sublistView(bytes);
    final jsonLength = data.getUint32(12, Endian.little);
    final gltf = jsonDecode(
        utf8.decode(bytes.sublist(20, 20 + jsonLength))) as Map<String, dynamic>;

    final animations = (gltf['animations'] as List)
        .map((a) => (a as Map<String, dynamic>)['name'] as String)
        .toSet();
    final lessons = (jsonDecode(File('assets/data/lessons.json').readAsStringSync())
            as List)
        .map((l) => (l as Map<String, dynamic>)['id'] as String)
        .toList();

    expect(lessons, isNotEmpty);
    for (final id in lessons) {
      expect(animations, contains(id),
          reason: 'la lezione "$id" non ha un\'animazione nel modello');
    }
    expect(gltf['skins'], isNotEmpty, reason: 'il modello deve essere skinnato');
  });

  test('ogni lezione ha una miniatura', () {
    final lessons = (jsonDecode(File('assets/data/lessons.json').readAsStringSync())
            as List)
        .map((l) => (l as Map<String, dynamic>)['id'] as String);
    for (final id in lessons) {
      expect(File('assets/lessons/$id.png').existsSync(), isTrue,
          reason: 'miniatura mancante per "$id"');
    }
  });
}
