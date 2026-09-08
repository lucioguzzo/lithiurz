import 'dart:math' as math;

import 'package:flutter/material.dart';

import '../theme.dart';

/// Comparsa in dissolvenza con leggera salita, sfalsata per indice.
/// Serve a dare ritmo alle liste senza che l'animazione diventi un'attesa.
class Reveal extends StatefulWidget {
  const Reveal({
    super.key,
    required this.child,
    this.index = 0,
    this.offset = 18,
    this.duration = const Duration(milliseconds: 420),
  });

  final Widget child;
  final int index;
  final double offset;
  final Duration duration;

  @override
  State<Reveal> createState() => _RevealState();
}

class _RevealState extends State<Reveal> with SingleTickerProviderStateMixin {
  late final AnimationController _c = AnimationController(
    vsync: this,
    duration: widget.duration,
  );

  @override
  void initState() {
    super.initState();
    final delay = Duration(milliseconds: math.min(widget.index, 12) * 55);
    Future<void>.delayed(delay, () {
      if (mounted) _c.forward();
    });
  }

  @override
  void dispose() {
    _c.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final curve = CurvedAnimation(parent: _c, curve: Curves.easeOutCubic);
    return AnimatedBuilder(
      animation: curve,
      builder: (context, child) => Opacity(
        opacity: curve.value,
        child: Transform.translate(
          offset: Offset(0, widget.offset * (1 - curve.value)),
          child: child,
        ),
      ),
      child: widget.child,
    );
  }
}

/// Sfondo dell'app: un gradiente profondo con un alone tenue del colore
/// della sezione, che da' identita' a ogni schermata senza pesare.
class InkBackground extends StatelessWidget {
  const InkBackground({super.key, required this.child, this.accent});

  final Widget child;
  final Color? accent;

  @override
  Widget build(BuildContext context) {
    final a = accent ?? AppColors.gold;
    return DecoratedBox(
      decoration: BoxDecoration(
        gradient: LinearGradient(
          begin: Alignment.topCenter,
          end: Alignment.bottomCenter,
          colors: [
            Color.alphaBlend(a.withValues(alpha: 0.10), AppColors.ink),
            AppColors.ink,
            AppColors.ink,
          ],
          stops: const [0.0, 0.42, 1.0],
        ),
      ),
      child: child,
    );
  }
}

/// Filetto decorativo che separa i blocchi di testo, con un rombo al centro.
class BrushDivider extends StatelessWidget {
  const BrushDivider({super.key, this.color, this.width = 120});

  final Color? color;
  final double width;

  @override
  Widget build(BuildContext context) {
    final c = color ?? AppColors.goldSoft;
    return SizedBox(
      height: 18,
      child: Center(
        child: Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            Container(
              width: width,
              height: 1,
              decoration: BoxDecoration(
                gradient: LinearGradient(
                  colors: [c.withValues(alpha: 0), c.withValues(alpha: 0.75)],
                ),
              ),
            ),
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: 8),
              child: Transform.rotate(
                angle: math.pi / 4,
                child: Container(width: 5, height: 5, color: c),
              ),
            ),
            Container(
              width: width,
              height: 1,
              decoration: BoxDecoration(
                gradient: LinearGradient(
                  colors: [c.withValues(alpha: 0.75), c.withValues(alpha: 0)],
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}

/// Etichetta piccola in maiuscoletto spaziato, usata per i cappelli di sezione.
class Eyebrow extends StatelessWidget {
  const Eyebrow(this.text, {super.key, this.color});

  final String text;
  final Color? color;

  @override
  Widget build(BuildContext context) => Text(
        text.toUpperCase(),
        style: Theme.of(context)
            .textTheme
            .labelMedium
            ?.copyWith(color: color ?? AppColors.textFaint),
      );
}

/// Riquadro con bordo sottile su cui poggiano quasi tutte le schede dell'app.
class Panel extends StatelessWidget {
  const Panel({
    super.key,
    required this.child,
    this.padding = const EdgeInsets.all(18),
    this.accent,
    this.onTap,
  });

  final Widget child;
  final EdgeInsetsGeometry padding;
  final Color? accent;
  final VoidCallback? onTap;

  @override
  Widget build(BuildContext context) {
    final a = accent ?? AppColors.line;
    final content = Container(
      padding: padding,
      decoration: BoxDecoration(
        color: AppColors.surface,
        borderRadius: BorderRadius.circular(16),
        border: Border.all(color: a.withValues(alpha: 0.42)),
      ),
      child: child,
    );
    if (onTap == null) return content;
    return Material(
      color: Colors.transparent,
      child: InkWell(
        onTap: onTap,
        borderRadius: BorderRadius.circular(16),
        child: content,
      ),
    );
  }
}

/// Pastiglia con un'icona e un testo breve (livello, lignaggio, durata).
class Pill extends StatelessWidget {
  const Pill(this.label, {super.key, this.icon, this.color});

  final String label;
  final IconData? icon;
  final Color? color;

  @override
  Widget build(BuildContext context) {
    final c = color ?? AppColors.textFaint;
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 5),
      decoration: BoxDecoration(
        color: c.withValues(alpha: 0.10),
        borderRadius: BorderRadius.circular(999),
        border: Border.all(color: c.withValues(alpha: 0.30)),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          if (icon != null) ...[
            Icon(icon, size: 12, color: c),
            const SizedBox(width: 5),
          ],
          Text(label,
              style: TextStyle(
                  fontFamily: AppText.body,
                  fontSize: 11.5,
                  fontWeight: FontWeight.w600,
                  color: c)),
        ],
      ),
    );
  }
}

/// Paragrafo di testo lungo, con la spaziatura usata in tutta l'app.
class Paragraphs extends StatelessWidget {
  const Paragraphs(this.texts, {super.key});

  final List<String> texts;

  @override
  Widget build(BuildContext context) {
    final style = Theme.of(context).textTheme.bodyLarge;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        for (var i = 0; i < texts.length; i++)
          Padding(
            padding: EdgeInsets.only(bottom: i == texts.length - 1 ? 0 : 16),
            child: Text(texts[i], style: style),
          ),
      ],
    );
  }
}

/// Elenco puntato dei punti chiave, con il rombo dorato come segno.
class KeyPoints extends StatelessWidget {
  const KeyPoints(this.points, {super.key, this.color});

  final List<String> points;
  final Color? color;

  @override
  Widget build(BuildContext context) {
    final c = color ?? AppColors.gold;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        for (final p in points)
          Padding(
            padding: const EdgeInsets.only(bottom: 11),
            child: Row(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Padding(
                  padding: const EdgeInsets.only(top: 7, right: 12),
                  child: Transform.rotate(
                    angle: math.pi / 4,
                    child: Container(width: 5, height: 5, color: c),
                  ),
                ),
                Expanded(
                  child: Text(p,
                      style: Theme.of(context)
                          .textTheme
                          .bodyMedium
                          ?.copyWith(color: AppColors.text, height: 1.45)),
                ),
              ],
            ),
          ),
      ],
    );
  }
}

/// Barra di avanzamento sottile con etichetta.
class ProgressBar extends StatelessWidget {
  const ProgressBar({
    super.key,
    required this.done,
    required this.total,
    this.color,
    this.label,
  });

  final int done;
  final int total;
  final Color? color;
  final String? label;

  @override
  Widget build(BuildContext context) {
    final c = color ?? AppColors.gold;
    final ratio = total == 0 ? 0.0 : done / total;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        if (label != null) ...[
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Eyebrow(label!),
              Text('$done / $total',
                  style: Theme.of(context)
                      .textTheme
                      .labelMedium
                      ?.copyWith(color: c)),
            ],
          ),
          const SizedBox(height: 8),
        ],
        ClipRRect(
          borderRadius: BorderRadius.circular(999),
          child: TweenAnimationBuilder<double>(
            tween: Tween(begin: 0, end: ratio),
            duration: const Duration(milliseconds: 700),
            curve: Curves.easeOutCubic,
            builder: (context, v, _) => LinearProgressIndicator(
              value: v,
              minHeight: 3,
              backgroundColor: AppColors.line,
              valueColor: AlwaysStoppedAnimation(c),
            ),
          ),
        ),
      ],
    );
  }
}

/// Intestazione comune a tutte le sezioni: titolo grande che si ritira
/// scorrendo, con il carattere cinese in filigrana.
class SectionAppBar extends StatelessWidget {
  const SectionAppBar({
    super.key,
    required this.title,
    required this.chinese,
    required this.accent,
    this.subtitle,
  });

  final String title;
  final String chinese;
  final Color accent;
  final String? subtitle;

  @override
  Widget build(BuildContext context) {
    final t = Theme.of(context).textTheme;
    return SliverAppBar.large(
      pinned: true,
      expandedHeight: 152,
      backgroundColor: AppColors.ink,
      surfaceTintColor: Colors.transparent,
      flexibleSpace: FlexibleSpaceBar(
        titlePadding: const EdgeInsets.fromLTRB(24, 0, 24, 16),
        title: Text(title,
            style: t.headlineSmall?.copyWith(fontSize: 21),
            maxLines: 1,
            overflow: TextOverflow.ellipsis),
        background: Stack(
          children: [
            Positioned(
              right: 18,
              bottom: 4,
              child: Text(chinese,
                  style: TextStyle(
                    fontFamily: AppText.cjk,
                    fontSize: 92,
                    height: 1,
                    fontWeight: FontWeight.w300,
                    color: accent.withValues(alpha: 0.13),
                  )),
            ),
            if (subtitle != null)
              Positioned(
                left: 24,
                right: 90,
                bottom: 62,
                child: Text(subtitle!,
                    style: t.bodySmall, maxLines: 2, overflow: TextOverflow.ellipsis),
              ),
          ],
        ),
      ),
    );
  }
}
