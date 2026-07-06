import 'dart:convert';
import 'dart:io';

import 'package:path_provider/path_provider.dart';

class HistoryEntry {
  final DateTime timestamp;
  final String label;
  final Map<String, double> probabilities;

  HistoryEntry(this.timestamp, this.label, this.probabilities);

  Map<String, dynamic> toJson() => {
        't': timestamp.toIso8601String(),
        'label': label,
        'probs': probabilities,
      };

  static HistoryEntry fromJson(Map<String, dynamic> m) => HistoryEntry(
        DateTime.parse(m['t'] as String),
        m['label'] as String,
        (m['probs'] as Map<String, dynamic>)
            .map((k, v) => MapEntry(k, (v as num).toDouble())),
      );
}

/// Storico analisi salvato come JSON nei documenti dell'app.
class HistoryStore {
  static Future<File> _file() async {
    final dir = await getApplicationDocumentsDirectory();
    return File('${dir.path}/cry_history.json');
  }

  static Future<List<HistoryEntry>> load() async {
    try {
      final f = await _file();
      if (!await f.exists()) return [];
      final list = json.decode(await f.readAsString()) as List;
      return list
          .map((e) => HistoryEntry.fromJson(e as Map<String, dynamic>))
          .toList()
        ..sort((a, b) => b.timestamp.compareTo(a.timestamp));
    } catch (_) {
      return [];
    }
  }

  static Future<void> add(HistoryEntry entry) async {
    final entries = await load();
    entries.insert(0, entry);
    final f = await _file();
    await f.writeAsString(
        json.encode(entries.map((e) => e.toJson()).toList()));
  }

  static Future<void> clear() async {
    final f = await _file();
    if (await f.exists()) await f.delete();
  }
}
