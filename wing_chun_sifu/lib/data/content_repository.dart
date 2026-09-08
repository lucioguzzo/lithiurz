import 'dart:convert';

import 'package:flutter/services.dart';

import 'models.dart';

/// Carica i contenuti dagli asset una sola volta e li tiene in memoria.
///
/// I dati sono piccoli (poche decine di KB) e servono a tutte le sezioni:
/// caricarli una volta all'avvio evita sfarfallii nelle transizioni.
class ContentRepository {
  ContentRepository._();

  static final ContentRepository instance = ContentRepository._();

  late final String principlesIntro;
  late final List<PrincipleGroup> principleGroups;

  late final String formsIntro;
  late final Map<String, Lineage> lineages;
  late final List<WingChunForm> forms;

  late final List<Lesson> lessons;

  late final String videosIntro;
  late final List<Channel> channels;
  late final List<VideoCategory> videoCategories;

  late final String historyIntro;
  late final String historyStatus;
  late final String historyNotice;
  late final List<HistoryChapter> historyChapters;

  bool _loaded = false;

  Future<void> load() async {
    if (_loaded) return;

    Future<Map<String, dynamic>> obj(String path) async =>
        jsonDecode(await rootBundle.loadString(path)) as Map<String, dynamic>;

    final p = await obj('assets/data/principles.json');
    principlesIntro = p['intro'] as String? ?? '';
    principleGroups = (p['groups'] as List)
        .map((e) => PrincipleGroup.fromJson(e as Map<String, dynamic>))
        .toList(growable: false);

    final f = await obj('assets/data/forms.json');
    formsIntro = f['intro'] as String? ?? '';
    lineages = (f['lineages'] as Map<String, dynamic>).map((k, v) {
      final m = v as Map<String, dynamic>;
      return MapEntry(
          k, Lineage(id: k, label: m['label'] as String, note: m['note'] as String));
    });
    forms = (f['forms'] as List)
        .map((e) => WingChunForm.fromJson(e as Map<String, dynamic>))
        .toList(growable: false)
      ..sort((a, b) => a.order.compareTo(b.order));

    lessons = (jsonDecode(await rootBundle.loadString('assets/data/lessons.json'))
            as List)
        .map((e) => Lesson.fromJson(e as Map<String, dynamic>))
        .toList(growable: false);

    final v = await obj('assets/data/videos.json');
    videosIntro = v['intro'] as String? ?? '';
    channels = (v['channels'] as List)
        .map((e) => Channel.fromJson(e as Map<String, dynamic>))
        .toList(growable: false);
    videoCategories = (v['categories'] as List)
        .map((e) => VideoCategory.fromJson(e as Map<String, dynamic>))
        .toList(growable: false);

    final h = await obj('assets/data/history.json');
    historyIntro = h['intro'] as String? ?? '';
    historyStatus = h['status'] as String? ?? '';
    historyNotice = h['notice'] as String? ?? '';
    historyChapters = (h['chapters'] as List)
        .map((e) => HistoryChapter.fromJson(e as Map<String, dynamic>))
        .toList(growable: false);

    _loaded = true;
  }

  /// La lezione 3D con questo identificativo, se esiste.
  Lesson? lesson(String? id) {
    if (id == null) return null;
    for (final l in lessons) {
      if (l.id == id) return l;
    }
    return null;
  }

  Principle? principle(String id) {
    for (final g in principleGroups) {
      for (final p in g.items) {
        if (p.id == id) return p;
      }
    }
    return null;
  }

  WingChunForm? form(String id) {
    for (final f in forms) {
      if (f.id == id) return f;
    }
    return null;
  }

  VideoItem? video(String id) {
    for (final c in videoCategories) {
      for (final v in c.videos) {
        if (v.id == id) return v;
      }
    }
    return null;
  }

  /// Tutte le voci dei principi, in ordine di lettura.
  List<Principle> get allPrinciples =>
      [for (final g in principleGroups) ...g.items];

  int get totalLearnable => allPrinciples.length + forms.length + lessons.length;
}
