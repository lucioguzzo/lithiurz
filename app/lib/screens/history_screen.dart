import 'package:flutter/material.dart';
import 'package:intl/intl.dart';

import '../services/cry_info.dart';
import '../services/history_store.dart';

class HistoryScreen extends StatefulWidget {
  const HistoryScreen({super.key});

  @override
  State<HistoryScreen> createState() => _HistoryScreenState();
}

class _HistoryScreenState extends State<HistoryScreen> {
  List<HistoryEntry> _entries = [];
  bool _loading = true;

  @override
  void initState() {
    super.initState();
    _reload();
  }

  Future<void> _reload() async {
    final e = await HistoryStore.load();
    if (mounted) {
      setState(() {
        _entries = e;
        _loading = false;
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    if (_loading) return const Center(child: CircularProgressIndicator());
    return Column(
      children: [
        Padding(
          padding: const EdgeInsets.fromLTRB(16, 16, 8, 0),
          child: Row(
            children: [
              Text('Storico', style: Theme.of(context).textTheme.headlineSmall),
              const Spacer(),
              IconButton(onPressed: _reload, icon: const Icon(Icons.refresh)),
              if (_entries.isNotEmpty)
                IconButton(
                  icon: const Icon(Icons.delete_outline),
                  onPressed: () async {
                    final ok = await showDialog<bool>(
                      context: context,
                      builder: (c) => AlertDialog(
                        title: const Text('Cancellare lo storico?'),
                        actions: [
                          TextButton(
                              onPressed: () => Navigator.pop(c, false),
                              child: const Text('No')),
                          TextButton(
                              onPressed: () => Navigator.pop(c, true),
                              child: const Text('Sì')),
                        ],
                      ),
                    );
                    if (ok == true) {
                      await HistoryStore.clear();
                      _reload();
                    }
                  },
                ),
            ],
          ),
        ),
        if (_entries.isNotEmpty) _buildSummary(),
        Expanded(
          child: _entries.isEmpty
              ? const Center(child: Text('Nessuna analisi salvata.'))
              : ListView.builder(
                  itemCount: _entries.length,
                  itemBuilder: (context, i) {
                    final e = _entries[i];
                    final info = CryInfo.byKey(e.label);
                    final conf = e.probabilities[e.label] ?? 0;
                    return ListTile(
                      leading: CircleAvatar(
                        backgroundColor: info.color.withValues(alpha: 0.15),
                        child: Icon(info.icon, color: info.color),
                      ),
                      title: Text(info.nameIt),
                      subtitle: Text(DateFormat('EEE d MMM, HH:mm', 'it')
                          .format(e.timestamp)),
                      trailing: Text('${(conf * 100).toStringAsFixed(0)}%'),
                    );
                  },
                ),
        ),
      ],
    );
  }

  /// Riepilogo per riconoscere pattern (es. fame sempre alla stessa ora).
  Widget _buildSummary() {
    final counts = <String, int>{};
    for (final e in _entries) {
      counts[e.label] = (counts[e.label] ?? 0) + 1;
    }
    final sorted = counts.entries.toList()
      ..sort((a, b) => b.value.compareTo(a.value));
    return SizedBox(
      height: 48,
      child: ListView(
        scrollDirection: Axis.horizontal,
        padding: const EdgeInsets.symmetric(horizontal: 12),
        children: sorted.map((e) {
          final info = CryInfo.byKey(e.key);
          return Padding(
            padding: const EdgeInsets.symmetric(horizontal: 4, vertical: 6),
            child: Chip(
              avatar: Icon(info.icon, size: 18, color: info.color),
              label: Text('${info.nameIt}: ${e.value}'),
            ),
          );
        }).toList(),
      ),
    );
  }
}
