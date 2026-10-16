import 'package:flutter/material.dart';

import '../../app_services.dart';
import '../../core/failures.dart';
import '../../core/formatters.dart';
import '../../services/transcription/model_manager.dart';
import '../theme.dart';
import '../widgets/app_footer.dart';
import '../widgets/common.dart';

/// Gestione dei modelli di trascrizione: download, selezione, eliminazione.
class ModelsScreen extends StatefulWidget {
  const ModelsScreen({super.key, this.required});

  /// Modello da scaricare per poter proseguire (se aperta dal flusso).
  final ModelOption? required;

  @override
  State<ModelsScreen> createState() => _ModelsScreenState();
}

class _ModelsScreenState extends State<ModelsScreen> {
  final Map<String, double?> _progress = <String, double?>{};
  final Map<String, bool> _installed = <String, bool>{};
  final Map<String, bool> _outdated = <String, bool>{};
  final Map<String, CancellationToken> _tokens = <String, CancellationToken>{};
  bool _tightOnMemory = false;
  int _freedLegacy = 0;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) => _refresh());
  }

  Future<void> _refresh() async {
    final AppServices s = ServicesScope.of(context);
    final Map<String, bool> installed = <String, bool>{};
    final Map<String, bool> outdated = <String, bool>{};
    for (final ModelOption o in ModelOption.all) {
      installed[o.id] = await s.models.isInstalled(o);
      outdated[o.id] = await s.models.hasOutdatedModel(o);
    }
    final bool tight = await s.models.isTightOnMemory();
    // Tiny e Base non sono più offerti: se restano da una versione
    // precedente occupano spazio per niente.
    final int freed = await s.models.deleteLegacyModels();
    if (mounted) {
      setState(() {
        _installed
          ..clear()
          ..addAll(installed);
        _outdated
          ..clear()
          ..addAll(outdated);
        _tightOnMemory = tight;
        _freedLegacy = freed;
      });
    }
  }

  Future<void> _download(ModelOption option) async {
    final AppServices s = ServicesScope.of(context);
    final CancellationToken token = CancellationToken();
    setState(() {
      _progress[option.id] = 0;
      _tokens[option.id] = token;
    });
    try {
      await s.models.download(
        option,
        token: token,
        onProgress: (ModelDownloadProgress p) {
          if (mounted) setState(() => _progress[option.id] = p.fraction);
        },
      );
      // Il rilevatore del parlato pesa circa 1 MB e riduce di molto le
      // frasi ripetute: si scarica insieme al modello.
      await s.models.downloadVad(token: token);
      if (!mounted) return;
      setState(() {
        _progress.remove(option.id);
        _installed[option.id] = true;
        _outdated[option.id] = false;
      });
      showMessage(context, '${option.label} pronto all\'uso');
      if (widget.required?.id == option.id && mounted) {
        Navigator.of(context).pop(true);
      }
    } on CancelledException {
      if (mounted) setState(() => _progress.remove(option.id));
    } on AppFailure catch (e) {
      if (!mounted) return;
      setState(() => _progress.remove(option.id));
      showMessage(context, e.message, error: true);
    } finally {
      _tokens.remove(option.id);
    }
  }

  Future<void> _delete(ModelOption option) async {
    final bool ok = await confirm(
      context,
      title: 'Eliminare il modello?',
      message: 'Libererai ${Fmt.bytes(option.bytes)}. Potrai '
          'riscaricarlo quando vuoi.',
      confirmLabel: 'Elimina',
      destructive: true,
    );
    if (!ok || !mounted) return;
    await ServicesScope.of(context).models.delete(option);
    await _refresh();
  }

  @override
  Widget build(BuildContext context) {
    final AppServices s = ServicesScope.of(context);
    final ThemeData theme = Theme.of(context);
    final String selected = s.settings.value.modelName;

    return Scaffold(
      appBar: AppBar(title: const Text('Modelli di trascrizione')),
      bottomNavigationBar: const BottomBar(showAd: false),
      body: SafeArea(
        child: ListView(
          padding: const EdgeInsets.fromLTRB(16, 16, 16, 32),
          children: <Widget>[
            if (widget.required != null)
              Card(
                color: theme.colorScheme.tertiaryContainer,
                child: Padding(
                  padding: const EdgeInsets.all(14),
                  child: Text(
                    'Per procedere serve il modello '
                    '${widget.required!.label} (${Fmt.bytes(widget.required!.bytes)}). '
                    'Si scarica una sola volta e poi funziona offline.',
                    style: TextStyle(
                      color: theme.colorScheme.onTertiaryContainer,
                    ),
                  ),
                ),
              ),
            if (widget.required != null) Gaps.h16,
            Text(
              'Il modello viene scaricato da Hugging Face e resta sul '
              'dispositivo: da quel momento la trascrizione funziona anche '
              'senza connessione.'
              '${_freedLegacy > 0 ? ' Ho liberato ${Fmt.bytes(_freedLegacy)} '
                  'eliminando i modelli non più usati.' : ''}',
              style: theme.textTheme.bodySmall?.copyWith(
                color: theme.colorScheme.onSurfaceVariant,
              ),
            ),
            Gaps.h16,
            for (final ModelOption option in ModelOption.all) ...<Widget>[
              _ModelCard(
                option: option,
                installed: _installed[option.id] ?? false,
                outdated: _outdated[option.id] ?? false,
                selected: selected == option.id,
                tightOnMemory: _tightOnMemory,
                progress: _progress[option.id],
                downloading: _progress.containsKey(option.id),
                onDownload: () => _download(option),
                onCancel: () => _tokens[option.id]?.cancel(),
                onDelete: () => _delete(option),
                onSelect: () async {
                  await s.settings.update(
                    s.settings.value.copyWith(modelName: option.id),
                  );
                  if (mounted) setState(() {});
                },
              ),
              Gaps.h12,
            ],
          ],
        ),
      ),
    );
  }
}

class _ModelCard extends StatelessWidget {
  const _ModelCard({
    required this.option,
    required this.installed,
    required this.outdated,
    required this.selected,
    required this.tightOnMemory,
    required this.progress,
    required this.downloading,
    required this.onDownload,
    required this.onCancel,
    required this.onDelete,
    required this.onSelect,
  });

  final ModelOption option;
  final bool installed;

  /// Sul telefono c'è un modello di una versione precedente: va
  /// sostituito, non affiancato.
  final bool outdated;
  final bool selected;
  final bool tightOnMemory;
  final double? progress;
  final bool downloading;
  final VoidCallback onDownload;
  final VoidCallback onCancel;
  final VoidCallback onDelete;
  final VoidCallback onSelect;

  @override
  Widget build(BuildContext context) {
    final ThemeData theme = Theme.of(context);
    return Card(
      color: selected ? theme.colorScheme.primaryContainer : null,
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: <Widget>[
            Row(
              children: <Widget>[
                Expanded(
                  child: Text(option.label,
                      style: theme.textTheme.titleMedium),
                ),
                if (tightOnMemory)
                  Chip(
                    avatar: Icon(Icons.warning_amber,
                        size: 16, color: theme.colorScheme.onErrorContainer),
                    label: const Text('memoria limitata'),
                    backgroundColor: theme.colorScheme.errorContainer,
                    visualDensity: VisualDensity.compact,
                    labelStyle: theme.textTheme.labelSmall?.copyWith(
                      color: theme.colorScheme.onErrorContainer,
                    ),
                  ),
              ],
            ),
            Gaps.h4,
            Text(
              '${Fmt.bytes(option.bytes)} · RAM consigliata '
              '${(option.ramHintMb / 1024).toStringAsFixed(1)} GB',
              style: theme.textTheme.labelSmall,
            ),
            Gaps.h8,
            Text(option.description, style: theme.textTheme.bodySmall),
            if (outdated && !downloading) ...<Widget>[
              Gaps.h8,
              Text(
                'Sul telefono c\'è la versione precedente di questo modello, '
                'più compressa e meno precisa. Scaricando quella nuova viene '
                'sostituita: lo spazio occupato non raddoppia.',
                style: theme.textTheme.bodySmall?.copyWith(
                  color: theme.colorScheme.error,
                ),
              ),
            ],
            Gaps.h12,
            if (downloading) ...<Widget>[
              LinearProgressIndicator(value: progress),
              Gaps.h8,
              Row(
                children: <Widget>[
                  Text(
                    progress == null
                        ? 'Download in corso…'
                        : 'Download ${(progress! * 100).round()}%',
                    style: theme.textTheme.labelSmall,
                  ),
                  const Spacer(),
                  TextButton(onPressed: onCancel, child: const Text('Annulla')),
                ],
              ),
            ] else
              Row(
                children: <Widget>[
                  if (!installed)
                    FilledButton.icon(
                      onPressed: onDownload,
                      style: FilledButton.styleFrom(
                        minimumSize: const Size(140, 40),
                      ),
                      icon: const Icon(Icons.download, size: 18),
                      label: const Text('Scarica'),
                    )
                  else if (!selected)
                    FilledButton(
                      onPressed: onSelect,
                      style: FilledButton.styleFrom(
                        minimumSize: const Size(140, 40),
                      ),
                      child: const Text('Usa questo'),
                    )
                  else
                    Row(
                      children: <Widget>[
                        Icon(Icons.check_circle,
                            size: 18, color: theme.colorScheme.primary),
                        Gaps.w8,
                        const Text('In uso'),
                      ],
                    ),
                  const Spacer(),
                  if (installed)
                    IconButton(
                      tooltip: 'Elimina dal dispositivo',
                      onPressed: onDelete,
                      icon: const Icon(Icons.delete_outline),
                    ),
                ],
              ),
          ],
        ),
      ),
    );
  }
}
