import 'package:flutter_test/flutter_test.dart';
import 'package:brief/services/transcription/transcript_quality.dart';

void main() {
  group('conteggio parole', () {
    test('un testo vuoto vale zero, non uno', () {
      expect(TranscriptQuality.wordCount(''), 0);
      expect(TranscriptQuality.wordCount('   \n '), 0);
    });

    test('gli spazi multipli non contano come parole', () {
      expect(TranscriptQuality.wordCount('ciao   a    tutti'), 3);
    });
  });

  group('trascrizione troncata', () {
    // Il caso reale che ha chiuso la partita col motore di sistema:
    // 36 secondi di audio, 15 parole trascritte contro le 127 di Whisper.
    const String quindiciParole =
        'buongiorno a tutti oggi parliamo di una cosa che riguarda '
        'il lavoro di questa settimana e poi';

    String parole(int n) => List<String>.filled(n, 'parola').join(' ');

    test('15 parole in 36 secondi sono troppo poche', () {
      expect(
        TranscriptQuality.looksTruncated(
          text: quindiciParole,
          audio: const Duration(seconds: 36),
        ),
        isTrue,
      );
    });

    test('127 parole negli stessi 36 secondi sono plausibili', () {
      expect(
        TranscriptQuality.looksTruncated(
          text: parole(127),
          audio: const Duration(seconds: 36),
        ),
        isFalse,
      );
    });

    test('un audio brevissimo non viene mai segnalato', () {
      expect(
        TranscriptQuality.looksTruncated(
          text: 'sì',
          audio: const Duration(seconds: 8),
        ),
        isFalse,
      );
    });

    test('un testo vuoto su audio lungo è sempre sospetto', () {
      expect(
        TranscriptQuality.looksTruncated(
          text: '',
          audio: const Duration(minutes: 3),
        ),
        isTrue,
      );
    });

    test('una durata nulla non manda in errore il calcolo', () {
      expect(TranscriptQuality.wordsPerMinute('due parole', Duration.zero), 0);
      expect(
        TranscriptQuality.looksTruncated(text: 'due parole', audio: Duration.zero),
        isFalse,
      );
    });

    test('mezz\'ora di audio con poche righe è troncata', () {
      expect(
        TranscriptQuality.looksTruncated(
          text: parole(200),
          audio: const Duration(minutes: 30),
        ),
        isTrue,
      );
    });
  });
}
