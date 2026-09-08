import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import 'package:provider/provider.dart';

import '../../core/theme.dart';
import '../../core/widgets/common.dart';
import '../../data/content_repository.dart';
import '../../data/models.dart';
import '../../data/progress_service.dart';

/// Principi e tecniche di base, organizzati in gruppi.
class PrinciplesScreen extends StatelessWidget {
  const PrinciplesScreen({super.key});

  static const accent = Color(0xFF4E9B87);

  @override
  Widget build(BuildContext context) {
    final repo = ContentRepository.instance;
    final progress = context.watch<ProgressService>();
    final t = Theme.of(context).textTheme;

    var index = 0;
    return Scaffold(
      body: InkBackground(
        accent: accent,
        child: CustomScrollView(
          slivers: [
            const SectionAppBar(
                title: 'Principi e tecniche', chinese: '理', accent: accent),
            SliverPadding(
              padding: const EdgeInsets.fromLTRB(24, 4, 24, 36),
              sliver: SliverList.list(
                children: [
                  Reveal(child: Text(repo.principlesIntro, style: t.bodyLarge)),
                  const SizedBox(height: 22),
                  Reveal(
                    index: 1,
                    child: ProgressBar(
                      done: progress
                          .countIn(repo.allPrinciples.map((e) => e.id)),
                      total: repo.allPrinciples.length,
                      color: accent,
                      label: 'Voci studiate',
                    ),
                  ),
                  const SizedBox(height: 30),
                  for (final group in repo.principleGroups) ...[
                    Reveal(
                      index: ++index,
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(group.title, style: t.headlineMedium),
                          if (group.subtitle.isNotEmpty) ...[
                            const SizedBox(height: 5),
                            Text(group.subtitle, style: t.bodySmall),
                          ],
                          const SizedBox(height: 14),
                        ],
                      ),
                    ),
                    for (final item in group.items)
                      Reveal(
                        index: ++index,
                        child: Padding(
                          padding: const EdgeInsets.only(bottom: 10),
                          child: _PrincipleTile(
                            principle: item,
                            studied: progress.isStudied(item.id),
                          ),
                        ),
                      ),
                    const SizedBox(height: 26),
                  ],
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _PrincipleTile extends StatelessWidget {
  const _PrincipleTile({required this.principle, required this.studied});

  final Principle principle;
  final bool studied;

  @override
  Widget build(BuildContext context) {
    final t = Theme.of(context).textTheme;
    final hasLesson = principle.lessonId != null;
    return Panel(
      accent: studied ? PrinciplesScreen.accent : AppColors.line,
      padding: const EdgeInsets.fromLTRB(16, 14, 12, 14),
      onTap: () => context.push('/principi/${principle.id}'),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Row(
                  children: [
                    Flexible(
                      child: Text(principle.name,
                          style: t.titleLarge, overflow: TextOverflow.ellipsis),
                    ),
                    if (studied) ...[
                      const SizedBox(width: 7),
                      const Icon(Icons.check_circle,
                          size: 14, color: PrinciplesScreen.accent),
                    ],
                  ],
                ),
                if (principle.chinese.isNotEmpty) ...[
                  const SizedBox(height: 2),
                  Text(principle.chinese,
                      style: t.bodySmall
                          ?.copyWith(color: AppColors.textFaint, fontSize: 12)),
                ],
                const SizedBox(height: 7),
                Text(principle.summary,
                    style: t.bodySmall?.copyWith(height: 1.5)),
                if (hasLesson) ...[
                  const SizedBox(height: 10),
                  const Pill('Lezione 3D',
                      icon: Icons.view_in_ar_outlined, color: AppColors.gold),
                ],
              ],
            ),
          ),
          const Padding(
            padding: EdgeInsets.only(left: 6, top: 2),
            child: Icon(Icons.chevron_right_rounded,
                size: 20, color: AppColors.textFaint),
          ),
        ],
      ),
    );
  }
}
