import 'package:flutter/material.dart';

import '../../app_services.dart';
import '../../core/constants.dart';
import '../../core/formatters.dart';
import '../../domain/note.dart';
import '../../domain/summary.dart';
import '../../services/transcription/engine.dart';
import '../../services/transcription/transcript_quality.dart';
import '../../services/tts/tts_service.dart';
import '../theme.dart';
import '../widgets/app_footer.dart';
import '../widgets/common.dart';

/// Risultato: riassunto, punti principali, azioni e trascrizione.
class ResultScreen extends StatefulWidget {
  const ResultScreen({super.key, required this.note});

  final Note note;

  @override
  State<ResultScreen> createState() => _ResultScreenState();
}

class _ResultScreenState extends State<ResultScreen> {
  late Note _note = widget.note;
  bool _regenerating = false;
  TtsService? _tts;

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    // Il riferimento va preso qui: in `dispose` non si può più risalire
    // all'InheritedWidget.
    _tts = ServicesScope.of(context).tts;
  }

  @override
  void dispose() {
    // Non lasciare la lettura attiva uscendo dalla schermata.
    _tts?.stop();
    super.dispose();
  }

  Future<void> _regenerate(SummaryStyle style) async {
    final AppServices s = ServicesScope.of(context);
    setState(() => _regenerating = true);
    final Note? updated =
        await s.processing.regenerateSummary(_note, style: style);
    if (!mounted) return;
    setState(() {
      _regenerating = false;
      if (updated != null) _note = updated;
    });
    final String? notice = s.processing.notice;
    if (notice != null && mounted) {
      showMessage(context, 'Riassunto locale: $notice');
    }
  }

  @override
  Widget build(BuildContext context) {
    final AppServices s = ServicesScope.of(context);
    final SummaryBundle? summary = _note.summary;

    return DefaultTabController(
      length: 2,
      child: Scaffold(
        appBar: AppBar(
          title: Text(_note.title, overflow: TextOverflow.ellipsis),
          bottom: const TabBar(
            tabs: <Widget>[
              Tab(text: 'Riassunto', icon: Icon(Icons.auto_awesome, size: 18)),
              Tab(text: 'Trascrizione', icon: Icon(Icons.notes, size: 18)),
            ],
          ),
          actions: <Widget>[
            PopupMenuButton<String>(
              tooltip: 'Altre azioni',
              onSelected: (String value) async {
                switch (value) {
                  case 'txt':
                    await s.export.exportAsFile(_note);
                  case 'md':
                    await s.export.exportAsFile(_note, markdown: true);
                  case 'share_summary':
                    await s.export.shareText(
                      summary?.toPlainText(title: _note.title) ??
                          _note.transcript,
                      subject: _note.title,
                    );
                }
              },
              itemBuilder: (BuildContext context) =>
                  const <PopupMenuEntry<String>>[
                PopupMenuItem<String>(
                  value: 'share_summary',
                  child: ListTile(
                    leading: Icon(Icons.share),
                    title: Text('Condividi il riassunto'),
                  ),
                ),
                PopupMenuItem<String>(
                  value: 'txt',
                  child: ListTile(
                    leading: Icon(Icons.description_outlined),
                    title: Text('Esporta come .txt'),
                  ),
                ),
                PopupMenuItem<String>(
                  value: 'md',
                  child: ListTile(
                    leading: Icon(Icons.code),
                    title: Text('Esporta come .md'),
                  ),
                ),
              ],
            ),
          ],
        ),
        bottomNavigationBar: const BottomBar(),
        body: TabBarView(
          children: <Widget>[
            _SummaryTab(
              note: _note,
              regenerating: _regenerating,
              onRegenerate: _regenerate,
            ),
            _TranscriptTab(note: _note),
          ],
        ),
      ),
    );
  }
}

class _SummaryTab extends StatelessWidget {
  const _SummaryTab({
    required this.note,
    required this.regenerating,
    required this.onRegenerate,
  });

  final Note note;
  final bool regenerating;
  final Future<void> Function(SummaryStyle) onRegenerate;

  @override
  Widget build(BuildContext context) {
    final ThemeData theme = Theme.of(context);
    final AppServices s = ServicesScope.of(context);
    final SummaryBundle? summary = note.summary;

    if (summary == null || summary.isEmpty) {
      return EmptyState(
        icon: Icons.auto_awesome_outlined,
        title: 'Nessun riassunto disponibile',
        message: 'La trascrizione è comunque pronta nella scheda accanto.',
        action: FilledButton(
          onPressed: regenerating
              ? null
              : () => onRegenerate(
                    s.settings.value.summaryStyle,
                  ),
          child: const Text('Genera il riassunto'),
        ),
      );
    }

    return ListView(
      padding: const EdgeInsets.fromLTRB(16, 16, 16, 32),
      children: <Widget>[
        _Toolbar(note: note),
        Gaps.h16,
        if (summary.text.trim().isNotEmpty) ...<Widget>[
          SectionCard(
            title: 'Riassunto',
            icon: Icons.summarize_outlined,
            trailing: Text(
              summary.engine == SummaryEngine.cloud ? 'cloud' : 'locale',
              style: theme.textTheme.labelSmall,
            ),
            child: SelectableText(summary.text,
                style: theme.textTheme.bodyLarge),
          ),
          Gaps.h16,
        ],
        if (summary.keyPoints.isNotEmpty) ...<Widget>[
          SectionCard(
            title: 'Punti principali',
            icon: Icons.format_list_bulleted,
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: <Widget>[
                for (final String point in summary.keyPoints)
                  Padding(
                    padding: const EdgeInsets.only(bottom: 10),
                    child: Row(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: <Widget>[
                        Padding(
                          padding: const EdgeInsets.only(top: 6, right: 10),
                          child: Container(
                            width: 6,
                            height: 6,
                            decoration: BoxDecoration(
                              color: theme.colorScheme.primary,
                              shape: BoxShape.circle,
                            ),
                          ),
                        ),
                        Expanded(child: SelectableText(point)),
                      ],
                    ),
                  ),
              ],
            ),
          ),
          Gaps.h16,
        ],
        if (summary.actions.isNotEmpty) ...<Widget>[
          SectionCard(
            title: 'Azioni da svolgere',
            icon: Icons.checklist,
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: <Widget>[
                for (final ActionItem action in summary.actions)
                  Padding(
                    padding: const EdgeInsets.only(bottom: 10),
                    child: Row(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: <Widget>[
                        Icon(Icons.check_box_outline_blank,
                            size: 18, color: theme.colorScheme.primary),
                        Gaps.w8,
                        Expanded(child: SelectableText(action.display)),
                      ],
                    ),
                  ),
              ],
            ),
          ),
          Gaps.h16,
        ],
        SectionCard(
          title: 'Cambia tipo di riassunto',
          icon: Icons.tune,
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: <Widget>[
              Wrap(
                spacing: 8,
                children: <Widget>[
                  for (final SummaryStyle style in SummaryStyle.values)
                    ChoiceChip(
                      label: Text(style.label),
                      selected: summary.style == style,
                      onSelected: regenerating
                          ? null
                          : (_) => onRegenerate(style),
                    ),
                ],
              ),
              if (regenerating) ...<Widget>[
                Gaps.h12,
                const LinearProgressIndicator(),
              ],
            ],
          ),
        ),
        Gaps.h16,
        Text(
          'Elaborato il ${Fmt.dateTime(note.createdAt)} · modello '
          '${note.modelName} · ${note.cloudUsed ? 'riassunto nel cloud' : 'tutto in locale'}',
          style: theme.textTheme.labelSmall?.copyWith(
            color: theme.colorScheme.onSurfaceVariant,
          ),
        ),
      ],
    );
  }
}

/// Pulsanti ascolta / copia / condividi.
class _Toolbar extends StatelessWidget {
  const _Toolbar({required this.note});

  final Note note;

  @override
  Widget build(BuildContext context) {
    final AppServices s = ServicesScope.of(context);
    final SummaryBundle? summary = note.summary;

    return AnimatedBuilder(
      animation: s.tts,
      builder: (BuildContext context, _) {
        final bool speaking = s.tts.isSpeaking;
        return Row(
          children: <Widget>[
            Expanded(
              child: FilledButton.tonalIcon(
                onPressed: () async {
                  if (speaking) {
                    await s.tts.stop();
                    return;
                  }
                  final String text = summary?.toSpeech() ?? note.transcript;
                  if (text.trim().isEmpty) return;
                  await s.tts.apply(
                    rate: s.settings.value.ttsRate,
                    pitch: s.settings.value.ttsPitch,
                  );
                  await s.tts.speak(text);
                },
                icon: Icon(speaking ? Icons.stop : Icons.volume_up),
                label: Text(speaking ? 'Ferma' : 'Ascolta'),
              ),
            ),
            Gaps.w8,
            IconButton.filledTonal(
              tooltip: 'Copia',
              onPressed: () async {
                await s.export.copy(
                  summary?.toPlainText(title: note.title) ?? note.transcript,
                );
                if (context.mounted) {
                  showMessage(context, 'Riassunto copiato negli appunti');
                }
              },
              icon: const Icon(Icons.copy_all_outlined),
            ),
            Gaps.w8,
            IconButton.filledTonal(
              tooltip: 'Condividi',
              onPressed: () => s.export.shareText(
                summary?.toPlainText(title: note.title) ?? note.transcript,
                subject: note.title,
              ),
              icon: const Icon(Icons.share),
            ),
          ],
        );
      },
    );
  }
}

class _TranscriptTab extends StatelessWidget {
  const _TranscriptTab({required this.note});

  final Note note;

  @override
  Widget build(BuildContext context) {
    final ThemeData theme = Theme.of(context);
    final AppServices s = ServicesScope.of(context);

    return ListView(
      padding: const EdgeInsets.fromLTRB(16, 16, 16, 32),
      children: <Widget>[
        Row(
          children: <Widget>[
            Expanded(
              child: Text(
                '${TranscriptQuality.wordCount(note.transcript)} parole · '
                '${Fmt.duration(note.duration)}',
                style: theme.textTheme.bodySmall,
              ),
            ),
            IconButton(
              tooltip: 'Copia la trascrizione',
              onPressed: () async {
                await s.export.copy(note.transcript);
                if (context.mounted) {
                  showMessage(context, 'Trascrizione copiata');
                }
              },
              icon: const Icon(Icons.copy_all_outlined),
            ),
            IconButton(
              tooltip: 'Condividi la trascrizione',
              onPressed: () => s.export.shareText(
                note.transcript,
                subject: note.title,
              ),
              icon: const Icon(Icons.share),
            ),
          ],
        ),
        if (TranscriptQuality.looksTruncated(
            text: note.transcript, audio: note.duration)) ...<Widget>[
          Gaps.h8,
          _TruncatedNotice(engine: note.engine),
        ],
        Gaps.h8,
        SelectableText(
          note.transcript.isEmpty
              ? 'Nessun testo riconosciuto.'
              : note.transcript,
          style: theme.textTheme.bodyLarge?.copyWith(height: 1.5),
        ),
        Gaps.h24,
        Text(
          switch (note.engine) {
            TranscriptionEngineId.sistema =>
              'Trascrizione eseguita sul dispositivo con il riconoscitore '
                  'vocale del telefono.',
            TranscriptionEngineId.whisper =>
              'Trascrizione eseguita sul dispositivo con ${AppInfo.fullName} '
                  '(whisper.cpp, modello ${note.modelName} compresso).',
            TranscriptionEngineId.cloud =>
              'Trascrizione eseguita nel cloud: l\'audio è stato inviato al '
                  'server di ${AppInfo.name} e non vi è stato conservato.',
          },
          style: theme.textTheme.labelSmall?.copyWith(
            color: theme.colorScheme.onSurfaceVariant,
          ),
        ),
      ],
    );
  }
}

/// Avviso mostrato quando il testo è troppo corto per la durata
/// dell'audio: quasi sempre vuol dire che il motore si è fermato prima
/// della fine.
class _TruncatedNotice extends StatelessWidget {
  const _TruncatedNotice({required this.engine});

  final TranscriptionEngineId engine;

  @override
  Widget build(BuildContext context) {
    final ThemeData theme = Theme.of(context);
    return Card(
      margin: EdgeInsets.zero,
      color: theme.colorScheme.errorContainer,
      child: Padding(
        padding: const EdgeInsets.all(12),
        child: Row(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: <Widget>[
            Icon(Icons.warning_amber_outlined,
                size: 18, color: theme.colorScheme.onErrorContainer),
            Gaps.w8,
            Expanded(
              child: Text(
                engine == TranscriptionEngineId.sistema
                    ? 'Il testo è molto più corto della durata dell\'audio: '
                        'il motore del telefono si ferma spesso dopo i primi '
                        'secondi. Disattivalo nelle impostazioni e ripeti la '
                        'trascrizione con Whisper.'
                    : 'Il testo è molto più corto della durata dell\'audio. '
                        'Può succedere con registrazioni in gran parte '
                        'silenziose o con una voce poco udibile: controlla il '
                        'risultato prima di usarlo.',
                style: theme.textTheme.bodySmall?.copyWith(
                  color: theme.colorScheme.onErrorContainer,
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}
