import 'package:flutter/material.dart';
import 'package:url_launcher/url_launcher.dart';
import 'package:youtube_player_iframe/youtube_player_iframe.dart';

import '../../core/theme.dart';
import '../../core/widgets/common.dart';
import '../../data/content_repository.dart';
import '../../data/models.dart';

/// Riproduzione di un video tramite il player ufficiale di YouTube.
/// L'app non ospita copie dei filmati: li mostra dalla loro sede originale.
class VideoScreen extends StatefulWidget {
  const VideoScreen({super.key, required this.id});

  final String id;

  @override
  State<VideoScreen> createState() => _VideoScreenState();
}

class _VideoScreenState extends State<VideoScreen> {
  YoutubePlayerController? _controller;
  VideoItem? _video;

  @override
  void initState() {
    super.initState();
    _video = ContentRepository.instance.video(widget.id);
    if (_video != null) {
      _controller = YoutubePlayerController.fromVideoId(
        videoId: _video!.id,
        autoPlay: false,
        params: const YoutubePlayerParams(
          showControls: true,
          showFullscreenButton: true,
          strictRelatedVideos: true,
        ),
      );
    }
  }

  @override
  void dispose() {
    _controller?.close();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final t = Theme.of(context).textTheme;
    final video = _video;
    if (video == null || _controller == null) {
      return Scaffold(
        appBar: AppBar(),
        body: Center(child: Text('Video non trovato', style: t.bodyMedium)),
      );
    }

    final related = ContentRepository.instance.videoCategories
        .expand((c) => c.videos)
        .where((v) => v.id != video.id)
        .take(4)
        .toList();

    return YoutubePlayerScaffold(
      controller: _controller!,
      aspectRatio: 16 / 9,
      builder: (context, player) => Scaffold(
        backgroundColor: AppColors.ink,
        appBar: AppBar(
          title: Text(video.title,
              style: t.titleLarge, overflow: TextOverflow.ellipsis),
          backgroundColor: AppColors.ink,
        ),
        body: InkBackground(
          accent: AppColors.cinnabar,
          child: ListView(
            padding: const EdgeInsets.fromLTRB(20, 8, 20, 36),
            children: [
              ClipRRect(
                borderRadius: BorderRadius.circular(14),
                child: player,
              ),
              const SizedBox(height: 20),
              Text(video.title, style: t.headlineSmall),
              const SizedBox(height: 6),
              Row(
                children: [
                  const Icon(Icons.person_outline,
                      size: 14, color: AppColors.cinnabar),
                  const SizedBox(width: 6),
                  Expanded(
                    child: Text(video.author,
                        style: t.bodySmall
                            ?.copyWith(color: AppColors.cinnabar)),
                  ),
                ],
              ),
              if (video.note.isNotEmpty) ...[
                const SizedBox(height: 16),
                Text(video.note, style: t.bodyLarge),
              ],
              const SizedBox(height: 22),
              OutlinedButton.icon(
                onPressed: () => launchUrl(Uri.parse(video.watchUrl),
                    mode: LaunchMode.externalApplication),
                icon: const Icon(Icons.open_in_new, size: 16),
                label: const Text('Apri su YouTube'),
                style: OutlinedButton.styleFrom(
                  foregroundColor: AppColors.textMuted,
                  side: const BorderSide(color: AppColors.line),
                  padding: const EdgeInsets.symmetric(vertical: 14),
                  shape: RoundedRectangleBorder(
                      borderRadius: BorderRadius.circular(999)),
                ),
              ),
              if (related.isNotEmpty) ...[
                const SizedBox(height: 30),
                const BrushDivider(),
                const SizedBox(height: 22),
                const Eyebrow('Continua a guardare'),
                const SizedBox(height: 12),
                for (final v in related)
                  Padding(
                    padding: const EdgeInsets.only(bottom: 10),
                    child: VideoTileLink(video: v),
                  ),
              ],
            ],
          ),
        ),
      ),
    );
  }
}

/// Come [VideoTile] ma sostituisce la schermata corrente invece di impilarla:
/// passando da un video all'altro non si accumula una pila di player.
class VideoTileLink extends StatelessWidget {
  const VideoTileLink({super.key, required this.video});

  final VideoItem video;

  @override
  Widget build(BuildContext context) {
    final t = Theme.of(context).textTheme;
    return Panel(
      padding: const EdgeInsets.fromLTRB(14, 12, 12, 12),
      onTap: () => Navigator.of(context).pushReplacement(
        MaterialPageRoute<void>(builder: (_) => VideoScreen(id: video.id)),
      ),
      child: Row(
        children: [
          const Icon(Icons.play_circle_outline,
              size: 18, color: AppColors.cinnabar),
          const SizedBox(width: 13),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(video.title,
                    style: t.titleMedium,
                    maxLines: 2,
                    overflow: TextOverflow.ellipsis),
                const SizedBox(height: 2),
                Text(video.author,
                    style: t.bodySmall?.copyWith(fontSize: 11.5)),
              ],
            ),
          ),
        ],
      ),
    );
  }
}
