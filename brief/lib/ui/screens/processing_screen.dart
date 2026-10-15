import 'package:flutter/material.dart';

import '../../app_services.dart';
import '../../core/formatters.dart';
import '../../domain/audio_source.dart';
import '../../domain/note.dart';
import '../../domain/processing.dart';
import '../../services/transcription/job_store.dart';
import '../theme.dart';
import '../widgets/app_footer.dart';
import '../widgets/common.dart';

/// Schermata di avanzamento. Nessuna pubblicità durante l'elaborazione.
class ProcessingScreen extends StatefulWidget {
  const ProcessingScreen({super.key, required this.source, this.resume});

  final AudioSource source;
  final TranscriptionJob? resume;

  @override
  State<ProcessingScreen> createState() => _ProcessingScreenState();
}

class _ProcessingScreenState extends State<ProcessingScreen> {
  bool _started = false;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) => _start());
  }

  Future<void> _start() async {
    if (_started) return;
    _started = true;
    final AppServices s = ServicesScope.of(context);
    s.processing.reset();

    // Dopo un annullamento o un errore il lavoro parziale è su disco: va
    // ripreso, altrimenti "Riprendi" ricomincerebbe da zero.
    TranscriptionJob? resume = widget.resume;
    final TranscriptionJob? saved = await s.transcription.jobs.load();
    if (saved != null && saved.audioPath == widget.source.path) {
      resume = saved;
    }
    if (!mounted) return;

    final Note? note = await s.processing.run(widget.source, resume: resume);
    if (!mounted) return;
    if (note != null) {
      Navigator.of(context).pop(note);
    }
  }

  @override
  Widget build(BuildContext context) {
    final AppServices s = ServicesScope.of(context);
    final ThemeData theme = Theme.of(context);

    return PopScope(
      canPop: false,
      onPopInvokedWithResult: (bool didPop, Object? result) async {
        if (didPop) return;
        final bool leave = s.processing.isRunning
            ? await confirm(
                context,
                title: 'Interrompere?',
                message: 'L\'elaborazione verrà annullata. I blocchi già '
                    'trascritti restano salvati e potrai riprendere.',
                confirmLabel: 'Interrompi',
                destructive: true,
              )
            : true;
        if (!leave || !context.mounted) return;
        s.processing.cancel();
        Navigator.of(context).pop();
      },
      child: Scaffold(
        appBar: AppBar(title: Text(widget.source.title)),
        // Nessuna pubblicità mentre l'app sta elaborando.
        bottomNavigationBar: const BottomBar(showAd: false),
        body: SafeArea(
          child: AnimatedBuilder(
            animation: s.processing,
            builder: (BuildContext context, _) {
              final ProcessingUpdate u = s.processing.update;
              final bool failed = u.stage == ProcessingStage.errore;
              final bool cancelled = u.stage == ProcessingStage.annullato;

              return Column(
                children: <Widget>[
                  // Intestazione fissa: fase, barra di avanzamento e file.
                  Padding(
                    padding: const EdgeInsets.fromLTRB(20, 20, 20, 12),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: <Widget>[
                        Row(
                          children: <Widget>[
                            if (failed)
                              Icon(Icons.error_outline,
                                  color: theme.colorScheme.error)
                            else if (cancelled)
                              Icon(Icons.pause_circle_outline,
                                  color: theme.colorScheme.outline)
                            else
                              SizedBox(
                                width: 18,
                                height: 18,
                                child: CircularProgressIndicator(
                                  strokeWidth: 2.5,
                                  color: theme.colorScheme.primary,
                                ),
                              ),
                            Gaps.w12,
                            Expanded(
                              child: Text(
                                u.stage.label,
                                style: theme.textTheme.titleMedium,
                              ),
                            ),
                            if (!failed && !cancelled && u.progress != null)
                              Text(
                                '${(u.progress! * 100).round()}%',
                                style: theme.textTheme.titleMedium?.copyWith(
                                  color: theme.colorScheme.primary,
                                  fontWeight: FontWeight.w700,
                                ),
                              ),
                          ],
                        ),
                        Gaps.h12,
                        ClipRRect(
                          borderRadius: BorderRadius.circular(6),
                          child: LinearProgressIndicator(
                            // Indeterminata finché non c'è una percentuale
                            // (analisi del file, riassunto).
                            value: failed || cancelled ? 1 : u.progress,
                            minHeight: 10,
                            color: failed
                                ? theme.colorScheme.error
                                : theme.colorScheme.primary,
                            backgroundColor:
                                theme.colorScheme.surfaceContainerHighest,
                          ),
                        ),
                        if (u.detail != null) ...<Widget>[
                          Gaps.h8,
                          Text(
                            u.detail!,
                            style: theme.textTheme.bodySmall?.copyWith(
                              color: theme.colorScheme.onSurfaceVariant,
                            ),
                          ),
                        ],
                        Gaps.h8,
                        Text(
                          '${widget.source.displayName} · '
                          '${Fmt.duration(widget.source.duration)} · '
                          '${Fmt.bytes(widget.source.sizeBytes)}',
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                          style: theme.textTheme.labelSmall,
                        ),
                      ],
                    ),
                  ),

                  // La trascrizione occupa il resto dello schermo e scorre
                  // da sola: è la parte che l'utente guarda mentre aspetta.
                  Expanded(
                    child: _LiveTranscript(
                      text: u.partialTranscript ?? '',
                      waiting: !failed &&
                          !cancelled &&
                          (u.partialTranscript ?? '').isEmpty,
                      title: u.stage == ProcessingStage.trascrizione
                          ? 'Trascrizione in corso'
                          : 'Trascrizione',
                    ),
                  ),

                  Padding(
                    padding: const EdgeInsets.fromLTRB(20, 12, 20, 16),
                    child: Column(
                      children: <Widget>[
                        if (failed || cancelled) ...<Widget>[
                          FilledButton.icon(
                            onPressed: () {
                              _started = false;
                              _start();
                            },
                            icon: const Icon(Icons.refresh),
                            label: Text(cancelled ? 'Riprendi' : 'Riprova'),
                          ),
                          Gaps.h8,
                          OutlinedButton(
                            onPressed: () => Navigator.of(context).pop(),
                            child: const Text('Torna alla schermata iniziale'),
                          ),
                        ] else
                          OutlinedButton.icon(
                            onPressed: () => s.processing.cancel(),
                            icon: const Icon(Icons.stop_circle_outlined),
                            label: const Text('Annulla elaborazione'),
                          ),
                        Gaps.h8,
                        Text(
                          'Tieni l\'app aperta: i sistemi operativi limitano '
                          'il lavoro in background. Se l\'elaborazione viene '
                          'sospesa, al rientro riprendi da dove eri.',
                          textAlign: TextAlign.center,
                          style: theme.textTheme.labelSmall?.copyWith(
                            color: theme.colorScheme.onSurfaceVariant,
                          ),
                        ),
                      ],
                    ),
                  ),
                ],
              );
            },
          ),
        ),
      ),
    );
  }
}

/// Mostra la trascrizione mentre viene prodotta, blocco dopo blocco,
/// scorrendo da sola verso il basso come farebbe una persona che legge.
class _LiveTranscript extends StatefulWidget {
  const _LiveTranscript({
    required this.text,
    required this.waiting,
    required this.title,
  });

  final String text;
  final String title;

  /// Nessun testo ancora: il primo blocco è in elaborazione.
  final bool waiting;

  @override
  State<_LiveTranscript> createState() => _LiveTranscriptState();
}

class _LiveTranscriptState extends State<_LiveTranscript> {
  final ScrollController _scroll = ScrollController();

  @override
  void didUpdateWidget(_LiveTranscript oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (widget.text.length > oldWidget.text.length) _scrollToEnd();
  }

  /// Segue il testo nuovo, ma solo se l'utente non sta già leggendo più in
  /// alto: in quel caso lo si lascia dov'è.
  void _scrollToEnd() {
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (!_scroll.hasClients) return;
      final double max = _scroll.position.maxScrollExtent;
      if (max - _scroll.offset > 220) return;
      _scroll.animateTo(
        max,
        duration: const Duration(milliseconds: 250),
        curve: Curves.easeOut,
      );
    });
  }

  @override
  void dispose() {
    _scroll.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final ThemeData theme = Theme.of(context);

    if (widget.text.isEmpty) {
      return Center(
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: 32),
          child: Text(
            widget.waiting
                ? 'Le prime parole compariranno qui fra poco.'
                : 'Nessun testo riconosciuto.',
            textAlign: TextAlign.center,
            style: theme.textTheme.bodyMedium?.copyWith(
              color: theme.colorScheme.onSurfaceVariant,
            ),
          ),
        ),
      );
    }

    return Container(
      width: double.infinity,
      margin: const EdgeInsets.symmetric(horizontal: 16),
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: theme.colorScheme.surfaceContainerLow,
        borderRadius: BorderRadius.circular(16),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: <Widget>[
          Row(
            children: <Widget>[
              Icon(Icons.notes, size: 18, color: theme.colorScheme.primary),
              Gaps.w8,
              Text(widget.title, style: theme.textTheme.titleSmall),
            ],
          ),
          Gaps.h12,
          Expanded(
            child: SingleChildScrollView(
              controller: _scroll,
              child: SelectableText(
                widget.text,
                style: theme.textTheme.bodyMedium?.copyWith(height: 1.5),
              ),
            ),
          ),
        ],
      ),
    );
  }
}
