import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import 'package:provider/provider.dart';

import '../../core/theme.dart';
import '../../core/widgets/common.dart';
import '../../data/content_repository.dart';
import '../../data/models.dart';
import '../../data/progress_service.dart';

/// Le forme, raggruppate per lignaggio.
class FormsScreen extends StatelessWidget {
  const FormsScreen({super.key});

  static const accent = Color(0xFF7C7FC4);

  @override
  Widget build(BuildContext context) {
    final repo = ContentRepository.instance;
    final progress = context.watch<ProgressService>();
    final t = Theme.of(context).textTheme;

    final byLineage = <String, List<WingChunForm>>{};
    for (final f in repo.forms) {
      byLineage.putIfAbsent(f.lineage, () => []).add(f);
    }

    var index = 0;
    return Scaffold(
      body: InkBackground(
        accent: accent,
        child: CustomScrollView(
          slivers: [
            const SectionAppBar(
                title: 'Forme', chinese: '式', accent: accent),
            SliverPadding(
              padding: const EdgeInsets.fromLTRB(24, 4, 24, 36),
              sliver: SliverList.list(
                children: [
                  Reveal(child: Text(repo.formsIntro, style: t.bodyLarge)),
                  const SizedBox(height: 20),
                  Reveal(
                    index: 1,
                    child: ProgressBar(
                      done: progress.countIn(repo.forms.map((e) => e.id)),
                      total: repo.forms.length,
                      color: accent,
                      label: 'Forme studiate',
                    ),
                  ),
                  const SizedBox(height: 30),
                  for (final entry in byLineage.entries) ...[
                    Reveal(
                      index: ++index,
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(
                              repo.lineages[entry.key]?.label ?? entry.key,
                              style: t.headlineMedium),
                          const SizedBox(height: 5),
                          Text(repo.lineages[entry.key]?.note ?? '',
                              style: t.bodySmall),
                          const SizedBox(height: 14),
                        ],
                      ),
                    ),
                    for (final f in entry.value)
                      Reveal(
                        index: ++index,
                        child: Padding(
                          padding: const EdgeInsets.only(bottom: 10),
                          child: _FormTile(
                            form: f,
                            studied: progress.isStudied(f.id),
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

/// Tre pallini che indicano il livello della forma.
class LevelDots extends StatelessWidget {
  const LevelDots({super.key, required this.level, this.color});

  final int level;
  final Color? color;

  @override
  Widget build(BuildContext context) {
    final c = color ?? FormsScreen.accent;
    return Row(
      mainAxisSize: MainAxisSize.min,
      children: [
        for (var i = 1; i <= 3; i++)
          Padding(
            padding: const EdgeInsets.only(right: 3),
            child: Container(
              width: 5,
              height: 5,
              decoration: BoxDecoration(
                shape: BoxShape.circle,
                color: i <= level ? c : c.withValues(alpha: 0.22),
              ),
            ),
          ),
      ],
    );
  }
}

class _FormTile extends StatelessWidget {
  const _FormTile({required this.form, required this.studied});

  final WingChunForm form;
  final bool studied;

  @override
  Widget build(BuildContext context) {
    final t = Theme.of(context).textTheme;
    return Panel(
      accent: studied ? FormsScreen.accent : AppColors.line,
      padding: const EdgeInsets.fromLTRB(16, 15, 12, 15),
      onTap: () => context.push('/forme/${form.id}'),
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
                      child: Text(form.name,
                          style: t.headlineSmall?.copyWith(fontSize: 19),
                          overflow: TextOverflow.ellipsis),
                    ),
                    const SizedBox(width: 8),
                    Text(form.chinese,
                        style: TextStyle(
                            fontFamily: AppText.cjk,
                            fontSize: 15,
                            color: FormsScreen.accent
                                .withValues(alpha: 0.75))),
                    if (studied) ...[
                      const SizedBox(width: 7),
                      const Icon(Icons.check_circle,
                          size: 14, color: FormsScreen.accent),
                    ],
                  ],
                ),
                const SizedBox(height: 2),
                Text(form.translation,
                    style: t.bodySmall?.copyWith(
                        fontSize: 12, fontStyle: FontStyle.italic)),
                const SizedBox(height: 8),
                Text(form.summary, style: t.bodySmall?.copyWith(height: 1.5)),
                const SizedBox(height: 11),
                Row(
                  children: [
                    LevelDots(level: form.level),
                    const SizedBox(width: 10),
                    if (form.lessonIds.isNotEmpty)
                      Pill('${form.lessonIds.length} in 3D',
                          icon: Icons.view_in_ar_outlined,
                          color: AppColors.gold),
                    if (form.videoIds.isNotEmpty) ...[
                      const SizedBox(width: 6),
                      Pill('${form.videoIds.length} video',
                          icon: Icons.play_circle_outline,
                          color: AppColors.cinnabar),
                    ],
                  ],
                ),
              ],
            ),
          ),
          const Padding(
            padding: EdgeInsets.only(left: 6, top: 4),
            child: Icon(Icons.chevron_right_rounded,
                size: 20, color: AppColors.textFaint),
          ),
        ],
      ),
    );
  }
}
