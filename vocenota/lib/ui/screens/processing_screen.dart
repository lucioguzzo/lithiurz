import 'package:flutter/material.dart';

import '../../app_services.dart';
import '../../core/formatters.dart';
import '../../domain/audio_source.dart';
import '../../domain/note.dart';
import '../../domain/processing.dart';
import '../../services/transcription/job_store.dart';
import '../theme.dart';
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
        body: SafeArea(
          child: AnimatedBuilder(
            animation: s.processing,
            builder: (BuildContext context, _) {
              final ProcessingUpdate u = s.processing.update;
              final bool failed = u.stage == ProcessingStage.errore;
              final bool cancelled = u.stage == ProcessingStage.annullato;

              return ListView(
                padding: const EdgeInsets.all(20),
                children: <Widget>[
                  Center(
                    child: SizedBox(
                      width: 140,
                      height: 140,
                      child: Stack(
                        alignment: Alignment.center,
                        children: <Widget>[
                          SizedBox(
                            width: 140,
                            height: 140,
                            child: CircularProgressIndicator(
                              value: failed || cancelled ? 1 : u.progress,
                              strokeWidth: 10,
                              color: failed
                                  ? theme.colorScheme.error
                                  : theme.colorScheme.primary,
                              backgroundColor:
                                  theme.colorScheme.surfaceContainerHighest,
                            ),
                          ),
                          if (failed)
                            Icon(Icons.error_outline,
                                size: 48, color: theme.colorScheme.error)
                          else if (cancelled)
                            Icon(Icons.pause_circle_outline,
                                size: 48, color: theme.colorScheme.outline)
                          else
                            Text(
                              u.progress == null
                                  ? '…'
                                  : '${(u.progress! * 100).round()}%',
                              style: theme.textTheme.headlineSmall,
                            ),
                        ],
                      ),
                    ),
                  ),
                  Gaps.h24,
                  Center(
                    child: Text(u.stage.label,
                        style: theme.textTheme.titleMedium),
                  ),
                  if (u.detail != null) ...<Widget>[
                    Gaps.h8,
                    Center(
                      child: Text(
                        u.detail!,
                        textAlign: TextAlign.center,
                        style: theme.textTheme.bodySmall?.copyWith(
                          color: theme.colorScheme.onSurfaceVariant,
                        ),
                      ),
                    ),
                  ],
                  Gaps.h24,
                  SectionCard(
                    title: 'File',
                    icon: Icons.audiotrack,
                    child: Text(
                      '${widget.source.displayName}\n'
                      '${Fmt.duration(widget.source.duration)} · '
                      '${Fmt.bytes(widget.source.sizeBytes)}',
                      style: theme.textTheme.bodySmall,
                    ),
                  ),
                  if ((u.partialTranscript ?? '').isNotEmpty) ...<Widget>[
                    Gaps.h16,
                    SectionCard(
                      title: 'Testo riconosciuto finora',
                      icon: Icons.notes,
                      child: Text(
                        Fmt.preview(u.partialTranscript!, max: 600),
                        style: theme.textTheme.bodySmall,
                      ),
                    ),
                  ],
                  Gaps.h24,
                  if (failed || cancelled)
                    Column(
                      children: <Widget>[
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
                      ],
                    )
                  else
                    OutlinedButton.icon(
                      onPressed: () => s.processing.cancel(),
                      icon: const Icon(Icons.stop_circle_outlined),
                      label: const Text('Annulla elaborazione'),
                    ),
                  Gaps.h16,
                  Text(
                    'Tieni l\'app aperta: i sistemi operativi limitano il '
                    'lavoro in background e l\'elaborazione potrebbe essere '
                    'sospesa. Al rientro potrai riprendere da dove eri.',
                    textAlign: TextAlign.center,
                    style: theme.textTheme.labelSmall?.copyWith(
                      color: theme.colorScheme.onSurfaceVariant,
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
