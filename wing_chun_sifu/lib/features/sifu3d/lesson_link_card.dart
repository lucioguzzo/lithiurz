import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

import '../../core/theme.dart';
import '../../core/widgets/common.dart';
import '../../data/models.dart';

/// Rimando a una lezione del SiFu 3D, usato dalle altre sezioni.
/// E' il collegamento che tiene insieme teoria e gesto: da un principio si
/// arriva sempre a vederlo eseguito.
class LessonLinkCard extends StatelessWidget {
  const LessonLinkCard({super.key, required this.lesson, this.dense = false});

  final Lesson lesson;
  final bool dense;

  @override
  Widget build(BuildContext context) {
    final t = Theme.of(context).textTheme;
    return Panel(
      accent: AppColors.gold,
      padding: const EdgeInsets.all(10),
      onTap: () => context.push('/sifu3d/${lesson.id}'),
      child: Row(
        children: [
          ClipRRect(
            borderRadius: BorderRadius.circular(10),
            child: Image.asset(
              lesson.thumbnail,
              width: dense ? 46 : 58,
              height: dense ? 56 : 72,
              fit: BoxFit.cover,
            ),
          ),
          const SizedBox(width: 14),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                const Eyebrow('Guarda il SiFu', color: AppColors.gold),
                const SizedBox(height: 4),
                Text(lesson.title, style: t.titleLarge),
                if (!dense) ...[
                  const SizedBox(height: 3),
                  Text('${lesson.duration.toStringAsFixed(0)} secondi in 3D',
                      style: t.bodySmall),
                ],
              ],
            ),
          ),
          const Padding(
            padding: EdgeInsets.only(right: 8),
            child: Icon(Icons.view_in_ar_outlined,
                color: AppColors.gold, size: 20),
          ),
        ],
      ),
    );
  }
}
