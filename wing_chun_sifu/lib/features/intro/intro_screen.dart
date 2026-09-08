import 'dart:math' as math;

import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

import '../../core/theme.dart';
import '../../core/widgets/common.dart';
import '../../data/progress_service.dart';

/// Introduzione all'app. Dura pochi secondi ed e' saltabile: un'intro che non
/// si puo' saltare diventa un ostacolo dalla seconda apertura in poi.
class IntroScreen extends StatefulWidget {
  const IntroScreen({super.key});

  @override
  State<IntroScreen> createState() => _IntroScreenState();
}

class _IntroScreenState extends State<IntroScreen>
    with TickerProviderStateMixin {
  late final AnimationController _c = AnimationController(
    vsync: this,
    duration: const Duration(milliseconds: 3200),
  )..forward();

  late final AnimationController _breath = AnimationController(
    vsync: this,
    duration: const Duration(seconds: 7),
  )..repeat(reverse: true);

  double _at(double start, double end) =>
      Curves.easeOutCubic.transform(
        ((_c.value - start) / (end - start)).clamp(0.0, 1.0),
      );

  @override
  void dispose() {
    _c.dispose();
    _breath.dispose();
    super.dispose();
  }

  void _enter() {
    ProgressService.instance.setIntroSeen();
    context.go('/menu');
  }

  @override
  Widget build(BuildContext context) {
    final t = Theme.of(context).textTheme;
    return Scaffold(
      body: InkBackground(
        child: Stack(
          children: [
            // alone che respira dietro ai caratteri
            Positioned.fill(
              child: AnimatedBuilder(
                animation: _breath,
                builder: (context, _) {
                  final v = 0.5 + 0.5 * math.sin(_breath.value * math.pi);
                  return DecoratedBox(
                    decoration: BoxDecoration(
                      gradient: RadialGradient(
                        center: const Alignment(0, -0.45),
                        radius: 0.85 + 0.12 * v,
                        colors: [
                          AppColors.gold.withValues(alpha: 0.11 + 0.05 * v),
                          Colors.transparent,
                        ],
                      ),
                    ),
                  );
                },
              ),
            ),
            SafeArea(
              child: AnimatedBuilder(
                animation: _c,
                builder: (context, _) {
                  final a1 = _at(0.00, 0.32); // caratteri
                  final a2 = _at(0.22, 0.52); // titolo
                  final a3 = _at(0.40, 0.70); // testo
                  final a4 = _at(0.62, 0.92); // pulsante
                  return Padding(
                    padding: const EdgeInsets.symmetric(horizontal: 28),
                    child: Column(
                      children: [
                        Align(
                          alignment: Alignment.topRight,
                          child: Opacity(
                            opacity: 0.55,
                            child: TextButton(
                              onPressed: _enter,
                              child: Text('Salta',
                                  style: t.labelLarge
                                      ?.copyWith(color: AppColors.textMuted)),
                            ),
                          ),
                        ),
                        const Spacer(flex: 3),
                        Opacity(
                          opacity: a1,
                          child: Transform.scale(
                            scale: 0.86 + 0.14 * a1,
                            child: Text(
                              '詠春',
                              style: TextStyle(
                                fontFamily: AppText.cjk,
                                fontSize: 74,
                                height: 1.0,
                                fontWeight: FontWeight.w300,
                                color: AppColors.gold
                                    .withValues(alpha: 0.55 + 0.45 * a1),
                                letterSpacing: 10,
                              ),
                            ),
                          ),
                        ),
                        const SizedBox(height: 22),
                        Opacity(opacity: a2, child: const BrushDivider()),
                        const SizedBox(height: 18),
                        Opacity(
                          opacity: a2,
                          child: Transform.translate(
                            offset: Offset(0, 14 * (1 - a2)),
                            child: Column(
                              children: [
                                Text('Wing Chun',
                                    textAlign: TextAlign.center,
                                    style: t.displayLarge),
                                Text('SiFu Online',
                                    textAlign: TextAlign.center,
                                    style: t.displayLarge
                                        ?.copyWith(color: AppColors.gold)),
                              ],
                            ),
                          ),
                        ),
                        const SizedBox(height: 26),
                        Opacity(
                          opacity: a3,
                          child: Transform.translate(
                            offset: Offset(0, 14 * (1 - a3)),
                            child: Text(
                              'Il Wing Chun non è una collezione di tecniche: '
                              'è un insieme di principi che generano tecniche.\n\n'
                              'Qui trovi la storia del sistema, i suoi principi, '
                              'le forme che li conservano e un SiFu in tre '
                              'dimensioni che te li mostra da ogni angolazione.',
                              textAlign: TextAlign.center,
                              style: t.bodyMedium?.copyWith(height: 1.72),
                            ),
                          ),
                        ),
                        const Spacer(flex: 4),
                        Opacity(
                          opacity: a4,
                          child: Transform.translate(
                            offset: Offset(0, 16 * (1 - a4)),
                            child: _EnterButton(onTap: _enter),
                          ),
                        ),
                        const SizedBox(height: 34),
                      ],
                    ),
                  );
                },
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _EnterButton extends StatelessWidget {
  const _EnterButton({required this.onTap});

  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    return Material(
      color: Colors.transparent,
      child: InkWell(
        onTap: onTap,
        borderRadius: BorderRadius.circular(999),
        child: Container(
          width: double.infinity,
          padding: const EdgeInsets.symmetric(vertical: 17),
          decoration: BoxDecoration(
            borderRadius: BorderRadius.circular(999),
            gradient: const LinearGradient(
              colors: [Color(0xFFE0BC6C), AppColors.gold],
            ),
            boxShadow: [
              BoxShadow(
                color: AppColors.gold.withValues(alpha: 0.22),
                blurRadius: 26,
                offset: const Offset(0, 8),
              ),
            ],
          ),
          child: Row(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              Text('Entra nella scuola',
                  style: Theme.of(context).textTheme.titleMedium?.copyWith(
                        color: AppColors.ink,
                        fontWeight: FontWeight.w700,
                        letterSpacing: 0.2,
                      )),
              const SizedBox(width: 8),
              const Icon(Icons.arrow_forward_rounded,
                  size: 18, color: AppColors.ink),
            ],
          ),
        ),
      ),
    );
  }
}
