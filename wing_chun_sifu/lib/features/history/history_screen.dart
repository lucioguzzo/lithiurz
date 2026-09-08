import 'package:flutter/material.dart';

import '../../core/theme.dart';
import '../../core/widgets/common.dart';
import '../../data/content_repository.dart';
import '../../data/models.dart';

/// Storia del Wing Chun.
///
/// I capitoli arrivano interamente da assets/data/history.json: la
/// documentazione definitiva potra' sostituire quel file senza toccare una
/// riga di codice. Finche' i contenuti sono provvisori l'app lo dichiara.
class HistoryScreen extends StatelessWidget {
  const HistoryScreen({super.key});

  static const accent = Color(0xFFB98A4A);

  @override
  Widget build(BuildContext context) {
    final repo = ContentRepository.instance;
    final t = Theme.of(context).textTheme;

    return Scaffold(
      body: InkBackground(
        accent: accent,
        child: CustomScrollView(
          slivers: [
            const SectionAppBar(
                title: 'Storia del Wing Chun', chinese: '史', accent: accent),
            SliverPadding(
              padding: const EdgeInsets.fromLTRB(24, 4, 24, 36),
              sliver: SliverList.list(
                children: [
                  Reveal(child: Text(repo.historyIntro, style: t.bodyLarge)),
                  const SizedBox(height: 20),
                  if (repo.historyStatus == 'provvisorio')
                    Reveal(
                      index: 1,
                      child: Panel(
                        accent: accent,
                        padding: const EdgeInsets.all(15),
                        child: Row(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            const Icon(Icons.info_outline,
                                size: 17, color: accent),
                            const SizedBox(width: 11),
                            Expanded(
                              child: Text(repo.historyNotice,
                                  style: t.bodySmall?.copyWith(height: 1.55)),
                            ),
                          ],
                        ),
                      ),
                    ),
                  const SizedBox(height: 26),
                  for (var i = 0; i < repo.historyChapters.length; i++)
                    Reveal(
                      index: i + 2,
                      child: _Chapter(
                        chapter: repo.historyChapters[i],
                        number: i + 1,
                        isLast: i == repo.historyChapters.length - 1,
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

class _Chapter extends StatelessWidget {
  const _Chapter({
    required this.chapter,
    required this.number,
    required this.isLast,
  });

  final HistoryChapter chapter;
  final int number;
  final bool isLast;

  @override
  Widget build(BuildContext context) {
    final t = Theme.of(context).textTheme;
    return IntrinsicHeight(
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          // filo verticale della cronologia
          Column(
            children: [
              Container(
                width: 26,
                height: 26,
                alignment: Alignment.center,
                decoration: BoxDecoration(
                  shape: BoxShape.circle,
                  color: HistoryScreen.accent.withValues(alpha: 0.14),
                  border: Border.all(
                      color: HistoryScreen.accent.withValues(alpha: 0.45)),
                ),
                child: Text('$number',
                    style: t.labelMedium
                        ?.copyWith(color: HistoryScreen.accent, letterSpacing: 0)),
              ),
              if (!isLast)
                Expanded(
                  child: Container(
                    width: 1,
                    margin: const EdgeInsets.symmetric(vertical: 6),
                    color: AppColors.line,
                  ),
                ),
            ],
          ),
          const SizedBox(width: 16),
          Expanded(
            child: Padding(
              padding: EdgeInsets.only(bottom: isLast ? 0 : 30),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  if (chapter.era.isNotEmpty) Eyebrow(chapter.era),
                  const SizedBox(height: 5),
                  Text(chapter.title, style: t.headlineSmall),
                  const SizedBox(height: 10),
                  Paragraphs(chapter.body),
                  if (chapter.sources.isNotEmpty) ...[
                    const SizedBox(height: 14),
                    for (final s in chapter.sources)
                      Padding(
                        padding: const EdgeInsets.only(bottom: 5),
                        child: Row(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            const Icon(Icons.bookmark_border,
                                size: 13, color: AppColors.textFaint),
                            const SizedBox(width: 7),
                            Expanded(child: Text(s, style: t.bodySmall)),
                          ],
                        ),
                      ),
                  ],
                ],
              ),
            ),
          ),
        ],
      ),
    );
  }
}
