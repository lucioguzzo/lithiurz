import 'package:flutter/material.dart';

/// Modelli dei contenuti. Sono deliberatamente immutabili e costruiti da JSON:
/// tutti i testi dell'app vivono in assets/data e possono essere sostituiti
/// senza ricompilare la logica.

@immutable
class Principle {
  const Principle({
    required this.id,
    required this.name,
    required this.chinese,
    required this.summary,
    required this.body,
    required this.keyPoints,
    this.translation,
    this.lessonId,
    this.source,
  });

  final String id;
  final String name;
  final String chinese;
  final String summary;
  final List<String> body;
  final List<String> keyPoints;
  final String? translation;
  final String? lessonId;
  final String? source;

  factory Principle.fromJson(Map<String, dynamic> j) => Principle(
        id: j['id'] as String,
        name: j['name'] as String,
        chinese: j['chinese'] as String? ?? '',
        summary: j['summary'] as String? ?? '',
        body: (j['body'] as List? ?? const []).cast<String>(),
        keyPoints: (j['keyPoints'] as List? ?? const []).cast<String>(),
        translation: j['translation'] as String?,
        lessonId: j['lessonId'] as String?,
        source: j['source'] as String?,
      );
}

@immutable
class PrincipleGroup {
  const PrincipleGroup({
    required this.id,
    required this.title,
    required this.subtitle,
    required this.items,
  });

  final String id;
  final String title;
  final String subtitle;
  final List<Principle> items;

  factory PrincipleGroup.fromJson(Map<String, dynamic> j) => PrincipleGroup(
        id: j['id'] as String,
        title: j['title'] as String,
        subtitle: j['subtitle'] as String? ?? '',
        items: (j['items'] as List? ?? const [])
            .map((e) => Principle.fromJson(e as Map<String, dynamic>))
            .toList(growable: false),
      );
}

@immutable
class FormSection {
  const FormSection({required this.n, required this.title, required this.text});
  final int n;
  final String title;
  final String text;

  factory FormSection.fromJson(Map<String, dynamic> j) => FormSection(
        n: j['n'] as int,
        title: j['title'] as String,
        text: j['text'] as String,
      );
}

@immutable
class WingChunForm {
  const WingChunForm({
    required this.id,
    required this.name,
    required this.chinese,
    required this.translation,
    required this.lineage,
    required this.level,
    required this.order,
    required this.summary,
    required this.body,
    required this.sections,
    required this.develops,
    required this.lessonIds,
    required this.videoIds,
  });

  final String id;
  final String name;
  final String chinese;
  final String translation;
  final String lineage;
  final int level;
  final int order;
  final String summary;
  final List<String> body;
  final List<FormSection> sections;
  final List<String> develops;
  final List<String> lessonIds;
  final List<String> videoIds;

  factory WingChunForm.fromJson(Map<String, dynamic> j) => WingChunForm(
        id: j['id'] as String,
        name: j['name'] as String,
        chinese: j['chinese'] as String? ?? '',
        translation: j['translation'] as String? ?? '',
        lineage: j['lineage'] as String? ?? '',
        level: j['level'] as int? ?? 1,
        order: j['order'] as int? ?? 0,
        summary: j['summary'] as String? ?? '',
        body: (j['body'] as List? ?? const []).cast<String>(),
        sections: (j['sections'] as List? ?? const [])
            .map((e) => FormSection.fromJson(e as Map<String, dynamic>))
            .toList(growable: false),
        develops: (j['develops'] as List? ?? const []).cast<String>(),
        lessonIds: (j['lessonIds'] as List? ?? const []).cast<String>(),
        videoIds: (j['videoIds'] as List? ?? const []).cast<String>(),
      );
}

@immutable
class Lineage {
  const Lineage({required this.id, required this.label, required this.note});
  final String id;
  final String label;
  final String note;
}

@immutable
class LessonCamera {
  const LessonCamera({
    required this.theta,
    required this.phi,
    required this.radius,
    required this.targetY,
    required this.targetZ,
  });

  final double theta;
  final double phi;
  final double radius;
  final double targetY;
  final double targetZ;

  factory LessonCamera.fromJson(Map<String, dynamic> j) => LessonCamera(
        theta: (j['theta'] as num).toDouble(),
        phi: (j['phi'] as num).toDouble(),
        radius: (j['radius'] as num).toDouble(),
        targetY: (j['targetY'] as num).toDouble(),
        targetZ: (j['targetZ'] as num).toDouble(),
      );
}

@immutable
class Lesson {
  const Lesson({
    required this.id,
    required this.title,
    required this.description,
    required this.duration,
    required this.camera,
  });

  final String id;
  final String title;
  final String description;
  final double duration;
  final LessonCamera camera;

  String get thumbnail => 'assets/lessons/$id.png';

  factory Lesson.fromJson(Map<String, dynamic> j) => Lesson(
        id: j['id'] as String,
        title: j['title'] as String,
        description: j['description'] as String? ?? '',
        duration: (j['duration'] as num?)?.toDouble() ?? 0,
        camera: LessonCamera.fromJson(j['camera'] as Map<String, dynamic>),
      );
}

@immutable
class VideoItem {
  const VideoItem({
    required this.id,
    required this.title,
    required this.author,
    required this.note,
  });

  final String id;
  final String title;
  final String author;
  final String note;

  String get thumbnailUrl => 'https://i.ytimg.com/vi/$id/hqdefault.jpg';
  String get watchUrl => 'https://www.youtube.com/watch?v=$id';

  factory VideoItem.fromJson(Map<String, dynamic> j) => VideoItem(
        id: j['id'] as String,
        title: j['title'] as String,
        author: j['author'] as String? ?? '',
        note: j['note'] as String? ?? '',
      );
}

@immutable
class VideoCategory {
  const VideoCategory({
    required this.id,
    required this.title,
    required this.subtitle,
    required this.videos,
  });

  final String id;
  final String title;
  final String subtitle;
  final List<VideoItem> videos;

  factory VideoCategory.fromJson(Map<String, dynamic> j) => VideoCategory(
        id: j['id'] as String,
        title: j['title'] as String,
        subtitle: j['subtitle'] as String? ?? '',
        videos: (j['videos'] as List? ?? const [])
            .map((e) => VideoItem.fromJson(e as Map<String, dynamic>))
            .toList(growable: false),
      );
}

@immutable
class Channel {
  const Channel({
    required this.name,
    required this.who,
    required this.url,
    required this.note,
  });

  final String name;
  final String who;
  final String url;
  final String note;

  factory Channel.fromJson(Map<String, dynamic> j) => Channel(
        name: j['name'] as String,
        who: j['who'] as String? ?? '',
        url: j['url'] as String,
        note: j['note'] as String? ?? '',
      );
}

@immutable
class HistoryChapter {
  const HistoryChapter({
    required this.id,
    required this.title,
    required this.era,
    required this.summary,
    required this.body,
    required this.sources,
  });

  final String id;
  final String title;
  final String era;
  final String summary;
  final List<String> body;
  final List<String> sources;

  factory HistoryChapter.fromJson(Map<String, dynamic> j) => HistoryChapter(
        id: j['id'] as String,
        title: j['title'] as String,
        era: j['era'] as String? ?? '',
        summary: j['summary'] as String? ?? '',
        body: (j['body'] as List? ?? const []).cast<String>(),
        sources: (j['sources'] as List? ?? const []).cast<String>(),
      );
}
