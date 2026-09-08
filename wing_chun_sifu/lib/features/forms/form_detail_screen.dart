import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../core/theme.dart';
import '../../core/widgets/common.dart';
import '../../data/content_repository.dart';
import '../../data/models.dart';
import '../../data/progress_service.dart';
import '../sifu3d/lesson_link_card.dart';
import '../videos/video_tile.dart';
import 'forms_screen.dart';

class FormDetailScreen extends StatelessWidget {
  const FormDetailScreen({super.key, required this.id});

  final String id;

  @override
  Widget build(BuildContext context) {
    final repo = ContentRepository.instance;
    final form = repo.form(id);
    final t = Theme.of(context).textTheme;
    const accent = FormsScreen.accent;

    if (form == null) {
      return Scaffold(
        appBar: AppBar(),
        body: Center(child: Text('Forma non trovata', style: t.bodyMedium)),
      );
    }

    final studied = context.watch<ProgressService>().isStudied(form.id);
    final lessons =
        form.lessonIds.map(repo.lesson).whereType<Lesson>().toList();
    final videos =
        form.videoIds.map(repo.video).whereType<VideoItem>().toList();

    return Scaffold(
      body: InkBackground(
        accent: accent,
        child: CustomScrollView(
          slivers: [
            SliverAppBar(
              pinned: true,
              backgroundColor: AppColors.ink,
              surfaceTintColor: Colors.transparent,
              title: Text(form.name,
                  style: t.titleLarge, overflow: TextOverflow.ellipsis),
              actions: [
                IconButton(
                  tooltip:
                      studied ? 'Segna da rivedere' : 'Segna come studiata',
                  onPressed: () =>
                      ProgressService.instance.toggleStudied(form.id),
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
                        Text(form.chinese,
                            style: TextStyle(
                                fontFamily: AppText.cjk,
                                fontSize: 40,
                                height: 1.2,
                                fontWeight: FontWeight.w300,
                                color: accent.withValues(alpha: 0.9))),
                        const SizedBox(height: 6),
                        Text(form.translation,
                            style: t.bodyMedium
                                ?.copyWith(fontStyle: FontStyle.italic)),
                        const SizedBox(height: 14),
                        Row(
                          children: [
                            LevelDots(level: form.level),
                            const SizedBox(width: 10),
                            Text(
                                switch (form.level) {
                                  1 => 'Livello base',
                                  2 => 'Livello intermedio',
                                  _ => 'Livello avanzato',
                                },
                                style: t.bodySmall),
                            const Spacer(),
                            Flexible(
                              child: Text(
                                  repo.lineages[form.lineage]?.label ?? '',
                                  style: t.bodySmall?.copyWith(color: accent),
                                  textAlign: TextAlign.end,
                                  overflow: TextOverflow.ellipsis),
                            ),
                          ],
                        ),
                        const SizedBox(height: 18),
                        Text(form.summary,
                            style: t.bodyLarge?.copyWith(
                                fontSize: 17, height: 1.55)),
                      ],
                    ),
                  ),
                  const SizedBox(height: 20),
                  const Reveal(index: 1, child: BrushDivider()),
                  const SizedBox(height: 20),
                  Reveal(index: 2, child: Paragraphs(form.body)),
                  if (form.sections.isNotEmpty) ...[
                    const SizedBox(height: 28),
                    const Reveal(
                      index: 3,
                      child: Eyebrow('Struttura della forma'),
                    ),
                    const SizedBox(height: 14),
                    for (var i = 0; i < form.sections.length; i++)
                      Reveal(
                        index: 4 + i,
                        child: Padding(
                          padding: const EdgeInsets.only(bottom: 10),
                          child: _SectionRow(section: form.sections[i]),
                        ),
                      ),
                  ],
                  if (form.develops.isNotEmpty) ...[
                    const SizedBox(height: 24),
                    Reveal(
                      index: 9,
                      child: Panel(
                        accent: accent,
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            const Eyebrow('Cosa sviluppa', color: accent),
                            const SizedBox(height: 13),
                            KeyPoints(form.develops, color: accent),
                          ],
                        ),
                      ),
                    ),
                  ],
                  if (lessons.isNotEmpty) ...[
                    const SizedBox(height: 26),
                    const Reveal(index: 10, child: Eyebrow('Guardala con il SiFu 3D')),
                    const SizedBox(height: 12),
                    for (var i = 0; i < lessons.length; i++)
                      Reveal(
                        index: 11 + i,
                        child: Padding(
                          padding: const EdgeInsets.only(bottom: 9),
                          child: LessonLinkCard(lesson: lessons[i], dense: true),
                        ),
                      ),
                  ],
                  if (videos.isNotEmpty) ...[
                    const SizedBox(height: 26),
                    const Reveal(index: 12, child: Eyebrow('Video di riferimento')),
                    const SizedBox(height: 12),
                    for (var i = 0; i < videos.length; i++)
                      Padding(
                        padding: const EdgeInsets.only(bottom: 10),
                        child: VideoTile(video: videos[i]),
                      ),
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

class _SectionRow extends StatelessWidget {
  const _SectionRow({required this.section});

  final FormSection section;

  @override
  Widget build(BuildContext context) {
    final t = Theme.of(context).textTheme;
    return Panel(
      padding: const EdgeInsets.fromLTRB(15, 14, 15, 14),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Container(
            width: 24,
            height: 24,
            alignment: Alignment.center,
            decoration: BoxDecoration(
              shape: BoxShape.circle,
              color: FormsScreen.accent.withValues(alpha: 0.14),
            ),
            child: Text('${section.n}',
                style: t.labelMedium?.copyWith(
                    color: FormsScreen.accent, letterSpacing: 0)),
          ),
          const SizedBox(width: 13),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(section.title, style: t.titleMedium),
                const SizedBox(height: 5),
                Text(section.text,
                    style: t.bodySmall?.copyWith(height: 1.55)),
              ],
            ),
          ),
        ],
      ),
    );
  }
}
