import 'package:flutter/material.dart';
import 'package:url_launcher/url_launcher.dart';

import '../../core/theme.dart';
import '../../core/widgets/common.dart';
import '../../data/content_repository.dart';
import '../../data/models.dart';
import 'video_tile.dart';

/// Selezione video dai maestri di riferimento.
class VideosScreen extends StatelessWidget {
  const VideosScreen({super.key});

  static const accent = AppColors.cinnabar;

  @override
  Widget build(BuildContext context) {
    final repo = ContentRepository.instance;
    final t = Theme.of(context).textTheme;

    var index = 0;
    return Scaffold(
      body: InkBackground(
        accent: accent,
        child: CustomScrollView(
          slivers: [
            const SectionAppBar(
                title: 'Video selection', chinese: '影', accent: accent),
            SliverPadding(
              padding: const EdgeInsets.fromLTRB(24, 4, 24, 36),
              sliver: SliverList.list(
                children: [
                  Reveal(child: Text(repo.videosIntro, style: t.bodyLarge)),
                  const SizedBox(height: 30),
                  for (final cat in repo.videoCategories) ...[
                    Reveal(
                      index: ++index,
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(cat.title, style: t.headlineMedium),
                          if (cat.subtitle.isNotEmpty) ...[
                            const SizedBox(height: 4),
                            Text(cat.subtitle, style: t.bodySmall),
                          ],
                          const SizedBox(height: 14),
                        ],
                      ),
                    ),
                    for (final v in cat.videos)
                      Reveal(
                        index: ++index,
                        child: Padding(
                          padding: const EdgeInsets.only(bottom: 10),
                          child: VideoTile(video: v),
                        ),
                      ),
                    const SizedBox(height: 26),
                  ],
                  const BrushDivider(),
                  const SizedBox(height: 24),
                  const Eyebrow('Le fonti ufficiali'),
                  const SizedBox(height: 6),
                  Text(
                    'Tutti i contenuti restano dei rispettivi autori. Da qui '
                    'raggiungi i loro canali e siti ufficiali.',
                    style: t.bodySmall,
                  ),
                  const SizedBox(height: 14),
                  for (final c in repo.channels)
                    Padding(
                      padding: const EdgeInsets.only(bottom: 10),
                      child: _ChannelTile(channel: c),
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

class _ChannelTile extends StatelessWidget {
  const _ChannelTile({required this.channel});

  final Channel channel;

  Future<void> _open(BuildContext context) async {
    final uri = Uri.parse(channel.url);
    final ok = await launchUrl(uri, mode: LaunchMode.externalApplication);
    if (!ok && context.mounted) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('Non riesco ad aprire ${channel.url}')),
      );
    }
  }

  @override
  Widget build(BuildContext context) {
    final t = Theme.of(context).textTheme;
    return Panel(
      padding: const EdgeInsets.fromLTRB(15, 14, 12, 14),
      onTap: () => _open(context),
      child: Row(
        children: [
          const Icon(Icons.open_in_new, size: 16, color: AppColors.cinnabar),
          const SizedBox(width: 13),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(channel.name, style: t.titleMedium),
                if (channel.who.isNotEmpty) ...[
                  const SizedBox(height: 2),
                  Text(channel.who,
                      style: t.bodySmall?.copyWith(fontSize: 11.5)),
                ],
                if (channel.note.isNotEmpty) ...[
                  const SizedBox(height: 5),
                  Text(channel.note,
                      style: t.bodySmall?.copyWith(fontSize: 11.5, height: 1.4)),
                ],
              ],
            ),
          ),
        ],
      ),
    );
  }
}
