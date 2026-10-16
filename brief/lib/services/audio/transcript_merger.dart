/// Unisce i testi dei singoli blocchi in una trascrizione continua.
///
/// whisper.cpp può ripetere l'ultima frase all'inizio del blocco successivo
/// (il contesto condiziona la finestra seguente): la sovrapposizione viene
/// rimossa confrontando la coda del testo accumulato con la testa del nuovo
/// blocco, ignorando maiuscole e punteggiatura.
class TranscriptMerger {
  const TranscriptMerger({this.maxOverlapWords = 14, this.minOverlapWords = 3});

  final int maxOverlapWords;
  final int minOverlapWords;

  /// Rimuove le ripetizioni a ciclo prodotte dal riconoscitore.
  ///
  /// Su audio con pause, rumore o parlato poco chiaro whisper può entrare
  /// in un anello e ripetere la stessa frase per righe intere. Qui si
  /// cercano sequenze di parole che si ripetono immediatamente identiche e
  /// se ne tiene una sola copia.
  ///
  /// Il confronto ignora maiuscole e punteggiatura; la soglia minima di
  /// [minPhraseWords] parole serve a non toccare le ripetizioni legittime
  /// del parlato ("no, no, no", "sì sì").
  static String collapseRepetitions(
    String text, {
    int minPhraseWords = 3,
    int maxPhraseWords = 40,
  }) {
    final List<String> raw = text
        .split(RegExp(r'\s+'))
        .where((String w) => w.isNotEmpty)
        .toList();
    if (raw.length < minPhraseWords * 2) return text;

    final List<String> key = <String>[
      for (final String w in raw)
        w.toLowerCase().replaceAll(RegExp(r"[^\w'àèéìòùç]", unicode: true), ''),
    ];

    bool sameRun(int a, int b, int length) {
      for (int i = 0; i < length; i++) {
        if (key[a + i] != key[b + i]) return false;
      }
      return true;
    }

    final List<String> out = <String>[];
    int i = 0;
    while (i < raw.length) {
      int collapsed = 0;
      // Dalla frase più corta alla più lunga: va trovato il periodo della
      // ripetizione. Partendo dalle lunghe, otto copie di una frase di nove
      // parole verrebbero viste come due blocchi da trentasei e se ne
      // terrebbero quattro.
      final int maxHere =
          (raw.length - i) ~/ 2 < maxPhraseWords ? (raw.length - i) ~/ 2 : maxPhraseWords;
      for (int len = minPhraseWords; len <= maxHere; len++) {
        if (!sameRun(i, i + len, len)) continue;
        int repeats = 2;
        while (i + (repeats + 1) * len <= raw.length &&
            sameRun(i, i + repeats * len, len)) {
          repeats++;
        }
        out.addAll(raw.sublist(i, i + len));
        i += repeats * len;
        collapsed = len;
        break;
      }
      if (collapsed == 0) {
        out.add(raw[i]);
        i++;
      }
    }
    return out.join(' ');
  }

  /// Riduce le sequenze che crescono come revisioni l'una dell'altra.
  ///
  /// Un riconoscitore che consegna risultati parziali produce
  /// "molto / molto interessante / molto interessante questa storia…":
  /// ogni pezzo è una versione più lunga del precedente, non testo nuovo.
  /// Qui si tiene solo il più lungo di ogni catena di prefissi.
  static String collapseGrowingPrefixes(String text) {
    final List<String> raw = text
        .split(RegExp(r'\s+'))
        .where((String w) => w.isNotEmpty)
        .toList();
    if (raw.length < 4) return text;

    String key(String w) =>
        w.toLowerCase().replaceAll(RegExp(r"[^\w'àèéìòùç]", unicode: true), '');

    final List<String> keys = raw.map(key).toList();

    // Cerca il punto in cui il testo ricomincia da capo: se le parole che
    // seguono ripetono il tratto corrente, quel tratto era una revisione
    // incompleta e va buttato.
    //
    // L'ultima parola del tratto può essere troncata a metà, perché il
    // riconoscitore consegna la revisione mentre la sta ancora scrivendo
    // ("…della lettera segre" poi "…della lettera segreta"): per quella
    // basta che sia l'inizio della corrispondente.
    int start = 0;
    int i = 1;
    while (i < raw.length) {
      final int runLength = i - start;
      if (runLength >= 1 && i + runLength <= raw.length) {
        bool restarts = true;
        for (int k = 0; k < runLength; k++) {
          final String current = keys[start + k];
          final String next = keys[i + k];
          final bool last = k == runLength - 1;
          final bool match = last ? next.startsWith(current) : next == current;
          if (!match) {
            restarts = false;
            break;
          }
        }
        if (restarts) {
          start = i;
          i = start + 1;
          continue;
        }
      }
      i++;
    }
    return raw.sublist(start).join(' ');
  }

  String merge(Iterable<String> parts) {
    String result = '';
    for (final String raw in parts) {
      final String piece = collapseRepetitions(normalize(raw));
      if (piece.isEmpty) continue;
      if (result.isEmpty) {
        result = collapseRepetitions(piece);
        continue;
      }
      final String trimmed = _stripOverlap(result, piece);
      if (trimmed.isEmpty) continue;
      result = normalize('$result $trimmed');
    }
    // La pulizia finale coglie anche gli anelli nati a cavallo fra blocchi.
    return normalize(collapseRepetitions(collapseGrowingPrefixes(result)));
  }

  /// Rimuove spazi doppi, spazi prima della punteggiatura e righe vuote.
  static String normalize(String text) {
    String t = text.replaceAll('\u0000', ' ');
    t = t.replaceAll(RegExp(r'[ \t]+'), ' ');
    t = t.replaceAll(RegExp(r' *\n *'), '\n');
    t = t.replaceAll(RegExp(r'\n{3,}'), '\n\n');
    // `replaceAll` non interpreta i riferimenti ai gruppi: serve la versione
    // con callback.
    t = t.replaceAllMapped(
      RegExp(r'\s+([,.;:!?])'),
      (Match m) => m.group(1)!,
    );
    return t.trim();
  }

  /// Taglia dalla testa di [next] le parole già presenti in coda a
  /// [accumulated]; restituisce il resto di [next].
  String _stripOverlap(String accumulated, String next) {
    final List<_Token> tail = _tokenize(accumulated);
    final List<_Token> head = _tokenize(next);
    if (tail.isEmpty || head.isEmpty) return next;

    final int maxWindow = <int>[maxOverlapWords, tail.length, head.length]
        .reduce((int a, int b) => a < b ? a : b);

    for (int n = maxWindow; n >= minOverlapWords; n--) {
      bool same = true;
      for (int i = 0; i < n; i++) {
        if (tail[tail.length - n + i].key != head[i].key) {
          same = false;
          break;
        }
      }
      if (same) {
        // Riprende dal carattere successivo all'ultima parola sovrapposta.
        if (n >= head.length) return '';
        return next.substring(head[n].start).trimLeft();
      }
    }
    return next;
  }

  /// Parole significative con la posizione originale nel testo.
  static List<_Token> _tokenize(String text) {
    final List<_Token> tokens = <_Token>[];
    for (final RegExpMatch m
        in RegExp(r"[\w'àèéìòùÀÈÉÌÒÙç]+", unicode: true).allMatches(text)) {
      final String key = m.group(0)!.toLowerCase().replaceAll("'", '');
      if (key.isEmpty) continue;
      tokens.add(_Token(key, m.start));
    }
    return tokens;
  }
}

class _Token {
  const _Token(this.key, this.start);
  final String key;
  final int start;
}
