import 'dart:convert';
import 'dart:math' as math;

import 'package:flutter/services.dart' show rootBundle;

/// Risultato della classificazione.
class CryPrediction {
  final String label; // chiave classe (es. 'hungry')
  final Map<String, double> probabilities; // tutte le classi
  CryPrediction(this.label, this.probabilities);
}

/// MLP addestrato sul donateacry-corpus (pesi in assets/model.json).
class CryClassifier {
  late List<String> classes;
  late List<double> _mean;
  late List<double> _std;
  late List<List<List<double>>> _weights; // per layer: [out][in]
  late List<List<double>> _biases;
  bool _loaded = false;

  Future<void> load() async {
    if (_loaded) return;
    final raw = await rootBundle.loadString('assets/model.json');
    loadFromJson(raw);
  }

  void loadFromJson(String raw) {
    final m = json.decode(raw) as Map<String, dynamic>;
    classes = (m['classes'] as List).cast<String>();
    _mean = (m['feature_mean'] as List).map((e) => (e as num).toDouble()).toList();
    _std = (m['feature_std'] as List).map((e) => (e as num).toDouble()).toList();
    _weights = (m['weights'] as List)
        .map((layer) => (layer as List)
            .map((row) =>
                (row as List).map((e) => (e as num).toDouble()).toList())
            .toList())
        .toList();
    _biases = (m['biases'] as List)
        .map((b) => (b as List).map((e) => (e as num).toDouble()).toList())
        .toList();
    _loaded = true;
  }

  CryPrediction predict(List<double> features) {
    assert(_loaded, 'Modello non caricato');
    var h = List<double>.generate(
        features.length, (i) => (features[i] - _mean[i]) / _std[i]);
    for (int l = 0; l < _weights.length; l++) {
      final w = _weights[l];
      final b = _biases[l];
      final out = List<double>.filled(w.length, 0);
      for (int o = 0; o < w.length; o++) {
        double s = b[o];
        for (int i = 0; i < h.length; i++) {
          s += w[o][i] * h[i];
        }
        out[o] = s;
      }
      if (l < _weights.length - 1) {
        for (int o = 0; o < out.length; o++) {
          out[o] = _tanh(out[o]);
        }
      }
      h = out;
    }
    // softmax
    final maxV = h.reduce(math.max);
    final exps = h.map((v) => math.exp(v - maxV)).toList();
    final sum = exps.reduce((a, b) => a + b);
    final probs = <String, double>{};
    for (int i = 0; i < classes.length; i++) {
      probs[classes[i]] = exps[i] / sum;
    }
    final best = probs.entries.reduce((a, b) => a.value >= b.value ? a : b);
    return CryPrediction(best.key, probs);
  }

  static double _tanh(double x) {
    if (x > 20) return 1;
    if (x < -20) return -1;
    final e2 = math.exp(2 * x);
    return (e2 - 1) / (e2 + 1);
  }
}
