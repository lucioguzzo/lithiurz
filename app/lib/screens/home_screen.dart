import 'dart:async';
import 'dart:io';

import 'package:flutter/material.dart';
import 'package:path_provider/path_provider.dart';
import 'package:record/record.dart';

import '../services/classifier.dart';
import '../services/cry_info.dart';
import '../services/feature_extractor.dart';
import '../services/history_store.dart';

class HomeScreen extends StatefulWidget {
  const HomeScreen({super.key});

  @override
  State<HomeScreen> createState() => _HomeScreenState();
}

enum _Phase { idle, recording, analyzing, result, error }

class _HomeScreenState extends State<HomeScreen>
    with SingleTickerProviderStateMixin {
  static const recordSeconds = 7;

  final _recorder = AudioRecorder();
  final _classifier = CryClassifier();
  _Phase _phase = _Phase.idle;
  CryPrediction? _prediction;
  String _error = '';
  int _countdown = recordSeconds;
  Timer? _timer;
  late final AnimationController _pulse;

  @override
  void initState() {
    super.initState();
    _pulse = AnimationController(
        vsync: this, duration: const Duration(milliseconds: 900))
      ..repeat(reverse: true);
    _classifier.load();
  }

  @override
  void dispose() {
    _timer?.cancel();
    _pulse.dispose();
    _recorder.dispose();
    super.dispose();
  }

  Future<void> _start() async {
    if (!await _recorder.hasPermission()) {
      setState(() {
        _phase = _Phase.error;
        _error = 'Permesso microfono negato. Abilitalo nelle impostazioni.';
      });
      return;
    }
    final dir = await getTemporaryDirectory();
    final path = '${dir.path}/cry_recording.wav';
    await _recorder.start(
      const RecordConfig(
        encoder: AudioEncoder.wav,
        sampleRate: 8000,
        numChannels: 1,
      ),
      path: path,
    );
    setState(() {
      _phase = _Phase.recording;
      _countdown = recordSeconds;
    });
    _timer = Timer.periodic(const Duration(seconds: 1), (t) {
      if (_countdown <= 1) {
        t.cancel();
        _stopAndAnalyze();
      } else {
        setState(() => _countdown--);
      }
    });
  }

  Future<void> _stopAndAnalyze() async {
    _timer?.cancel();
    final path = await _recorder.stop();
    setState(() => _phase = _Phase.analyzing);
    try {
      if (path == null) throw Exception('Registrazione fallita');
      final bytes = await File(path).readAsBytes();
      final (samples, sr) = FeatureExtractor.decodeWav(bytes);
      // controllo volume minimo
      final maxAbs =
          samples.fold<double>(0, (m, v) => v.abs() > m ? v.abs() : m);
      if (maxAbs < 0.01) {
        throw Exception(
            'Audio troppo debole: avvicina il telefono al bambino e riprova.');
      }
      final features = FeatureExtractor.extract(samples, sr);
      await _classifier.load();
      final pred = _classifier.predict(features);
      await HistoryStore.add(
          HistoryEntry(DateTime.now(), pred.label, pred.probabilities));
      setState(() {
        _prediction = pred;
        _phase = _Phase.result;
      });
    } catch (e) {
      setState(() {
        _phase = _Phase.error;
        _error = e.toString().replaceFirst('Exception: ', '');
      });
    }
  }

  Future<void> _cancel() async {
    _timer?.cancel();
    await _recorder.stop();
    setState(() => _phase = _Phase.idle);
  }

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.all(24),
      child: switch (_phase) {
        _Phase.idle => _buildIdle(),
        _Phase.recording => _buildRecording(),
        _Phase.analyzing => const Center(
            child: Column(mainAxisSize: MainAxisSize.min, children: [
            CircularProgressIndicator(),
            SizedBox(height: 16),
            Text('Analisi del pianto in corso...'),
          ])),
        _Phase.result => _buildResult(),
        _Phase.error => _buildError(),
      },
    );
  }

  Widget _buildIdle() {
    return Center(
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          Text('Perché piange?',
              style: Theme.of(context).textTheme.headlineMedium),
          const SizedBox(height: 8),
          const Text(
            'Avvicina il telefono al bambino mentre piange\ne premi il pulsante.',
            textAlign: TextAlign.center,
          ),
          const SizedBox(height: 40),
          GestureDetector(
            onTap: _start,
            child: Container(
              width: 140,
              height: 140,
              decoration: BoxDecoration(
                shape: BoxShape.circle,
                color: Theme.of(context).colorScheme.primary,
                boxShadow: [
                  BoxShadow(
                    color: Theme.of(context)
                        .colorScheme
                        .primary
                        .withValues(alpha: 0.4),
                    blurRadius: 24,
                    spreadRadius: 4,
                  ),
                ],
              ),
              child: const Icon(Icons.mic, size: 64, color: Colors.white),
            ),
          ),
          const SizedBox(height: 40),
          Text(
            'Ascolta per $recordSeconds secondi e confronta il pianto\ncon il dataset donateacry-corpus.',
            textAlign: TextAlign.center,
            style: Theme.of(context).textTheme.bodySmall,
          ),
        ],
      ),
    );
  }

  Widget _buildRecording() {
    return Center(
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          ScaleTransition(
            scale: Tween(begin: 0.9, end: 1.1).animate(
                CurvedAnimation(parent: _pulse, curve: Curves.easeInOut)),
            child: Container(
              width: 140,
              height: 140,
              decoration: const BoxDecoration(
                shape: BoxShape.circle,
                color: Colors.redAccent,
              ),
              child: const Icon(Icons.graphic_eq, size: 64, color: Colors.white),
            ),
          ),
          const SizedBox(height: 32),
          Text('Sto ascoltando... $_countdown',
              style: Theme.of(context).textTheme.headlineSmall),
          const SizedBox(height: 24),
          TextButton(onPressed: _cancel, child: const Text('Annulla')),
        ],
      ),
    );
  }

  Widget _buildResult() {
    final pred = _prediction!;
    final info = CryInfo.byKey(pred.label);
    final sorted = pred.probabilities.entries.toList()
      ..sort((a, b) => b.value.compareTo(a.value));
    return SingleChildScrollView(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Card(
            color: info.color.withValues(alpha: 0.12),
            child: Padding(
              padding: const EdgeInsets.all(20),
              child: Column(
                children: [
                  Icon(info.icon, size: 56, color: info.color),
                  const SizedBox(height: 8),
                  Text(info.nameIt,
                      style: Theme.of(context)
                          .textTheme
                          .headlineMedium
                          ?.copyWith(color: info.color)),
                  Text(
                      'compatibilità ${(sorted.first.value * 100).toStringAsFixed(0)}%'),
                  const SizedBox(height: 12),
                  Text(info.description, textAlign: TextAlign.center),
                ],
              ),
            ),
          ),
          const SizedBox(height: 12),
          ...sorted.map((e) {
            final i = CryInfo.byKey(e.key);
            return Padding(
              padding: const EdgeInsets.symmetric(vertical: 3),
              child: Row(
                children: [
                  SizedBox(
                      width: 130,
                      child: Text(i.nameIt,
                          style: const TextStyle(fontSize: 13))),
                  Expanded(
                    child: LinearProgressIndicator(
                      value: e.value,
                      minHeight: 8,
                      borderRadius: BorderRadius.circular(4),
                      color: i.color,
                      backgroundColor: Colors.grey.shade200,
                    ),
                  ),
                  SizedBox(
                      width: 44,
                      child: Text(' ${(e.value * 100).toStringAsFixed(0)}%',
                          textAlign: TextAlign.right,
                          style: const TextStyle(fontSize: 13))),
                ],
              ),
            );
          }),
          const SizedBox(height: 16),
          Text('Cosa provare',
              style: Theme.of(context).textTheme.titleMedium),
          const SizedBox(height: 8),
          ...info.tips.map((t) => Padding(
                padding: const EdgeInsets.symmetric(vertical: 4),
                child: Row(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Icon(Icons.check_circle_outline,
                        size: 20, color: info.color),
                    const SizedBox(width: 8),
                    Expanded(child: Text(t)),
                  ],
                ),
              )),
          const SizedBox(height: 20),
          FilledButton.icon(
            onPressed: () => setState(() => _phase = _Phase.idle),
            icon: const Icon(Icons.mic),
            label: const Text('Nuova analisi'),
          ),
          const SizedBox(height: 8),
          Text(
            'Suggerimento indicativo basato su confronto statistico, '
            'non è un parere medico.',
            textAlign: TextAlign.center,
            style: Theme.of(context).textTheme.bodySmall,
          ),
        ],
      ),
    );
  }

  Widget _buildError() {
    return Center(
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          const Icon(Icons.error_outline, size: 56, color: Colors.redAccent),
          const SizedBox(height: 12),
          Text(_error, textAlign: TextAlign.center),
          const SizedBox(height: 20),
          FilledButton(
            onPressed: () => setState(() => _phase = _Phase.idle),
            child: const Text('Riprova'),
          ),
        ],
      ),
    );
  }
}
