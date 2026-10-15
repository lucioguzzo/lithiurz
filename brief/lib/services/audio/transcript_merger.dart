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

  String merge(Iterable<String> parts) {
    String result = '';
    for (final String raw in parts) {
      final String piece = normalize(raw);
      if (piece.isEmpty) continue;
      if (result.isEmpty) {
        result = piece;
        continue;
      }
      final String trimmed = _stripOverlap(result, piece);
      if (trimmed.isEmpty) continue;
      result = normalize('$result $trimmed');
    }
    return normalize(result);
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
