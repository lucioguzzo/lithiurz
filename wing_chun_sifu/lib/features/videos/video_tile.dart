import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

import '../../core/theme.dart';
import '../../data/models.dart';

/// Anteprima di un video. La miniatura arriva da YouTube; se il dispositivo
/// e' offline resta il riquadro con il titolo, che continua a informare.
class VideoTile extends StatelessWidget {
  const VideoTile({super.key, required this.video});

  final VideoItem video;

  @override
  Widget build(BuildContext context) {
    final t = Theme.of(context).textTheme;
    return Material(
      color: Colors.transparent,
      child: InkWell(
        onTap: () => context.push('/video/${video.id}'),
        borderRadius: BorderRadius.circular(16),
        child: Container(
          clipBehavior: Clip.antiAlias,
          decoration: BoxDecoration(
            color: AppColors.surface,
            borderRadius: BorderRadius.circular(16),
            border: Border.all(color: AppColors.line),
          ),
          // IntrinsicHeight da' alla Row un'altezza definita, che e' quello
          // che serve a "stretch" per far riempire alla miniatura tutta
          // l'altezza della scheda: dentro una lista l'altezza sarebbe
          // altrimenti illimitata e il layout fallirebbe.
          child: IntrinsicHeight(
            child: Row(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                SizedBox(
                  width: 126,
                  child: Stack(
                    fit: StackFit.expand,
                    children: [
                      Image.network(
                        video.thumbnailUrl,
                        fit: BoxFit.cover,
                        errorBuilder: (_, __, ___) => const ColoredBox(
                          color: AppColors.surfaceHigh,
                          child: Icon(Icons.movie_outlined,
                              size: 20, color: AppColors.textFaint),
                        ),
                        loadingBuilder: (context, child, progress) =>
                            progress == null
                                ? child
                                : const ColoredBox(color: AppColors.surfaceHigh),
                      ),
                      Center(
                        child: Container(
                          width: 32,
                          height: 32,
                          decoration: BoxDecoration(
                            shape: BoxShape.circle,
                            color: AppColors.ink.withValues(alpha: 0.62),
                            border: Border.all(
                                color: Colors.white.withValues(alpha: 0.35)),
                          ),
                          child: const Icon(Icons.play_arrow_rounded,
                              size: 18, color: Colors.white),
                        ),
                      ),
                    ],
                  ),
                ),
                Expanded(
                  child: Padding(
                    padding: const EdgeInsets.fromLTRB(14, 13, 12, 13),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      mainAxisAlignment: MainAxisAlignment.center,
                      children: [
                        Text(video.title,
                            style: t.titleMedium,
                            maxLines: 2,
                            overflow: TextOverflow.ellipsis),
                        const SizedBox(height: 4),
                        Text(video.author,
                            style: t.bodySmall?.copyWith(
                                fontSize: 11.5, color: AppColors.cinnabar),
                            maxLines: 1,
                            overflow: TextOverflow.ellipsis),
                        if (video.note.isNotEmpty) ...[
                          const SizedBox(height: 6),
                          Text(video.note,
                              style: t.bodySmall
                                  ?.copyWith(fontSize: 11.5, height: 1.4),
                              maxLines: 2,
                              overflow: TextOverflow.ellipsis),
                        ],
                      ],
                    ),
                  ),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}
