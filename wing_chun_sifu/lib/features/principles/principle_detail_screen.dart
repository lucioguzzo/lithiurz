import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../core/theme.dart';
import '../../core/widgets/common.dart';
import '../../data/content_repository.dart';
import '../../data/progress_service.dart';
import '../sifu3d/lesson_link_card.dart';
import 'principles_screen.dart';

class PrincipleDetailScreen extends StatelessWidget {
  const PrincipleDetailScreen({super.key, required this.id});

  final String id;

  @override
  Widget build(BuildContext context) {
    final principle = ContentRepository.instance.principle(id);
    final t = Theme.of(context).textTheme;
    const accent = PrinciplesScreen.accent;

    if (principle == null) {
      return const _NotFound(what: 'Principio');
    }

    final progress = context.watch<ProgressService>();
    final studied = progress.isStudied(principle.id);
    final lesson = ContentRepository.instance.lesson(principle.lessonId);

    return Scaffold(
      body: InkBackground(
        accent: accent,
        child: CustomScrollView(
          slivers: [
            SliverAppBar(
              pinned: true,
              backgroundColor: AppColors.ink,
              surfaceTintColor: Colors.transparent,
              title: Text(principle.name,
                  style: t.titleLarge, overflow: TextOverflow.ellipsis),
              actions: [
                IconButton(
                  tooltip: studied ? 'Segna da rivedere' : 'Segna come studiato',
                  onPressed: () =>
                      ProgressService.instance.toggleStudied(principle.id),
                  icon: Icon(
                    studied ? Icons.check_circle : Icons.check_circle_outline,
                    color: studied ? accent : AppColors.textFaint,
                  ),
                ),
              ],
            ),
            SliverPadding(
              padding: const EdgeInsets.fromLTRB(24, 8, 24, 40),
              sliver: SliverList.list(
                children: [
                  Reveal(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        if (principle.chinese.isNotEmpty)
                          Text(principle.chinese,
                              style: t.headlineMedium?.copyWith(
                                  color: accent, fontSize: 22, height: 1.3)),
                        if (principle.translation != null) ...[
                          const SizedBox(height: 4),
                          Text(principle.translation!,
                              style: t.bodySmall
                                  ?.copyWith(fontStyle: FontStyle.italic)),
                        ],
                        const SizedBox(height: 16),
                        Text(principle.summary,
                            style: t.bodyLarge?.copyWith(
                                color: AppColors.text,
                                fontSize: 17,
                                height: 1.55)),
                      ],
                    ),
                  ),
                  const SizedBox(height: 20),
                  const Reveal(index: 1, child: BrushDivider()),
                  const SizedBox(height: 20),
                  Reveal(index: 2, child: Paragraphs(principle.body)),
                  if (principle.keyPoints.isNotEmpty) ...[
                    const SizedBox(height: 26),
                    Reveal(
                      index: 3,
                      child: Panel(
                        accent: accent,
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            const Eyebrow('Da ricordare', color: accent),
                            const SizedBox(height: 13),
                            KeyPoints(principle.keyPoints, color: accent),
                          ],
                        ),
                      ),
                    ),
                  ],
                  if (lesson != null) ...[
                    const SizedBox(height: 22),
                    Reveal(index: 4, child: LessonLinkCard(lesson: lesson)),
                  ],
                  if (principle.source != null) ...[
                    const SizedBox(height: 22),
                    Reveal(
                      index: 5,
                      child: Row(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          const Icon(Icons.school_outlined,
                              size: 14, color: AppColors.textFaint),
                          const SizedBox(width: 8),
                          Expanded(
                              child:
                                  Text(principle.source!, style: t.bodySmall)),
                        ],
                      ),
                    ),
                  ],
                  const SizedBox(height: 30),
                  Reveal(
                    index: 6,
                    child: _StudiedButton(id: principle.id, accent: accent),
                  ),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _StudiedButton extends StatelessWidget {
  const _StudiedButton({required this.id, required this.accent});

  final String id;
  final Color accent;

  @override
  Widget build(BuildContext context) {
    final studied = context.watch<ProgressService>().isStudied(id);
    return SizedBox(
      width: double.infinity,
      child: OutlinedButton.icon(
        onPressed: () => ProgressService.instance.toggleStudied(id),
        icon: Icon(studied ? Icons.replay : Icons.check, size: 17),
        label: Text(studied ? 'Segna da rivedere' : 'Ho studiato questa voce'),
        style: OutlinedButton.styleFrom(
          foregroundColor: studied ? AppColors.textMuted : accent,
          side: BorderSide(
              color: (studied ? AppColors.line : accent).withValues(alpha: 0.6)),
          padding: const EdgeInsets.symmetric(vertical: 15),
          shape:
              RoundedRectangleBorder(borderRadius: BorderRadius.circular(999)),
        ),
      ),
    );
  }
}

class _NotFound extends StatelessWidget {
  const _NotFound({required this.what});

  final String what;

  @override
  Widget build(BuildContext context) => Scaffold(
        appBar: AppBar(),
        body: Center(
          child: Text('$what non trovato',
              style: Theme.of(context).textTheme.bodyMedium),
        ),
      );
}
