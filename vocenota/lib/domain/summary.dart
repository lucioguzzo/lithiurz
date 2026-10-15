import 'dart:convert';

import '../core/constants.dart';

/// Attività da svolgere estratta dall'audio.
class ActionItem {
  const ActionItem({required this.text, this.owner, this.due});

  final String text;
  final String? owner;
  final String? due;

  factory ActionItem.fromJson(Map<String, dynamic> json) => ActionItem(
        text: (json['text'] ?? '').toString(),
        owner: (json['owner'] as String?)?.trim().isEmpty ?? true
            ? null
            : json['owner'] as String?,
        due: (json['due'] as String?)?.trim().isEmpty ?? true
            ? null
            : json['due'] as String?,
      );

  Map<String, dynamic> toJson() => <String, dynamic>{
        'text': text,
        if (owner != null) 'owner': owner,
        if (due != null) 'due': due,
      };

  /// Riga leggibile: `Chiamare il fornitore — Luca (entro venerdì)`.
  String get display {
    final StringBuffer b = StringBuffer(text);
    if (owner != null) b.write(' — $owner');
    if (due != null) b.write(' (${due!})');
    return b.toString();
  }
}

/// Risultato completo della fase di sintesi.
class SummaryBundle {
  const SummaryBundle({
    required this.style,
    required this.text,
    required this.keyPoints,
    required this.actions,
    required this.engine,
    required this.generatedAt,
    this.provider,
    this.model,
  });

  final SummaryStyle style;
  final String text;
  final List<String> keyPoints;
  final List<ActionItem> actions;
  final SummaryEngine engine;
  final DateTime generatedAt;
  final String? provider;
  final String? model;

  bool get isEmpty =>
      text.trim().isEmpty && keyPoints.isEmpty && actions.isEmpty;

  factory SummaryBundle.fromJson(Map<String, dynamic> json) => SummaryBundle(
        style: SummaryStyle.fromId(json['style'] as String?),
        text: (json['summary'] ?? json['text'] ?? '').toString(),
        keyPoints: <String>[
          for (final Object? p in (json['key_points'] as List<Object?>? ??
              json['keyPoints'] as List<Object?>? ??
              const <Object?>[]))
            p.toString(),
        ],
        actions: <ActionItem>[
          for (final Object? a
              in (json['actions'] as List<Object?>? ?? const <Object?>[]))
            if (a is Map<String, dynamic>)
              ActionItem.fromJson(a)
            else
              ActionItem(text: a.toString()),
        ],
        engine: SummaryEngine.fromId(json['engine'] as String?),
        generatedAt:
            DateTime.tryParse(json['generated_at']?.toString() ?? '') ??
                DateTime.now(),
        provider: json['provider'] as String?,
        model: json['model'] as String?,
      );

  Map<String, dynamic> toJson() => <String, dynamic>{
        'style': style.id,
        'summary': text,
        'key_points': keyPoints,
        'actions': <Map<String, dynamic>>[
          for (final ActionItem a in actions) a.toJson(),
        ],
        'engine': engine.id,
        'generated_at': generatedAt.toIso8601String(),
        if (provider != null) 'provider': provider,
        if (model != null) 'model': model,
      };

  String encode() => jsonEncode(toJson());

  static SummaryBundle? decode(String? raw) {
    if (raw == null || raw.trim().isEmpty) return null;
    try {
      final Object? decoded = jsonDecode(raw);
      if (decoded is Map<String, dynamic>) return SummaryBundle.fromJson(decoded);
    } on FormatException {
      return null;
    }
    return null;
  }

  /// Testo pronto per copia/condivisione/esportazione.
  String toPlainText({String? title, String? transcript}) {
    final StringBuffer b = StringBuffer();
    if (title != null && title.isNotEmpty) {
      b.writeln(title);
      b.writeln('=' * title.length);
      b.writeln();
    }
    if (text.trim().isNotEmpty) {
      b.writeln('RIASSUNTO');
      b.writeln(text.trim());
      b.writeln();
    }
    if (keyPoints.isNotEmpty) {
      b.writeln('PUNTI PRINCIPALI');
      for (final String p in keyPoints) {
        b.writeln('• $p');
      }
      b.writeln();
    }
    if (actions.isNotEmpty) {
      b.writeln('AZIONI DA SVOLGERE');
      for (final ActionItem a in actions) {
        b.writeln('☐ ${a.display}');
      }
      b.writeln();
    }
    if (transcript != null && transcript.trim().isNotEmpty) {
      b.writeln('TRASCRIZIONE');
      b.writeln(transcript.trim());
      b.writeln();
    }
    b.writeln('— Generato con ${AppInfo.name}');
    return b.toString();
  }

  /// Testo letto dalla sintesi vocale.
  String toSpeech() {
    final StringBuffer b = StringBuffer();
    if (text.trim().isNotEmpty) b.writeln(text.trim());
    if (keyPoints.isNotEmpty) {
      b.writeln('Punti principali.');
      for (final String p in keyPoints) {
        b.writeln('$p.');
      }
    }
    if (actions.isNotEmpty) {
      b.writeln('Azioni da svolgere.');
      for (final ActionItem a in actions) {
        b.writeln('${a.display}.');
      }
    }
    return b.toString().trim();
  }
}
