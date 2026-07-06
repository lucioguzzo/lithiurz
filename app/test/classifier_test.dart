import 'dart:convert';
import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:baby_cry_translator/services/classifier.dart';

/// Verifica che il classifier in Dart produca le stesse probabilità della
/// pipeline Python/JS sullo stesso vettore di feature (parità end-to-end).
void main() {
  test('classifier: parità probabilità con Python', () {
    final raw = File('assets/model.json').readAsStringSync();
    final clf = CryClassifier()..loadFromJson(raw);

    const features = [
      -72.0502351753, 3.3287677552, 17.6778046576, 3.9341131619,
      -3.5625348341, 2.0600872749, -25.4099045412, 0.3607924099,
      -4.2319040642, 1.2706207741, 3.1685609014, 1.3389723882,
      -12.3558644322, 0.5867062845, -0.5811837120, 0.3945548485,
      -9.0555227834, 0.8077124120, 20.3038260653, 0.6105765317,
      2.4918083113, 0.1439825701, 6.2680636769, 0.4695872585,
      -9.4150207376, 0.6106419544, 0.1763969090, 0.0516378498,
      -1.0389422783, 0.4587138597, 653.2809358465, 175.2926940113,
    ];

    final pred = clf.predict(features);
    // valori calcolati con la stessa rete in Python
    expect((pred.probabilities['hungry']! - 0.837).abs(), lessThan(0.01));
    expect((pred.probabilities['discomfort']! - 0.161).abs(), lessThan(0.01));
    expect(pred.label, 'hungry');
  });
}
