import 'package:flutter/material.dart';
import 'package:just_audio/just_audio.dart' hide AudioSource;

import '../../core/formatters.dart';
import '../../domain/audio_source.dart';
import '../theme.dart';

/// Anteprima ascoltabile del file prima di elaborarlo.
///
/// Alcuni formati (es. Ogg/Opus su iOS) non sono riproducibili dal lettore di
/// sistema: in quel caso l'anteprima si disattiva senza bloccare nulla, la
/// trascrizione funziona comunque perché passa da FFmpeg.
class AudioPreview extends StatefulWidget {
  const AudioPreview({super.key, required this.source});

  final AudioSource source;

  @override
  State<AudioPreview> createState() => _AudioPreviewState();
}

class _AudioPreviewState extends State<AudioPreview> {
  final AudioPlayer _player = AudioPlayer();
  bool _unsupported = false;

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    try {
      await _player.setFilePath(widget.source.path);
    } on Object {
      if (mounted) setState(() => _unsupported = true);
    }
  }

  @override
  void didUpdateWidget(AudioPreview oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.source.path != widget.source.path) {
      _unsupported = false;
      _load();
    }
  }

  @override
  void dispose() {
    _player.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final ThemeData theme = Theme.of(context);
    if (_unsupported) {
      return Row(
        children: <Widget>[
          Icon(Icons.graphic_eq, color: theme.colorScheme.outline),
          Gaps.w12,
          Expanded(
            child: Text(
              'Anteprima non disponibile per questo formato, '
              'ma la trascrizione funziona.',
              style: theme.textTheme.bodySmall,
            ),
          ),
        ],
      );
    }

    return StreamBuilder<PlayerState>(
      stream: _player.playerStateStream,
      builder: (BuildContext context, AsyncSnapshot<PlayerState> snapshot) {
        final bool playing = snapshot.data?.playing ?? false;
        final bool completed =
            snapshot.data?.processingState == ProcessingState.completed;
        return Row(
          children: <Widget>[
            IconButton.filledTonal(
              onPressed: () async {
                if (playing) {
                  await _player.pause();
                } else {
                  if (completed) await _player.seek(Duration.zero);
                  await _player.play();
                }
              },
              icon: Icon(playing ? Icons.pause : Icons.play_arrow),
              tooltip: playing ? 'Pausa' : 'Ascolta',
            ),
            Gaps.w12,
            Expanded(
              child: StreamBuilder<Duration>(
                stream: _player.positionStream,
                builder: (BuildContext context,
                    AsyncSnapshot<Duration> posSnapshot) {
                  final Duration total =
                      _player.duration ?? widget.source.duration;
                  final Duration pos = posSnapshot.data ?? Duration.zero;
                  final double value = total.inMilliseconds == 0
                      ? 0
                      : (pos.inMilliseconds / total.inMilliseconds)
                          .clamp(0.0, 1.0);
                  return Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: <Widget>[
                      LinearProgressIndicator(
                        value: value,
                        minHeight: 6,
                        borderRadius: BorderRadius.circular(3),
                      ),
                      Gaps.h4,
                      Text(
                        '${Fmt.duration(pos)} / ${Fmt.duration(total)}',
                        style: theme.textTheme.bodySmall,
                      ),
                    ],
                  );
                },
              ),
            ),
          ],
        );
      },
    );
  }
}
