import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_3d_controller/flutter_3d_controller.dart';
import 'package:go_router/go_router.dart';
import 'package:provider/provider.dart';

import '../../core/theme.dart';
import '../../core/widgets/common.dart';
import '../../data/content_repository.dart';
import '../../data/models.dart';
import '../../data/progress_service.dart';

/// La lezione con il SiFu 3D.
///
/// Il modello e' un unico file glb che contiene tutte le lezioni come
/// animazioni con nome: cambiare lezione significa cambiare animazione, non
/// ricaricare il modello. Per questo il passaggio fra tecniche e' immediato.
class LessonScreen extends StatefulWidget {
  const LessonScreen({super.key, required this.id});

  final String id;

  @override
  State<LessonScreen> createState() => _LessonScreenState();
}

class _LessonScreenState extends State<LessonScreen> {
  final _controller = Flutter3DController();

  late Lesson _lesson;
  late int _index;

  bool _ready = false;
  bool _failed = false;
  Timer? _loadTimeout;
  bool _playing = true;
  double _loading = 0;
  _View _view = _View.lesson;

  List<Lesson> get _lessons => ContentRepository.instance.lessons;

  @override
  void initState() {
    super.initState();
    _index = _lessons.indexWhere((l) => l.id == widget.id);
    if (_index < 0) _index = 0;
    _lesson = _lessons[_index];
    ProgressService.instance.setLastLesson(_lesson.id);

    // Il visualizzatore 3D carica il modello in una WebView. Se qualcosa
    // glielo impedisce, il callback di completamento non arriva mai e senza
    // questo limite l'allievo resterebbe davanti a un'attesa infinita senza
    // sapere perche'. Meglio dirglielo e lasciargli la lezione scritta.
    _loadTimeout = Timer(const Duration(seconds: 20), () {
      if (mounted && !_ready) setState(() => _failed = true);
    });
  }

  @override
  void dispose() {
    _loadTimeout?.cancel();
    super.dispose();
  }

  /// Il controller 3D solleva un'eccezione se lo si comanda prima che il
  /// modello sia caricato. Puo' succedere per una ricostruzione del widget o
  /// per il ritorno da secondo piano: e' un caso di tempistica, non un errore
  /// che l'allievo debba vedere. Qui lo assorbiamo e teniamo la schermata viva.
  void _safe(VoidCallback action) {
    if (!_ready) return;
    try {
      action();
    } on Exception {
      if (mounted) setState(() => _ready = false);
    }
  }

  void _onModelLoaded() {
    if (!mounted) return;
    _loadTimeout?.cancel();
    setState(() {
      _ready = true;
      _failed = false;
    });
    _applyLesson(resetCamera: true);
    ProgressService.instance.markStudied(_lesson.id);
  }

  void _applyLesson({bool resetCamera = false}) {
    _safe(() => _controller.playAnimation(animationName: _lesson.id));
    _playing = true;
    if (resetCamera || _view == _View.lesson) {
      _applyView(_View.lesson);
    }
  }

  void _applyView(_View view) {
    final cam = _lesson.camera;
    // I raggi sono quelli misurati sul motore reale: model-viewer inquadra
    // con un campo visivo verticale di 30 gradi, e sotto i 3,6 metri la
    // figura esce dal riquadro.
    final (theta, phi, radius) = switch (view) {
      _View.lesson => (cam.theta, cam.phi, cam.radius),
      _View.front => (0.0, 82.0, 3.9),
      _View.side => (-86.0, 82.0, 3.9),
      _View.back => (180.0, 82.0, 3.9),
      _View.top => (-10.0, 56.0, 3.9),
    };
    _safe(() {
      _controller.setCameraTarget(0, cam.targetY, cam.targetZ);
      _controller.setCameraOrbit(theta, phi, radius);
    });
    if (mounted) setState(() => _view = view);
  }

  void _togglePlay() {
    _safe(() {
      if (_playing) {
        _controller.pauseAnimation();
      } else {
        _controller.playAnimation(animationName: _lesson.id);
      }
    });
    setState(() => _playing = !_playing);
  }

  void _go(int delta) {
    final next = (_index + delta) % _lessons.length;
    setState(() {
      _index = next < 0 ? _lessons.length - 1 : next;
      _lesson = _lessons[_index];
    });
    ProgressService.instance
      ..setLastLesson(_lesson.id)
      ..markStudied(_lesson.id);
    if (_ready) _applyLesson(resetCamera: true);
  }

  @override
  Widget build(BuildContext context) {
    final t = Theme.of(context).textTheme;
    final studied = context.watch<ProgressService>().isStudied(_lesson.id);

    return Scaffold(
      backgroundColor: AppColors.ink,
      body: InkBackground(
        accent: AppColors.gold,
        child: SafeArea(
          child: Column(
            children: [
              _TopBar(
                title: _lesson.title,
                studied: studied,
                onToggle: () =>
                    ProgressService.instance.toggleStudied(_lesson.id),
              ),
              Expanded(
                child: SingleChildScrollView(
                  padding: const EdgeInsets.fromLTRB(20, 4, 20, 32),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      _Stage(
                        controller: _controller,
                        thumbnail: _lesson.thumbnail,
                        ready: _ready,
                        failed: _failed,
                        loading: _loading,
                        onLoad: (_) => _onModelLoaded(),
                        onError: (e) {
                          _loadTimeout?.cancel();
                          if (mounted) setState(() => _failed = true);
                        },
                        onProgress: (v) {
                          if (mounted && !_ready) setState(() => _loading = v);
                        },
                      ),
                      const SizedBox(height: 12),
                      _Controls(
                        playing: _playing,
                        view: _view,
                        enabled: _ready,
                        onPlay: _togglePlay,
                        onView: _applyView,
                        onPrev: () => _go(-1),
                        onNext: () => _go(1),
                      ),
                      const SizedBox(height: 22),
                      Text(_lesson.title, style: t.displaySmall),
                      const SizedBox(height: 10),
                      Text(_lesson.description,
                          style: t.bodyLarge?.copyWith(height: 1.62)),
                      const SizedBox(height: 20),
                      const BrushDivider(),
                      const SizedBox(height: 20),
                      _Related(lessonId: _lesson.id),
                      const SizedBox(height: 24),
                      _Neighbours(
                        prev: _lessons[
                            (_index - 1 + _lessons.length) % _lessons.length],
                        next: _lessons[(_index + 1) % _lessons.length],
                        onPrev: () => _go(-1),
                        onNext: () => _go(1),
                      ),
                    ],
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

enum _View { lesson, front, side, back, top }

extension on _View {
  String get label => switch (this) {
        _View.lesson => 'Lezione',
        _View.front => 'Fronte',
        _View.side => 'Lato',
        _View.back => 'Retro',
        _View.top => 'Alto',
      };

  IconData get icon => switch (this) {
        _View.lesson => Icons.auto_awesome_outlined,
        _View.front => Icons.person_outline,
        _View.side => Icons.swap_horiz,
        _View.back => Icons.flip_camera_android_outlined,
        _View.top => Icons.expand_more,
      };
}

class _TopBar extends StatelessWidget {
  const _TopBar({
    required this.title,
    required this.studied,
    required this.onToggle,
  });

  final String title;
  final bool studied;
  final VoidCallback onToggle;

  @override
  Widget build(BuildContext context) => Padding(
        padding: const EdgeInsets.fromLTRB(6, 4, 8, 0),
        child: Row(
          children: [
            IconButton(
              onPressed: () => context.pop(),
              icon: const Icon(Icons.arrow_back_rounded),
            ),
            Expanded(
              child: Text(title,
                  style: Theme.of(context).textTheme.titleLarge,
                  overflow: TextOverflow.ellipsis),
            ),
            IconButton(
              tooltip: studied ? 'Segna da rivedere' : 'Segna come vista',
              onPressed: onToggle,
              icon: Icon(
                studied ? Icons.check_circle : Icons.check_circle_outline,
                color: studied ? AppColors.gold : AppColors.textFaint,
              ),
            ),
          ],
        ),
      );
}

/// Il palco: il visualizzatore 3D con lo stato di caricamento ed errore.
class _Stage extends StatelessWidget {
  const _Stage({
    required this.controller,
    required this.thumbnail,
    required this.ready,
    required this.failed,
    required this.loading,
    required this.onLoad,
    required this.onError,
    required this.onProgress,
  });

  final Flutter3DController controller;
  final String thumbnail;
  final bool ready;
  final bool failed;
  final double loading;
  final void Function(String) onLoad;
  final void Function(String) onError;
  final void Function(double) onProgress;

  @override
  Widget build(BuildContext context) {
    final t = Theme.of(context).textTheme;
    return AspectRatio(
      aspectRatio: 0.86,
      child: Container(
        clipBehavior: Clip.antiAlias,
        decoration: BoxDecoration(
          borderRadius: BorderRadius.circular(20),
          border: Border.all(color: AppColors.gold.withValues(alpha: 0.30)),
          gradient: const LinearGradient(
            begin: Alignment.topCenter,
            end: Alignment.bottomCenter,
            colors: [AppColors.surfaceHigh, AppColors.surface],
          ),
        ),
        child: Stack(
          fit: StackFit.expand,
          children: [
            if (!failed)
              Flutter3DViewer(
                controller: controller,
                src: 'assets/models/sifu.glb',
                activeGestureInterceptor: true,
                enableTouch: true,
                onLoad: onLoad,
                onError: onError,
                onProgress: onProgress,
              ),
            if (failed) ...[
              // Ripiego: la posizione finale della tecnica resta visibile
              // anche quando il visualizzatore 3D non parte.
              Opacity(
                opacity: 0.5,
                child: Image.asset(thumbnail, fit: BoxFit.contain),
              ),
              Positioned.fill(
                child: DecoratedBox(
                  decoration: BoxDecoration(
                    gradient: LinearGradient(
                      begin: Alignment.topCenter,
                      end: Alignment.bottomCenter,
                      colors: [
                        AppColors.surface.withValues(alpha: 0.25),
                        AppColors.surface.withValues(alpha: 0.92),
                      ],
                    ),
                  ),
                ),
              ),
              Align(
                alignment: Alignment.bottomCenter,
                child: Padding(
                  padding: const EdgeInsets.all(22),
                  child: Column(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      const Icon(Icons.view_in_ar_outlined,
                          size: 28, color: AppColors.textFaint),
                      const SizedBox(height: 12),
                      Text(
                        'Non riesco a mostrare il SiFu su questo dispositivo.\n'
                        'La descrizione della tecnica resta disponibile qui '
                        'sotto, e la miniatura mostra la posizione finale.',
                        textAlign: TextAlign.center,
                        style: t.bodySmall,
                      ),
                    ],
                  ),
                ),
              ),
            ] else if (!ready)
              ColoredBox(
                color: AppColors.surface,
                child: Center(
                  child: Column(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      Text('詠春',
                          style: TextStyle(
                            fontFamily: AppText.cjk,
                            fontSize: 40,
                            color: AppColors.gold.withValues(alpha: 0.35),
                            letterSpacing: 6,
                          )),
                      const SizedBox(height: 18),
                      SizedBox(
                        width: 120,
                        child: ClipRRect(
                          borderRadius: BorderRadius.circular(999),
                          child: LinearProgressIndicator(
                            value: loading > 0 && loading < 1 ? loading : null,
                            minHeight: 2,
                            backgroundColor: AppColors.line,
                            valueColor: const AlwaysStoppedAnimation(
                                AppColors.gold),
                          ),
                        ),
                      ),
                      const SizedBox(height: 12),
                      Text('Il SiFu si prepara…', style: t.bodySmall),
                    ],
                  ),
                ),
              ),
            if (ready)
              Positioned(
                left: 12,
                bottom: 10,
                child: Row(
                  children: [
                    const Icon(Icons.touch_app_outlined,
                        size: 13, color: AppColors.textFaint),
                    const SizedBox(width: 6),
                    Text('trascina per ruotare · pizzica per lo zoom',
                        style: t.bodySmall?.copyWith(fontSize: 10.5)),
                  ],
                ),
              ),
          ],
        ),
      ),
    );
  }
}

class _Controls extends StatelessWidget {
  const _Controls({
    required this.playing,
    required this.view,
    required this.enabled,
    required this.onPlay,
    required this.onView,
    required this.onPrev,
    required this.onNext,
  });

  final bool playing;
  final _View view;
  final bool enabled;
  final VoidCallback onPlay;
  final void Function(_View) onView;
  final VoidCallback onPrev;
  final VoidCallback onNext;

  @override
  Widget build(BuildContext context) {
    return Column(
      children: [
        Row(
          children: [
            _RoundButton(
                icon: Icons.skip_previous_rounded,
                onTap: enabled ? onPrev : null),
            const SizedBox(width: 10),
            Expanded(
              child: Material(
                color: Colors.transparent,
                child: InkWell(
                  onTap: enabled ? onPlay : null,
                  borderRadius: BorderRadius.circular(999),
                  child: Container(
                    padding: const EdgeInsets.symmetric(vertical: 13),
                    decoration: BoxDecoration(
                      borderRadius: BorderRadius.circular(999),
                      color: AppColors.gold.withValues(alpha: enabled ? 1 : 0.3),
                    ),
                    child: Row(
                      mainAxisAlignment: MainAxisAlignment.center,
                      children: [
                        Icon(
                            playing
                                ? Icons.pause_rounded
                                : Icons.play_arrow_rounded,
                            size: 20,
                            color: AppColors.ink),
                        const SizedBox(width: 7),
                        Text(playing ? 'In esecuzione' : 'Riprendi',
                            style: Theme.of(context)
                                .textTheme
                                .titleMedium
                                ?.copyWith(
                                    color: AppColors.ink,
                                    fontWeight: FontWeight.w700)),
                      ],
                    ),
                  ),
                ),
              ),
            ),
            const SizedBox(width: 10),
            _RoundButton(
                icon: Icons.skip_next_rounded, onTap: enabled ? onNext : null),
          ],
        ),
        const SizedBox(height: 12),
        SingleChildScrollView(
          scrollDirection: Axis.horizontal,
          child: Row(
            children: [
              for (final v in _View.values) ...[
                _ViewChip(
                  view: v,
                  selected: v == view,
                  onTap: enabled ? () => onView(v) : null,
                ),
                const SizedBox(width: 8),
              ],
            ],
          ),
        ),
      ],
    );
  }
}

class _RoundButton extends StatelessWidget {
  const _RoundButton({required this.icon, this.onTap});

  final IconData icon;
  final VoidCallback? onTap;

  @override
  Widget build(BuildContext context) => Material(
        color: AppColors.surfaceHigh,
        borderRadius: BorderRadius.circular(999),
        child: InkWell(
          onTap: onTap,
          borderRadius: BorderRadius.circular(999),
          child: Padding(
            padding: const EdgeInsets.all(12),
            child: Icon(icon,
                size: 20,
                color: onTap == null ? AppColors.textFaint : AppColors.text),
          ),
        ),
      );
}

class _ViewChip extends StatelessWidget {
  const _ViewChip({
    required this.view,
    required this.selected,
    required this.onTap,
  });

  final _View view;
  final bool selected;
  final VoidCallback? onTap;

  @override
  Widget build(BuildContext context) {
    final c = selected ? AppColors.gold : AppColors.textFaint;
    return Material(
      color: Colors.transparent,
      child: InkWell(
        onTap: onTap,
        borderRadius: BorderRadius.circular(999),
        child: AnimatedContainer(
          duration: const Duration(milliseconds: 200),
          padding: const EdgeInsets.symmetric(horizontal: 13, vertical: 8),
          decoration: BoxDecoration(
            borderRadius: BorderRadius.circular(999),
            color: c.withValues(alpha: selected ? 0.16 : 0.06),
            border: Border.all(color: c.withValues(alpha: selected ? 0.6 : 0.22)),
          ),
          child: Row(
            mainAxisSize: MainAxisSize.min,
            children: [
              Icon(view.icon, size: 13, color: c),
              const SizedBox(width: 6),
              Text(view.label,
                  style: TextStyle(
                      fontFamily: AppText.body,
                      fontSize: 12,
                      fontWeight: FontWeight.w600,
                      color: selected ? AppColors.gold : AppColors.textMuted)),
            ],
          ),
        ),
      ),
    );
  }
}

/// Rimandi dalla lezione alla teoria: il principio e le forme che la contengono.
class _Related extends StatelessWidget {
  const _Related({required this.lessonId});

  final String lessonId;

  @override
  Widget build(BuildContext context) {
    final repo = ContentRepository.instance;
    final t = Theme.of(context).textTheme;
    final principle = repo.allPrinciples
        .where((p) => p.lessonId == lessonId)
        .firstOrNull;
    final forms =
        repo.forms.where((f) => f.lessonIds.contains(lessonId)).toList();

    if (principle == null && forms.isEmpty) return const SizedBox.shrink();

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        const Eyebrow('Approfondisci'),
        const SizedBox(height: 12),
        if (principle != null)
          Padding(
            padding: const EdgeInsets.only(bottom: 10),
            child: Panel(
              accent: AppColors.jade,
              padding: const EdgeInsets.fromLTRB(15, 13, 12, 13),
              onTap: () => context.push('/principi/${principle.id}'),
              child: Row(
                children: [
                  const Icon(Icons.hub_outlined,
                      size: 17, color: AppColors.jade),
                  const SizedBox(width: 12),
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text('Il principio', style: t.bodySmall),
                        Text(principle.name, style: t.titleMedium),
                      ],
                    ),
                  ),
                  const Icon(Icons.chevron_right_rounded,
                      size: 19, color: AppColors.textFaint),
                ],
              ),
            ),
          ),
        for (final f in forms)
          Padding(
            padding: const EdgeInsets.only(bottom: 10),
            child: Panel(
              accent: const Color(0xFF7C7FC4),
              padding: const EdgeInsets.fromLTRB(15, 13, 12, 13),
              onTap: () => context.push('/forme/${f.id}'),
              child: Row(
                children: [
                  const Icon(Icons.view_timeline_outlined,
                      size: 17, color: Color(0xFF7C7FC4)),
                  const SizedBox(width: 12),
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text('Compare nella forma', style: t.bodySmall),
                        Text(f.name, style: t.titleMedium),
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
    );
  }
}

class _Neighbours extends StatelessWidget {
  const _Neighbours({
    required this.prev,
    required this.next,
    required this.onPrev,
    required this.onNext,
  });

  final Lesson prev;
  final Lesson next;
  final VoidCallback onPrev;
  final VoidCallback onNext;

  @override
  Widget build(BuildContext context) {
    final t = Theme.of(context).textTheme;
    Widget side(Lesson l, bool isPrev, VoidCallback onTap) => Expanded(
          child: Panel(
            padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 13),
            onTap: onTap,
            child: Column(
              crossAxisAlignment:
                  isPrev ? CrossAxisAlignment.start : CrossAxisAlignment.end,
              children: [
                Row(
                  mainAxisAlignment:
                      isPrev ? MainAxisAlignment.start : MainAxisAlignment.end,
                  children: [
                    if (isPrev)
                      const Icon(Icons.chevron_left_rounded,
                          size: 15, color: AppColors.textFaint),
                    Text(isPrev ? 'Precedente' : 'Successiva',
                        style: t.bodySmall?.copyWith(fontSize: 11)),
                    if (!isPrev)
                      const Icon(Icons.chevron_right_rounded,
                          size: 15, color: AppColors.textFaint),
                  ],
                ),
                const SizedBox(height: 2),
                Text(l.title,
                    style: t.titleMedium,
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis),
              ],
            ),
          ),
        );

    return Row(
      children: [
        side(prev, true, onPrev),
        const SizedBox(width: 10),
        side(next, false, onNext),
      ],
    );
  }
}
