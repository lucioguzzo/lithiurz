import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import 'package:provider/provider.dart';

import '../../core/theme.dart';
import '../../core/widgets/common.dart';
import '../../data/content_repository.dart';
import '../../data/progress_service.dart';
import '../school/school_screen.dart';

/// Il menu principale: cinque sezioni, piu' la ripresa dell'ultima lezione.
class HomeScreen extends StatelessWidget {
  const HomeScreen({super.key});

  @override
  Widget build(BuildContext context) {
    final repo = ContentRepository.instance;
    final progress = context.watch<ProgressService>();
    final t = Theme.of(context).textTheme;

    final studiedTotal = progress.studied.length;
    final last = repo.lesson(progress.lastLesson);

    final sections = <_Section>[
      _Section(
        id: 'storia',
        title: 'Storia del Wing Chun',
        subtitle: 'Dalla leggenda di Ng Mui alle scuole di oggi',
        chinese: '史',
        icon: Icons.auto_stories_outlined,
        route: '/storia',
        count: '${repo.historyChapters.length} capitoli',
      ),
      _Section(
        id: 'principi',
        title: 'Principi e tecniche di base',
        subtitle: 'La linea centrale, il gomito, le mani del sistema',
        chinese: '理',
        icon: Icons.hub_outlined,
        route: '/principi',
        count: '${repo.allPrinciples.length} voci',
        done: progress.countIn(repo.allPrinciples.map((e) => e.id)),
        total: repo.allPrinciples.length,
      ),
      _Section(
        id: 'forme',
        title: 'Forme',
        subtitle: 'Le sequenze che conservano i principi del sistema',
        chinese: '式',
        icon: Icons.view_timeline_outlined,
        route: '/forme',
        count: '${repo.forms.length} forme',
        done: progress.countIn(repo.forms.map((e) => e.id)),
        total: repo.forms.length,
      ),
      _Section(
        id: 'sifu3d',
        title: '3D SiFu — Lessons',
        subtitle: 'Il SiFu ti mostra ogni tecnica da ogni angolazione',
        chinese: '師',
        icon: Icons.view_in_ar_outlined,
        route: '/sifu3d',
        count: '${repo.lessons.length} lezioni',
        done: progress.countIn(repo.lessons.map((e) => e.id)),
        total: repo.lessons.length,
        highlight: true,
      ),
      _Section(
        id: 'video',
        title: 'Video selection',
        subtitle: 'Una selezione dai maestri di riferimento',
        chinese: '影',
        icon: Icons.play_circle_outline,
        route: '/video',
        count:
            '${repo.videoCategories.fold<int>(0, (s, c) => s + c.videos.length)} video',
      ),
    ];

    return Scaffold(
      body: InkBackground(
        child: SafeArea(
          child: CustomScrollView(
            slivers: [
              SliverToBoxAdapter(
                child: Padding(
                  padding: const EdgeInsets.fromLTRB(24, 20, 24, 8),
                  child: Reveal(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Row(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Expanded(
                              child: Column(
                                crossAxisAlignment: CrossAxisAlignment.start,
                                children: [
                                  const Eyebrow('La scuola'),
                                  const SizedBox(height: 6),
                                  Text('Wing Chun', style: t.displayMedium),
                                  Text('SiFu Online',
                                      style: t.displayMedium
                                          ?.copyWith(color: AppColors.gold)),
                                ],
                              ),
                            ),
                            Padding(
                              padding: const EdgeInsets.only(top: 10),
                              child: Text('詠春',
                                  style: TextStyle(
                                    fontFamily: AppText.cjk,
                                    fontSize: 30,
                                    height: 1,
                                    color:
                                        AppColors.gold.withValues(alpha: 0.30),
                                    letterSpacing: 3,
                                  )),
                            ),
                          ],
                        ),
                        const SizedBox(height: 18),
                        if (studiedTotal > 0)
                          ProgressBar(
                            done: studiedTotal,
                            total: repo.totalLearnable,
                            label: 'Il tuo percorso',
                          ),
                      ],
                    ),
                  ),
                ),
              ),
              const SliverToBoxAdapter(
                child: Padding(
                  padding: EdgeInsets.fromLTRB(24, 18, 24, 0),
                  child: Reveal(index: 1, child: _SchoolBanner()),
                ),
              ),
              if (last != null)
                SliverToBoxAdapter(
                  child: Padding(
                    padding: const EdgeInsets.fromLTRB(24, 16, 24, 4),
                    child: Reveal(index: 2, child: _ResumeCard(lessonId: last.id)),
                  ),
                ),
              SliverPadding(
                padding: const EdgeInsets.fromLTRB(24, 20, 24, 36),
                sliver: SliverList.separated(
                  itemCount: sections.length,
                  separatorBuilder: (_, __) => const SizedBox(height: 12),
                  itemBuilder: (context, i) => Reveal(
                    index: i + 3,
                    child: _SectionCard(section: sections[i]),
                  ),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

/// La scuola da cui nasce l'app, in testa al menu: non e' un credito da
/// mettere in fondo, e' il riferimento che spiega da dove vengono i contenuti.
class _SchoolBanner extends StatelessWidget {
  const _SchoolBanner();

  @override
  Widget build(BuildContext context) {
    final s = ContentRepository.instance.school;
    final t = Theme.of(context).textTheme;
    const c = SchoolScreen.accent;
    return Material(
      color: Colors.transparent,
      child: InkWell(
        onTap: () => context.push('/scuola'),
        borderRadius: BorderRadius.circular(16),
        child: Container(
          padding: const EdgeInsets.fromLTRB(16, 14, 12, 14),
          decoration: BoxDecoration(
            borderRadius: BorderRadius.circular(16),
            border: Border.all(color: c.withValues(alpha: 0.45)),
            gradient: LinearGradient(
              begin: Alignment.centerLeft,
              end: Alignment.centerRight,
              colors: [
                Color.alphaBlend(c.withValues(alpha: 0.17), AppColors.surface),
                AppColors.surface,
              ],
            ),
          ),
          child: Row(
            children: [
              Container(
                width: 44,
                height: 44,
                alignment: Alignment.center,
                decoration: BoxDecoration(
                  color: c.withValues(alpha: 0.16),
                  borderRadius: BorderRadius.circular(12),
                  border: Border.all(color: c.withValues(alpha: 0.38)),
                ),
                child: const Text('龍鳳',
                    style: TextStyle(
                        fontFamily: AppText.cjk,
                        fontSize: 15,
                        height: 1.1,
                        color: c)),
              ),
              const SizedBox(width: 13),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    const Eyebrow('La scuola', color: c),
                    const SizedBox(height: 3),
                    Text('${s.name} · ${s.sifuName}',
                        style: t.titleLarge,
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis),
                    const SizedBox(height: 2),
                    Text(s.fullName,
                        style: t.bodySmall?.copyWith(fontSize: 11.5),
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis),
                  ],
                ),
              ),
              const Icon(Icons.chevron_right_rounded, color: c, size: 22),
            ],
          ),
        ),
      ),
    );
  }
}

class _Section {
  const _Section({
    required this.id,
    required this.title,
    required this.subtitle,
    required this.chinese,
    required this.icon,
    required this.route,
    required this.count,
    this.done,
    this.total,
    this.highlight = false,
  });

  final String id;
  final String title;
  final String subtitle;
  final String chinese;
  final IconData icon;
  final String route;
  final String count;
  final int? done;
  final int? total;
  final bool highlight;

  Color get color => AppColors.sectionColors[id] ?? AppColors.gold;
}

class _SectionCard extends StatelessWidget {
  const _SectionCard({required this.section});

  final _Section section;

  @override
  Widget build(BuildContext context) {
    final t = Theme.of(context).textTheme;
    final c = section.color;
    return Material(
      color: Colors.transparent,
      child: InkWell(
        onTap: () => context.push(section.route),
        borderRadius: BorderRadius.circular(18),
        child: Container(
          decoration: BoxDecoration(
            borderRadius: BorderRadius.circular(18),
            border: Border.all(
                color: c.withValues(alpha: section.highlight ? 0.55 : 0.28)),
            gradient: LinearGradient(
              begin: Alignment.centerLeft,
              end: Alignment.centerRight,
              colors: [
                Color.alphaBlend(
                    c.withValues(alpha: section.highlight ? 0.16 : 0.09),
                    AppColors.surface),
                AppColors.surface,
              ],
            ),
          ),
          child: Stack(
            children: [
              Positioned(
                right: 12,
                top: -6,
                child: Text(
                  section.chinese,
                  style: TextStyle(
                    fontFamily: AppText.cjk,
                    fontSize: 66,
                    height: 1.1,
                    fontWeight: FontWeight.w300,
                    color: c.withValues(alpha: 0.12),
                  ),
                ),
              ),
              Padding(
                padding: const EdgeInsets.fromLTRB(18, 18, 18, 16),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Row(
                      children: [
                        Container(
                          width: 38,
                          height: 38,
                          decoration: BoxDecoration(
                            color: c.withValues(alpha: 0.14),
                            borderRadius: BorderRadius.circular(11),
                            border:
                                Border.all(color: c.withValues(alpha: 0.34)),
                          ),
                          child: Icon(section.icon, size: 19, color: c),
                        ),
                        const SizedBox(width: 13),
                        Expanded(
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              Text(section.title, style: t.headlineSmall),
                              const SizedBox(height: 3),
                              Text(section.subtitle,
                                  style: t.bodySmall,
                                  maxLines: 2,
                                  overflow: TextOverflow.ellipsis),
                            ],
                          ),
                        ),
                        const SizedBox(width: 8),
                        Icon(Icons.chevron_right_rounded,
                            color: c.withValues(alpha: 0.8), size: 22),
                      ],
                    ),
                    const SizedBox(height: 14),
                    Row(
                      children: [
                        Pill(section.count, color: c),
                        const Spacer(),
                        if (section.total != null && section.total! > 0)
                          SizedBox(
                            width: 96,
                            child: ProgressBar(
                              done: section.done ?? 0,
                              total: section.total!,
                              color: c,
                            ),
                          ),
                      ],
                    ),
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

class _ResumeCard extends StatelessWidget {
  const _ResumeCard({required this.lessonId});

  final String lessonId;

  @override
  Widget build(BuildContext context) {
    final lesson = ContentRepository.instance.lesson(lessonId)!;
    final t = Theme.of(context).textTheme;
    return Panel(
      accent: AppColors.gold,
      padding: const EdgeInsets.all(10),
      onTap: () => context.push('/sifu3d/${lesson.id}'),
      child: Row(
        children: [
          ClipRRect(
            borderRadius: BorderRadius.circular(10),
            child: Image.asset(lesson.thumbnail,
                width: 54, height: 66, fit: BoxFit.cover),
          ),
          const SizedBox(width: 14),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                const Eyebrow('Riprendi da qui', color: AppColors.gold),
                const SizedBox(height: 4),
                Text(lesson.title, style: t.titleLarge),
              ],
            ),
          ),
          const Padding(
            padding: EdgeInsets.only(right: 8),
            child: Icon(Icons.play_arrow_rounded, color: AppColors.gold),
          ),
        ],
      ),
    );
  }
}
