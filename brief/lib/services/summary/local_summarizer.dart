import '../../core/constants.dart';
import '../../domain/summary.dart';

/// Riassunto estrattivo calcolato interamente sul dispositivo.
///
/// Non sostituisce un modello linguistico: seleziona le frasi più
/// rappresentative (frequenza dei termini, posizione, lunghezza) e isola le
/// frasi che contengono segnali di azione. Serve come modalità "solo locale"
/// e come riserva quando il cloud non è disponibile.
class LocalSummarizer {
  const LocalSummarizer();

  static const Set<String> _stopwords = <String>{
    'a', 'ad', 'al', 'alla', 'alle', 'allo', 'agli', 'ai', 'anche', 'ancora',
    'avere', 'aveva', 'avevo', 'c', 'che', 'chi', 'ci', 'cioè', 'come', 'con',
    'cosa', 'cui', 'da', 'dal', 'dalla', 'dalle', 'dei', 'del', 'della',
    'delle', 'dello', 'degli', 'di', 'dove', 'due', 'e', 'ecco', 'ed', 'era',
    'essere', 'fa', 'fare', 'fatto', 'gli', 'ha', 'hai', 'hanno', 'ho', 'i',
    'il', 'in', 'io', 'la', 'le', 'lei', 'li', 'lo', 'loro', 'lui', 'ma',
    'me', 'mi', 'mia', 'mio', 'molto', 'ne', 'nei', 'nel', 'nella', 'nelle',
    'no', 'noi', 'non', 'nostro', 'o', 'ogni', 'oppure', 'per', 'perché',
    'però', 'più', 'poi', 'posso', 'può', 'quando', 'quella', 'quelle',
    'quello', 'questa', 'queste', 'questi', 'questo', 'qui', 'se', 'sei',
    'sì', 'sia', 'siamo', 'solo', 'sono', 'su', 'sua', 'sue', 'sui', 'sul',
    'sulla', 'suo', 'ti', 'tra', 'tu', 'tuo', 'tutto', 'tutti', 'un', 'una',
    'uno', 'va', 'vi', 'voi', 'già', 'allora', 'insomma', 'praticamente',
    'diciamo', 'niente', 'okay', 'ok', 'ecc',
  };

  /// Indizi di un'attività da svolgere.
  static const List<String> _actionCues = <String>[
    'bisogna', 'dobbiamo', 'devo', 'devi', 'dovete', 'ricordati', 'ricordare',
    'mi raccomando', 'per favore', 'puoi ', 'potresti', 'mandami', 'manda',
    'invia', 'inviami', 'chiama', 'chiamami', 'richiama', 'prenota',
    'conferma', 'verifica', 'controlla', 'prepara', 'scrivi', 'fissa',
    'entro ', 'scadenza', 'appuntamento', 'serve che', 'occorre',
    'ci vediamo', 'portare', 'porta ', 'comprare', 'compra ', 'pagare',
  ];

  SummaryBundle summarize(
    String transcript, {
    SummaryStyle style = SummaryStyle.standard,
  }) {
    final List<String> sentences = splitSentences(transcript);
    if (sentences.isEmpty) {
      return SummaryBundle(
        style: style,
        text: '',
        keyPoints: const <String>[],
        actions: const <ActionItem>[],
        engine: SummaryEngine.locale,
        generatedAt: DateTime.now(),
        provider: 'estrattivo-locale',
      );
    }

    final Map<String, int> freq = _frequencies(sentences);
    final List<_Scored> scored = <_Scored>[];
    for (int i = 0; i < sentences.length; i++) {
      scored.add(_Scored(i, sentences[i], _score(sentences[i], freq, i, sentences.length)));
    }

    final int wanted = switch (style) {
      SummaryStyle.breve => 2,
      SummaryStyle.standard => 4,
      SummaryStyle.dettagliato => 8,
    };

    final List<_Scored> best = _withoutNearDuplicates(
      List<_Scored>.of(scored)
        ..sort((_Scored a, _Scored b) => b.score.compareTo(a.score)),
      wanted.clamp(1, sentences.length),
    )..sort((_Scored a, _Scored b) => a.index.compareTo(b.index));

    // Una trascrizione con frasi ripetute produceva punti chiave tutti
    // uguali: si scartano i quasi-duplicati prima di scegliere.
    final List<_Scored> candidates = List<_Scored>.of(scored)
      ..sort((_Scored a, _Scored b) => b.score.compareTo(a.score));
    final List<_Scored> topPoints = _withoutNearDuplicates(
      candidates,
      style == SummaryStyle.dettagliato ? 8 : 5,
    )..sort((_Scored a, _Scored b) => a.index.compareTo(b.index));

    return SummaryBundle(
      style: style,
      text: best.map((_Scored s) => s.text).join(' '),
      keyPoints: <String>[
        for (final _Scored s in topPoints) _trimPoint(s.text),
      ],
      actions: extractActions(sentences),
      engine: SummaryEngine.locale,
      generatedAt: DateTime.now(),
      provider: 'estrattivo-locale',
    );
  }

  /// Divide in frasi tenendo conto della punteggiatura e delle pause lunghe.
  static List<String> splitSentences(String text) {
    final String normalized = text.replaceAll(RegExp(r'\s+'), ' ').trim();
    if (normalized.isEmpty) return const <String>[];

    final List<String> raw = normalized
        .split(RegExp(r'(?<=[.!?…])\s+'))
        .expand<String>((String s) =>
            s.length > 320 ? s.split(RegExp(r'(?<=,)\s+')) : <String>[s])
        .map((String s) => s.trim())
        .where((String s) => s.isNotEmpty)
        .toList();

    // Riaggrega i frammenti troppo corti con la frase precedente.
    final List<String> out = <String>[];
    for (final String s in raw) {
      if (out.isNotEmpty && s.split(' ').length < 3) {
        out[out.length - 1] = '${out.last} $s';
      } else {
        out.add(s);
      }
    }
    return out;
  }

  /// Frasi che contengono un impegno o una richiesta.
  static List<ActionItem> extractActions(List<String> sentences) {
    final List<ActionItem> actions = <ActionItem>[];
    for (final String s in sentences) {
      final String lower = s.toLowerCase();
      final bool hit = _actionCues.any(lower.contains);
      if (!hit) continue;
      final String text = _trimPoint(s);
      if (text.split(' ').length < 3) continue;
      if (actions.any((ActionItem a) => a.text == text)) continue;
      actions.add(ActionItem(text: text));
      if (actions.length >= 8) break;
    }
    return actions;
  }

  /// Prende al più [limit] frasi, saltando quelle troppo simili a una già
  /// scelta (stesse parole per almeno l'ottanta per cento).
  static List<_Scored> _withoutNearDuplicates(
    List<_Scored> ordered,
    int limit,
  ) {
    final List<_Scored> kept = <_Scored>[];
    final List<Set<String>> keptWords = <Set<String>>[];
    for (final _Scored candidate in ordered) {
      if (kept.length >= limit) break;
      final Set<String> words = _words(candidate.text).toSet();
      if (words.isEmpty) continue;
      final bool duplicate = keptWords.any((Set<String> other) {
        final int common = words.intersection(other).length;
        final int smallest = words.length < other.length ? words.length : other.length;
        return smallest > 0 && common / smallest >= 0.8;
      });
      if (duplicate) continue;
      kept.add(candidate);
      keptWords.add(words);
    }
    return kept;
  }

  static Map<String, int> _frequencies(List<String> sentences) {
    final Map<String, int> freq = <String, int>{};
    for (final String s in sentences) {
      for (final String w in _words(s)) {
        freq[w] = (freq[w] ?? 0) + 1;
      }
    }
    return freq;
  }

  static List<String> _words(String sentence) => sentence
      .toLowerCase()
      .split(RegExp(r"[^\w'àèéìòùç]+", unicode: true))
      .where((String w) => w.length > 2 && !_stopwords.contains(w))
      .toList();

  static double _score(
    String sentence,
    Map<String, int> freq,
    int index,
    int total,
  ) {
    final List<String> words = _words(sentence);
    if (words.isEmpty) return 0;

    double sum = 0;
    for (final String w in words) {
      sum += freq[w] ?? 0;
    }
    // Media per non premiare solo le frasi lunghe…
    double score = sum / words.length;
    // …con un bonus per quelle di lunghezza "informativa".
    final int n = words.length;
    if (n >= 6 && n <= 40) score *= 1.25;
    if (n < 4) score *= 0.5;
    // Apertura e chiusura di un messaggio vocale sono spesso rilevanti.
    if (index == 0 || index == total - 1) score *= 1.15;
    return score;
  }

  static String _trimPoint(String sentence) {
    String s = sentence.trim();
    s = s.replaceFirst(RegExp(r'^(e|ma|quindi|allora|poi|insomma|cioè)\s+',
        caseSensitive: false), '');
    if (s.isEmpty) return sentence.trim();
    final String first = s[0].toUpperCase();
    s = '$first${s.substring(1)}';
    if (s.length > 220) {
      final int cut = s.lastIndexOf(' ', 215);
      s = '${s.substring(0, cut > 60 ? cut : 215)}…';
    }
    return s;
  }
}

class _Scored {
  const _Scored(this.index, this.text, this.score);
  final int index;
  final String text;
  final double score;
}
