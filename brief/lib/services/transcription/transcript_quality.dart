/// Controlli sulla plausibilità di una trascrizione.
///
/// Non giudicano la correttezza delle parole — per quella servirebbe il
/// testo di riferimento — ma riconoscono il caso in cui il motore ha
/// smesso di ascoltare dopo pochi secondi e ha restituito un testo
/// troppo corto per la durata dell'audio.
class TranscriptQuality {
  const TranscriptQuality._();

  /// Parlato continuo in italiano: dalle 120 alle 180 parole al minuto.
  /// Sotto questa soglia o c'è molto silenzio, o il motore si è fermato.
  static const double minPlausibleWordsPerMinute = 40;

  /// Sotto questa durata il conteggio non dice niente: un messaggio di
  /// cinque secondi può contenere due parole ed essere completo.
  static const Duration minMeaningfulAudio = Duration(seconds: 20);

  /// Numero di parole nel testo.
  static int wordCount(String text) {
    final String t = text.trim();
    if (t.isEmpty) return 0;
    return t.split(RegExp(r'\s+')).length;
  }

  /// Parole al minuto, `0` se la durata è nulla.
  static double wordsPerMinute(String text, Duration audio) {
    final double minutes = audio.inMilliseconds / 60000;
    if (minutes <= 0) return 0;
    return wordCount(text) / minutes;
  }

  /// `true` se il testo è troppo breve per la durata dell'audio, cioè se
  /// con ogni probabilità la trascrizione si è interrotta a metà.
  static bool looksTruncated({required String text, required Duration audio}) {
    if (audio < minMeaningfulAudio) return false;
    if (wordCount(text) == 0) return true;
    return wordsPerMinute(text, audio) < minPlausibleWordsPerMinute;
  }
}
