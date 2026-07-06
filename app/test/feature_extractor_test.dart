import 'dart:math' as math;

import 'package:flutter_test/flutter_test.dart';
import 'package:baby_cry_translator/services/feature_extractor.dart';

/// Verifica che l'estrazione feature in Dart produca ESATTAMENTE gli stessi
/// valori della pipeline Python usata per addestrare il modello
/// (tools/train_model.py). I valori attesi sono stati calcolati in Python
/// sullo stesso segnale sintetico deterministico.
void main() {
  test('feature Dart == feature Python (parità pipeline)', () {
    const sr = 8000;
    const n = 3 * sr;
    final x = List<double>.generate(n, (i) {
      final t = i / sr;
      return math.sin(2 * math.pi * 450 * t) *
              (0.5 + 0.5 * math.sin(2 * math.pi * 1.5 * t)) +
          0.3 * math.sin(2 * math.pi * 900 * t) +
          0.05 * math.sin(2 * math.pi * 3000 * t);
    });

    const expected = [
      -72.0502351753, 3.3287677552, 17.6778046576, 3.9341131619,
      -3.5625348341, 2.0600872749, -25.4099045412, 0.3607924099,
      -4.2319040642, 1.2706207741, 3.1685609014, 1.3389723882,
      -12.3558644322, 0.5867062845, -0.5811837120, 0.3945548485,
      -9.0555227834, 0.8077124120, 20.3038260653, 0.6105765317,
      2.4918083113, 0.1439825701, 6.2680636769, 0.4695872585,
      -9.4150207376, 0.6106419544, 0.1763969090, 0.0516378498,
      -1.0389422783, 0.4587138597, 653.2809358465, 175.2926940113,
    ];

    final features = FeatureExtractor.extract(x, sr);
    expect(features.length, expected.length);
    for (int i = 0; i < expected.length; i++) {
      final tol = math.max(expected[i].abs() * 1e-6, 1e-6);
      expect((features[i] - expected[i]).abs(), lessThan(tol),
          reason: 'feature[$i]: ${features[i]} != ${expected[i]}');
    }
  });
}
