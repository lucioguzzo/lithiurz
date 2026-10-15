import 'dart:async';

import 'package:flutter/material.dart';

import '../../app_services.dart';
import '../../core/constants.dart';
import '../../core/failures.dart';
import '../../core/formatters.dart';
import '../../domain/audio_source.dart';
import '../../domain/note.dart';
import '../../services/transcription/job_store.dart';
import '../../services/transcription/model_manager.dart';
import '../theme.dart';
import '../widgets/audio_preview.dart';
import '../widgets/app_footer.dart';
import '../widgets/common.dart';
import 'history_screen.dart';
import 'models_screen.dart';
import 'processing_screen.dart';
import 'result_screen.dart';
import 'settings_screen.dart';

class HomeScreen extends StatefulWidget {
  const HomeScreen({super.key});

  @override
  State<HomeScreen> createState() => _HomeScreenState();
}

class _HomeScreenState extends State<HomeScreen> {
  StreamSubscription<List<AudioSource>>? _sub;
  List<Note> _recent = const <Note>[];
  TranscriptionJob? _resumable;
  bool _busy = false;
  bool _started = false;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) => _bootstrap());
  }

  Future<void> _bootstrap() async {
    if (_started) return;
    _started = true;
    final AppServices s = ServicesScope.of(context);

    _sub = s.share.incoming.listen((List<AudioSource> files) {
      if (files.isNotEmpty) _onFiles(files);
    });

    final List<AudioSource> initial = await s.share.start();
    final TranscriptionJob? job = await s.transcription.jobs.load();
    if (!mounted) return;
    setState(() => _resumable = job);
    await _loadRecent();
    if (initial.isNotEmpty && mounted) await _onFiles(initial);
  }

  Future<void> _loadRecent() async {
    final AppServices s = ServicesScope.of(context);
    if (!s.settings.value.historyEnabled) {
      if (mounted) setState(() => _recent = const <Note>[]);
      return;
    }
    final List<Note> notes = await s.notes.list(limit: 5);
    if (mounted) setState(() => _recent = notes);
  }

  @override
  void dispose() {
    _sub?.cancel();
    super.dispose();
  }

  Future<void> _importManually() async {
    final AppServices s = ServicesScope.of(context);
    try {
      final List<AudioSource> files = await s.share.pickManually();
      if (files.isNotEmpty) await _onFiles(files);
    } on AppFailure catch (e) {
      if (mounted) showMessage(context, e.message, error: true);
    }
  }

  /// Valida i file ricevuti e propone l'elaborazione del primo.
  Future<void> _onFiles(List<AudioSource> files) async {
    if (_busy) return;
    setState(() => _busy = true);
    final AppServices s = ServicesScope.of(context);

    try {
      final AudioSource validated =
          await s.processing.validator.validate(files.first);
      if (!mounted) return;
      if (files.length > 1) {
        showMessage(
          context,
          'Ho ricevuto ${files.length} file: elaboro il primo, gli altri '
          'restano nella cartella di importazione.',
        );
      }
      await _confirmAndRun(validated);
    } on AppFailure catch (e) {
      if (mounted) showMessage(context, e.message, error: true);
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  Future<void> _confirmAndRun(AudioSource source) async {
    final AppServices s = ServicesScope.of(context);
    final bool? go = await showModalBottomSheet<bool>(
      context: context,
      isScrollControlled: true,
      showDragHandle: true,
      builder: (BuildContext context) => _PreviewSheet(source: source),
    );
    if (go != true || !mounted) return;

    final ModelOption model = ModelOption.byId(s.settings.value.modelName);
    if (!await s.models.isInstalled(model)) {
      if (!mounted) return;
      final bool downloaded = await Navigator.of(context).push<bool>(
            MaterialPageRoute<bool>(
              builder: (_) => ModelsScreen(required: model),
            ),
          ) ??
          false;
      if (!downloaded || !mounted) return;
    }

    await _startProcessing(source);
  }

  Future<void> _startProcessing(AudioSource source,
      {TranscriptionJob? resume}) async {
    final AppServices s = ServicesScope.of(context);
    final Note? note = await Navigator.of(context).push<Note>(
      MaterialPageRoute<Note>(
        builder: (_) => ProcessingScreen(source: source, resume: resume),
      ),
    );
    if (!mounted) return;
    setState(() => _resumable = null);
    await _loadRecent();
    if (note != null && mounted) {
      await Navigator.of(context).push(
        MaterialPageRoute<void>(builder: (_) => ResultScreen(note: note)),
      );
      if (mounted) await _loadRecent();
    }
    // Il job può essere rimasto in sospeso dopo un annullamento.
    final TranscriptionJob? pending = await s.transcription.jobs.load();
    if (mounted) setState(() => _resumable = pending);
  }

  Future<void> _resume() async {
    final TranscriptionJob? job = _resumable;
    if (job == null) return;
    final AppServices s = ServicesScope.of(context);
    try {
      final AudioSource source = await s.processing.validator.validate(
        AudioSource(
          path: job.audioPath,
          displayName: job.displayName,
          sizeBytes: 0,
          duration: Duration.zero,
          origin: AudioOrigin.condivisione,
        ),
      );
      await _startProcessing(source, resume: job);
    } on AppFailure catch (e) {
      await s.transcription.jobs.clear();
      if (mounted) {
        setState(() => _resumable = null);
        showMessage(context, e.message, error: true);
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final ThemeData theme = Theme.of(context);
    return Scaffold(
      appBar: AppBar(
        title: const FittedBox(
          fit: BoxFit.scaleDown,
          alignment: Alignment.centerLeft,
          child: Text(AppInfo.fullName),
        ),
        actions: <Widget>[
          IconButton(
            tooltip: 'Cronologia',
            icon: const Icon(Icons.history),
            onPressed: () async {
              await Navigator.of(context).push(
                MaterialPageRoute<void>(builder: (_) => const HistoryScreen()),
              );
              await _loadRecent();
            },
          ),
          IconButton(
            tooltip: 'Impostazioni',
            icon: const Icon(Icons.settings_outlined),
            onPressed: () async {
              await Navigator.of(context).push(
                MaterialPageRoute<void>(builder: (_) => const SettingsScreen()),
              );
              await _loadRecent();
            },
          ),
        ],
      ),
      bottomNavigationBar: const BottomBar(),
      body: SafeArea(
        child: RefreshIndicator(
          onRefresh: _loadRecent,
          child: ListView(
            padding: const EdgeInsets.fromLTRB(16, 8, 16, 32),
            children: <Widget>[
              if (_resumable != null) ...<Widget>[
                _ResumeCard(job: _resumable!, onResume: _resume, onDiscard: () async {
                  await ServicesScope.of(context).transcription.jobs.clear();
                  if (mounted) setState(() => _resumable = null);
                }),
                Gaps.h16,
              ],
              SectionCard(
                title: 'Come funziona',
                icon: Icons.ios_share,
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: <Widget>[
                    const _Step(
                      number: '1',
                      text: 'Apri WhatsApp, Telegram, l\'email o il file '
                          'manager e usa "Condividi" sull\'audio.',
                    ),
                    const _Step(
                      number: '2',
                      text: 'Scegli ${AppInfo.name} nell\'elenco delle app.',
                    ),
                    const _Step(
                      number: '3',
                      text: 'Ricevi trascrizione, riassunto, punti principali '
                          'e azioni da svolgere.',
                    ),
                    Gaps.h12,
                    Text(
                      'La trascrizione avviene sul telefono: l\'audio non '
                      'viene caricato su internet.',
                      style: theme.textTheme.bodySmall?.copyWith(
                        color: theme.colorScheme.onSurfaceVariant,
                      ),
                    ),
                  ],
                ),
              ),
              Gaps.h16,
              FilledButton.icon(
                onPressed: _busy ? null : _importManually,
                icon: _busy
                    ? const SizedBox(
                        width: 18,
                        height: 18,
                        child: CircularProgressIndicator(strokeWidth: 2),
                      )
                    : const Icon(Icons.folder_open),
                label: Text(_busy ? 'Controllo del file…' : 'Importa un audio'),
              ),
              Gaps.h24,
              Row(
                children: <Widget>[
                  Text('Elaborazioni recenti',
                      style: theme.textTheme.titleMedium),
                  const Spacer(),
                  if (_recent.isNotEmpty)
                    TextButton(
                      onPressed: () async {
                        await Navigator.of(context).push(
                          MaterialPageRoute<void>(
                            builder: (_) => const HistoryScreen(),
                          ),
                        );
                        await _loadRecent();
                      },
                      child: const Text('Vedi tutte'),
                    ),
                ],
              ),
              Gaps.h8,
              if (_recent.isEmpty)
                Padding(
                  padding: const EdgeInsets.symmetric(vertical: 24),
                  child: Text(
                    ServicesScope.of(context).settings.value.historyEnabled
                        ? 'Non hai ancora elaborato nessun audio.'
                        : 'La cronologia è disattivata nelle impostazioni.',
                    style: theme.textTheme.bodyMedium?.copyWith(
                      color: theme.colorScheme.onSurfaceVariant,
                    ),
                  ),
                )
              else
                ...<Widget>[
                  for (final Note note in _recent)
                    Padding(
                      padding: const EdgeInsets.only(bottom: 8),
                      child: NoteTile(
                        note: note,
                        onTap: () async {
                          await Navigator.of(context).push(
                            MaterialPageRoute<void>(
                              builder: (_) => ResultScreen(note: note),
                            ),
                          );
                          await _loadRecent();
                        },
                      ),
                    ),
                ],
            ],
          ),
        ),
      ),
    );
  }
}

class _Step extends StatelessWidget {
  const _Step({required this.number, required this.text});

  final String number;
  final String text;

  @override
  Widget build(BuildContext context) {
    final ThemeData theme = Theme.of(context);
    return Padding(
      padding: const EdgeInsets.only(bottom: 10),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: <Widget>[
          CircleAvatar(
            radius: 12,
            backgroundColor: theme.colorScheme.primaryContainer,
            child: Text(
              number,
              style: theme.textTheme.labelMedium?.copyWith(
                color: theme.colorScheme.onPrimaryContainer,
                fontWeight: FontWeight.bold,
              ),
            ),
          ),
          Gaps.w12,
          Expanded(child: Text(text, style: theme.textTheme.bodyMedium)),
        ],
      ),
    );
  }
}

class _ResumeCard extends StatelessWidget {
  const _ResumeCard({
    required this.job,
    required this.onResume,
    required this.onDiscard,
  });

  final TranscriptionJob job;
  final VoidCallback onResume;
  final Future<void> Function() onDiscard;

  @override
  Widget build(BuildContext context) {
    final ThemeData theme = Theme.of(context);
    return Card(
      color: theme.colorScheme.tertiaryContainer,
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: <Widget>[
            Row(
              children: <Widget>[
                Icon(Icons.restore, color: theme.colorScheme.onTertiaryContainer),
                Gaps.w8,
                Expanded(
                  child: Text(
                    'Elaborazione interrotta',
                    style: theme.textTheme.titleSmall?.copyWith(
                      color: theme.colorScheme.onTertiaryContainer,
                    ),
                  ),
                ),
              ],
            ),
            Gaps.h8,
            Text(
              '${job.displayName} · ${(job.fraction * 100).round()}% completato '
              '(${job.remaining} blocchi mancanti).',
              style: theme.textTheme.bodySmall?.copyWith(
                color: theme.colorScheme.onTertiaryContainer,
              ),
            ),
            Gaps.h12,
            Row(
              children: <Widget>[
                FilledButton(
                  onPressed: onResume,
                  style: FilledButton.styleFrom(
                    minimumSize: const Size(120, 40),
                  ),
                  child: const Text('Riprendi'),
                ),
                Gaps.w8,
                TextButton(
                  onPressed: onDiscard,
                  child: const Text('Scarta'),
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }
}

/// Scheda di anteprima prima di avviare l'elaborazione.
class _PreviewSheet extends StatelessWidget {
  const _PreviewSheet({required this.source});

  final AudioSource source;

  @override
  Widget build(BuildContext context) {
    final ThemeData theme = Theme.of(context);
    final AppServices s = ServicesScope.of(context);
    final ModelOption model = ModelOption.byId(s.settings.value.modelName);
    final bool cloud = s.settings.value.cloudAllowed;
    final Duration estimate = _estimate(source.duration, model.id);

    return Padding(
      padding: EdgeInsets.fromLTRB(
        20,
        0,
        20,
        20 + MediaQuery.of(context).viewInsets.bottom,
      ),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.start,
        children: <Widget>[
          Text(source.title, style: theme.textTheme.titleLarge),
          Gaps.h4,
          Text(source.subtitle,
              style: theme.textTheme.bodySmall?.copyWith(
                color: theme.colorScheme.onSurfaceVariant,
              )),
          Gaps.h16,
          AudioPreview(source: source),
          Gaps.h16,
          _InfoRow(
            icon: Icons.memory,
            text: 'Modello ${model.label} · stima ~${Fmt.duration(estimate)} '
                'di elaborazione',
          ),
          _InfoRow(
            icon: cloud ? Icons.cloud_outlined : Icons.phone_android,
            text: cloud
                ? 'Riassunto nel cloud: viene inviato solo il testo'
                : 'Riassunto locale: nessun dato esce dal telefono',
          ),
          if (source.duration > AudioLimits.chunkThreshold)
            const _InfoRow(
              icon: Icons.splitscreen,
              text: 'Audio lungo: verrà diviso in blocchi, con avanzamento e '
                  'possibilità di riprendere',
            ),
          Gaps.h16,
          FilledButton.icon(
            onPressed: () => Navigator.of(context).pop(true),
            icon: const Icon(Icons.auto_awesome),
            label: const Text('Trascrivi e riassumi'),
          ),
          TextButton(
            onPressed: () => Navigator.of(context).pop(false),
            child: const Text('Annulla'),
          ),
        ],
      ),
    );
  }

  /// Stima grossolana: su un telefono di fascia media whisper.cpp elabora
  /// più veloce del tempo reale con i modelli piccoli.
  static Duration _estimate(Duration audio, String modelId) {
    final double factor = switch (modelId) {
      'tiny' => 0.25,
      'small' => 1.1,
      _ => 0.45,
    };
    return Duration(seconds: (audio.inSeconds * factor).round().clamp(2, 99999));
  }
}

class _InfoRow extends StatelessWidget {
  const _InfoRow({required this.icon, required this.text});

  final IconData icon;
  final String text;

  @override
  Widget build(BuildContext context) {
    final ThemeData theme = Theme.of(context);
    return Padding(
      padding: const EdgeInsets.only(bottom: 8),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: <Widget>[
          Icon(icon, size: 18, color: theme.colorScheme.primary),
          Gaps.w8,
          Expanded(child: Text(text, style: theme.textTheme.bodySmall)),
        ],
      ),
    );
  }
}

/// Riga della cronologia.
class NoteTile extends StatelessWidget {
  const NoteTile({
    super.key,
    required this.note,
    required this.onTap,
    this.onDelete,
  });

  final Note note;
  final VoidCallback onTap;
  final VoidCallback? onDelete;

  @override
  Widget build(BuildContext context) {
    final ThemeData theme = Theme.of(context);
    return Card(
      child: ListTile(
        onTap: onTap,
        title: Text(note.title, maxLines: 1, overflow: TextOverflow.ellipsis),
        subtitle: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: <Widget>[
            Gaps.h4,
            Text(note.preview,
                maxLines: 2,
                overflow: TextOverflow.ellipsis,
                style: theme.textTheme.bodySmall),
            Gaps.h4,
            Row(
              children: <Widget>[
                Icon(Icons.schedule,
                    size: 13, color: theme.colorScheme.outline),
                const SizedBox(width: 4),
                Text(
                  '${Fmt.duration(note.duration)} · ${Fmt.dateTime(note.createdAt)}',
                  style: theme.textTheme.labelSmall,
                ),
                if (note.cloudUsed) ...<Widget>[
                  Gaps.w8,
                  Icon(Icons.cloud_done_outlined,
                      size: 13, color: theme.colorScheme.outline),
                ],
              ],
            ),
          ],
        ),
        trailing: onDelete == null
            ? const Icon(Icons.chevron_right)
            : IconButton(
                icon: const Icon(Icons.delete_outline),
                tooltip: 'Elimina',
                onPressed: onDelete,
              ),
        isThreeLine: true,
      ),
    );
  }
}
