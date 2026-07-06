import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart' show rootBundle;

import '../services/cry_info.dart';

class InfoScreen extends StatefulWidget {
  const InfoScreen({super.key});

  @override
  State<InfoScreen> createState() => _InfoScreenState();
}

class _InfoScreenState extends State<InfoScreen> {
  Map<String, dynamic>? _metrics;
  Map<String, dynamic>? _samples;

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    final raw = await rootBundle.loadString('assets/model.json');
    final m = json.decode(raw) as Map<String, dynamic>;
    setState(() {
      _metrics = m['metrics'] as Map<String, dynamic>?;
      _samples = (m['metrics']?['n_samples']) as Map<String, dynamic>?;
    });
  }

  @override
  Widget build(BuildContext context) {
    final recall = _metrics?['per_class_recall'] as Map<String, dynamic>?;
    return ListView(
      padding: const EdgeInsets.all(16),
      children: [
        Text('Come funziona', style: Theme.of(context).textTheme.headlineSmall),
        const SizedBox(height: 8),
        const Text(
          'L\'app registra 7 secondi di pianto, ne estrae le caratteristiche '
          'acustiche (coefficienti MFCC, energia, frequenza) e le confronta con '
          'un modello addestrato sul donateacry-corpus, un dataset pubblico di '
          '457 registrazioni di pianti reali etichettate dai genitori.\n\n'
          'L\'analisi avviene interamente sul telefono: nessun audio viene '
          'inviato online.',
        ),
        const SizedBox(height: 16),
        Text('Il dataset', style: Theme.of(context).textTheme.titleLarge),
        const SizedBox(height: 8),
        const Text(
            'donateacry-corpus (github.com/gveres/donateacry-corpus), raccolto '
            'con la campagna Donate-a-cry: registrazioni di bambini 0-2 anni '
            'i cui genitori hanno indicato la causa del pianto.'),
        if (_samples != null) ...[
          const SizedBox(height: 8),
          ...CryInfo.all.entries.map((e) => Padding(
                padding: const EdgeInsets.symmetric(vertical: 2),
                child: Row(children: [
                  Icon(e.value.icon, size: 18, color: e.value.color),
                  const SizedBox(width: 8),
                  Text('${e.value.nameIt}: ${_samples![e.key] ?? '-'} registrazioni'),
                ]),
              )),
        ],
        const SizedBox(height: 16),
        Text('Affidabilità (numeri veri)',
            style: Theme.of(context).textTheme.titleLarge),
        const SizedBox(height: 8),
        if (_metrics != null) ...[
          Text(
            'Accuratezza in validazione incrociata: '
            '${((_metrics!['accuracy'] as num) * 100).toStringAsFixed(0)}% — '
            'accuratezza bilanciata tra le classi: '
            '${((_metrics!['balanced_accuracy'] as num) * 100).toStringAsFixed(0)}%.',
          ),
          const SizedBox(height: 8),
          if (recall != null)
            ...recall.entries.map((e) {
              final info = CryInfo.byKey(e.key);
              return Padding(
                padding: const EdgeInsets.symmetric(vertical: 2),
                child: Row(children: [
                  SizedBox(width: 140, child: Text(info.nameIt)),
                  Expanded(
                    child: LinearProgressIndicator(
                      value: (e.value as num).toDouble(),
                      minHeight: 6,
                      color: info.color,
                      backgroundColor: Colors.grey.shade200,
                    ),
                  ),
                  SizedBox(
                      width: 44,
                      child: Text(
                          ' ${((e.value as num) * 100).toStringAsFixed(0)}%',
                          textAlign: TextAlign.right)),
                ]),
              );
            }),
          const SizedBox(height: 8),
          const Text(
            'La ricerca scientifica su questo problema ottiene risultati simili: '
            'distinguere le cause del pianto dal solo audio è difficile, e il '
            'dataset contiene soprattutto pianti da fame. Usa il risultato come '
            'spunto, insieme all\'orario dell\'ultima poppata, al pannolino e '
            'ai segnali del bambino.',
            style: TextStyle(fontStyle: FontStyle.italic),
          ),
        ] else
          const Text('Caricamento metriche...'),
        const SizedBox(height: 16),
        Card(
          color: Colors.amber.shade50,
          child: const Padding(
            padding: EdgeInsets.all(12),
            child: Text(
              '⚠️ Questa app non è un dispositivo medico e non sostituisce il '
              'pediatra. In caso di pianto inconsolabile, febbre, vomito o '
              'qualsiasi dubbio sulla salute del bambino, rivolgiti al medico.',
            ),
          ),
        ),
        const SizedBox(height: 24),
      ],
    );
  }
}
