import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import 'package:provider/provider.dart';

import '../../core/theme.dart';
import '../../core/widgets/common.dart';
import '../../data/content_repository.dart';
import '../../data/models.dart';
import '../../data/progress_service.dart';

/// Elenco delle lezioni del SiFu 3D.
class LessonsScreen extends StatelessWidget {
  const LessonsScreen({super.key});

  static const accent = AppColors.gold;

  @override
  Widget build(BuildContext context) {
    final repo = ContentRepository.instance;
    final progress = context.watch<ProgressService>();
    final t = Theme.of(context).textTheme;
    final lessons = repo.lessons;

    return Scaffold(
      body: InkBackground(
        accent: accent,
        child: CustomScrollView(
          slivers: [
            const SectionAppBar(
                title: '3D SiFu — Lessons', chinese: '師', accent: accent),
            SliverPadding(
              padding: const EdgeInsets.fromLTRB(24, 4, 24, 16),
              sliver: SliverList.list(
                children: [
                  Reveal(
                    child: Text(
                      'Il SiFu esegue ogni tecnica in tre dimensioni. Puoi '
                      'ruotarlo con un dito, avvicinarti, e guardare il gesto '
                      'esattamente dall\'angolazione da cui non riusciresti a '
                      'vederlo in palestra.',
                      style: t.bodyLarge,
                    ),
                  ),
                  const SizedBox(height: 20),
                  Reveal(
                    index: 1,
                    child: ProgressBar(
                      done: progress.countIn(lessons.map((e) => e.id)),
                      total: lessons.length,
                      color: accent,
                      label: 'Lezioni viste',
                    ),
                  ),
                  const SizedBox(height: 22),
                ],
              ),
            ),
            SliverPadding(
              padding: const EdgeInsets.fromLTRB(24, 0, 24, 36),
              sliver: SliverGrid.builder(
                gridDelegate:
                    const SliverGridDelegateWithFixedCrossAxisCount(
                  crossAxisCount: 2,
                  mainAxisSpacing: 12,
                  crossAxisSpacing: 12,
                  childAspectRatio: 0.63,
                ),
                itemCount: lessons.length,
                itemBuilder: (context, i) => Reveal(
                  index: i + 2,
                  child: _LessonCard(
                    lesson: lessons[i],
                    seen: progress.isStudied(lessons[i].id),
                  ),
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _LessonCard extends StatelessWidget {
  const _LessonCard({required this.lesson, required this.seen});

  final Lesson lesson;
  final bool seen;

  @override
  Widget build(BuildContext context) {
    final t = Theme.of(context).textTheme;
    return Material(
      color: Colors.transparent,
      child: InkWell(
        onTap: () => context.push('/sifu3d/${lesson.id}'),
        borderRadius: BorderRadius.circular(16),
        child: Container(
          clipBehavior: Clip.antiAlias,
          decoration: BoxDecoration(
            color: AppColors.surface,
            borderRadius: BorderRadius.circular(16),
            border: Border.all(
              color: seen
                  ? AppColors.gold.withValues(alpha: 0.45)
                  : AppColors.line,
            ),
          ),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Expanded(
                child: Stack(
                  fit: StackFit.expand,
                  children: [
                    Image.asset(lesson.thumbnail, fit: BoxFit.cover),
                    Positioned.fill(
                      child: DecoratedBox(
                        decoration: BoxDecoration(
                          gradient: LinearGradient(
                            begin: Alignment.topCenter,
                            end: Alignment.bottomCenter,
                            colors: [
                              Colors.transparent,
                              AppColors.surface.withValues(alpha: 0.85),
                            ],
                            stops: const [0.55, 1.0],
                          ),
                        ),
                      ),
                    ),
                    if (seen)
                      const Positioned(
                        top: 8,
                        right: 8,
                        child: Icon(Icons.check_circle,
                            size: 16, color: AppColors.gold),
                      ),
                    Positioned(
                      left: 8,
                      top: 8,
                      child: Pill('${lesson.duration.toStringAsFixed(0)}s',
                          color: AppColors.textMuted),
                    ),
                  ],
                ),
              ),
              Padding(
                padding: const EdgeInsets.fromLTRB(12, 10, 12, 13),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(lesson.title,
                        style: t.titleMedium,
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis),
                    const SizedBox(height: 4),
                    Text(lesson.description,
                        style: t.bodySmall?.copyWith(fontSize: 11.5, height: 1.4),
                        maxLines: 3,
                        overflow: TextOverflow.ellipsis),
                  ],
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
