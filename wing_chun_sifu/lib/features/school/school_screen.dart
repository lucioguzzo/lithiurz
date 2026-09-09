import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import 'package:url_launcher/url_launcher.dart';

import '../../core/theme.dart';
import '../../core/widgets/common.dart';
import '../../data/content_repository.dart';
import '../../data/models.dart';

/// La scuola da cui nasce l'app.
///
/// Non e' una pagina di crediti in fondo al menu: l'app segue una linea di
/// trasmissione precisa, e dire quale e' di chi e' un'informazione tecnica,
/// non una cortesia.
class SchoolScreen extends StatelessWidget {
  const SchoolScreen({super.key});

  static const accent = Color(0xFFC8452F);

  @override
  Widget build(BuildContext context) {
    final s = ContentRepository.instance.school;
    final t = Theme.of(context).textTheme;

    return Scaffold(
      body: InkBackground(
        accent: accent,
        child: CustomScrollView(
          slivers: [
            SliverAppBar(
              pinned: true,
              backgroundColor: AppColors.ink,
              surfaceTintColor: Colors.transparent,
              title: Text(s.name, style: t.titleLarge),
            ),
            SliverPadding(
              padding: const EdgeInsets.fromLTRB(24, 4, 24, 40),
              sliver: SliverList.list(
                children: [
                  Reveal(child: _Crest(school: s)),
                  const SizedBox(height: 22),
                  Reveal(index: 1, child: Text(s.intro, style: t.bodyLarge)),
                  const SizedBox(height: 24),
                  const Reveal(index: 2, child: BrushDivider(color: accent)),
                  const SizedBox(height: 24),
                  Reveal(
                    index: 3,
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        const Eyebrow('Il Maestro', color: accent),
                        const SizedBox(height: 6),
                        Text(s.sifuName, style: t.displaySmall),
                        const SizedBox(height: 4),
                        Text(s.sifuRole,
                            style: t.bodySmall?.copyWith(color: accent)),
                        const SizedBox(height: 16),
                        Paragraphs(s.sifuBody),
                      ],
                    ),
                  ),
                  const SizedBox(height: 26),
                  Reveal(
                    index: 4,
                    child: Panel(
                      accent: accent,
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Eyebrow(s.lineageTitle, color: accent),
                          const SizedBox(height: 12),
                          Paragraphs(s.lineageBody),
                        ],
                      ),
                    ),
                  ),
                  if (s.academyBody.isNotEmpty) ...[
                    const SizedBox(height: 22),
                    Reveal(
                      index: 5,
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Eyebrow(s.academyTitle),
                          const SizedBox(height: 10),
                          Paragraphs(s.academyBody),
                        ],
                      ),
                    ),
                  ],
                  const SizedBox(height: 22),
                  Reveal(
                    index: 6,
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Eyebrow(s.seniorsTitle),
                        const SizedBox(height: 6),
                        Text(s.seniorsNote, style: t.bodySmall),
                        const SizedBox(height: 12),
                        Wrap(
                          spacing: 8,
                          runSpacing: 8,
                          children: [
                            for (final n in s.seniors)
                              Pill(n, icon: Icons.person_outline, color: accent),
                          ],
                        ),
                      ],
                    ),
                  ),
                  const SizedBox(height: 30),
                  const Reveal(index: 6, child: Eyebrow('Dove trovare la scuola')),
                  const SizedBox(height: 12),
                  for (var i = 0; i < s.links.length; i++)
                    Reveal(
                      index: 7 + i,
                      child: Padding(
                        padding: const EdgeInsets.only(bottom: 10),
                        child: _LinkTile(link: s.links[i]),
                      ),
                    ),
                  const SizedBox(height: 22),
                  Reveal(
                    index: 12,
                    child: Panel(
                      padding: const EdgeInsets.fromLTRB(15, 14, 12, 14),
                      onTap: () => context.push('/video'),
                      child: Row(
                        children: [
                          const Icon(Icons.play_circle_outline,
                              size: 18, color: accent),
                          const SizedBox(width: 13),
                          Expanded(
                            child: Column(
                              crossAxisAlignment: CrossAxisAlignment.start,
                              children: [
                                Text('Gli episodi dell\'IDPA Academy',
                                    style: t.titleMedium),
                                const SizedBox(height: 2),
                                Text(
                                    'Storia, cultura e principi del Wing Chun '
                                    'raccontati da SiFu Fiorentini',
                                    style: t.bodySmall
                                        ?.copyWith(fontSize: 11.5)),
                              ],
                            ),
                          ),
                          const Icon(Icons.chevron_right_rounded,
                              size: 19, color: AppColors.textFaint),
                        ],
                      ),
                    ),
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

/// Il blocco d'apertura: sigla, nome esteso, fondazione.
class _Crest extends StatelessWidget {
  const _Crest({required this.school});

  final School school;

  @override
  Widget build(BuildContext context) {
    final t = Theme.of(context).textTheme;
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.fromLTRB(22, 24, 22, 22),
      decoration: BoxDecoration(
        borderRadius: BorderRadius.circular(18),
        border:
            Border.all(color: SchoolScreen.accent.withValues(alpha: 0.42)),
        gradient: LinearGradient(
          begin: Alignment.topLeft,
          end: Alignment.bottomRight,
          colors: [
            Color.alphaBlend(
                SchoolScreen.accent.withValues(alpha: 0.16), AppColors.surface),
            AppColors.surface,
          ],
        ),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Expanded(
                child: Text(school.name,
                    style: t.displayLarge?.copyWith(
                        color: SchoolScreen.accent, letterSpacing: 1)),
              ),
              Padding(
                padding: const EdgeInsets.only(top: 6),
                child: Text('龍鳳',
                    style: TextStyle(
                      fontFamily: AppText.cjk,
                      fontSize: 34,
                      height: 1,
                      color: SchoolScreen.accent.withValues(alpha: 0.35),
                    )),
              ),
            ],
          ),
          const SizedBox(height: 4),
          Text(school.fullName, style: t.headlineSmall?.copyWith(fontSize: 18)),
          const SizedBox(height: 10),
          Row(
            children: [
              const Icon(Icons.place_outlined,
                  size: 13, color: AppColors.textFaint),
              const SizedBox(width: 6),
              Text(school.founded, style: t.bodySmall),
            ],
          ),
        ],
      ),
    );
  }
}

class _LinkTile extends StatelessWidget {
  const _LinkTile({required this.link});

  final SchoolLink link;

  static const _icons = <String, IconData>{
    'youtube': Icons.smart_display_outlined,
    'facebook': Icons.groups_outlined,
    'instagram': Icons.photo_camera_outlined,
    'web': Icons.language,
  };

  Future<void> _open(BuildContext context) async {
    final ok = await launchUrl(Uri.parse(link.url),
        mode: LaunchMode.externalApplication);
    if (!ok && context.mounted) {
      ScaffoldMessenger.of(context)
          .showSnackBar(SnackBar(content: Text('Non riesco ad aprire ${link.url}')));
    }
  }

  @override
  Widget build(BuildContext context) {
    final t = Theme.of(context).textTheme;
    final c = link.primary ? SchoolScreen.accent : AppColors.textFaint;
    return Panel(
      accent: link.primary ? SchoolScreen.accent : AppColors.line,
      padding: const EdgeInsets.fromLTRB(15, 14, 12, 14),
      onTap: () => _open(context),
      child: Row(
        children: [
          Icon(_icons[link.kind] ?? Icons.language, size: 17, color: c),
          const SizedBox(width: 13),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(link.label, style: t.titleMedium),
                if (link.note.isNotEmpty) ...[
                  const SizedBox(height: 4),
                  Text(link.note,
                      style: t.bodySmall?.copyWith(fontSize: 11.5, height: 1.4)),
                ],
              ],
            ),
          ),
          const SizedBox(width: 8),
          const Icon(Icons.open_in_new, size: 14, color: AppColors.textFaint),
        ],
      ),
    );
  }
}
