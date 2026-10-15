import 'package:flutter_test/flutter_test.dart';
import 'package:brief/core/formatters.dart';

void main() {
  group('Fmt.duration', () {
    test('minuti e secondi', () {
      expect(Fmt.duration(const Duration(minutes: 3, seconds: 7)), '03:07');
    });
    test('oltre l\'ora mostra le ore', () {
      expect(
        Fmt.duration(const Duration(hours: 1, minutes: 4, seconds: 9)),
        '1:04:09',
      );
    });
    test('durata nulla', () {
      expect(Fmt.duration(Duration.zero), '00:00');
    });
  });

  group('Fmt.bytes', () {
    test('byte', () => expect(Fmt.bytes(512), '512 B'));
    test('kilobyte', () => expect(Fmt.bytes(2048), '2,0 KB'));
    test('megabyte', () => expect(Fmt.bytes(4 * 1024 * 1024 + 200000), '4,2 MB'));
    test('gigabyte', () => expect(Fmt.bytes(3 * 1024 * 1024 * 1024), '3,0 GB'));
  });

  group('Fmt.titleFromFileName', () {
    test('rimuove percorso, estensione, separatori e prefisso temporale', () {
      expect(
        Fmt.titleFromFileName('/tmp/1760561234567_AUD-20260101-WA0007.opus'),
        'AUD 20260101 WA0007',
      );
    });

    test('non tocca un numero che fa parte del nome scelto dall\'utente', () {
      expect(Fmt.titleFromFileName('2024_bilancio.m4a'), '2024 bilancio');
    });

    test('mette l\'iniziale maiuscola', () {
      expect(Fmt.titleFromFileName('nota della riunione.m4a'),
          'Nota della riunione');
    });
    test('nome vuoto ricade su un titolo generico', () {
      expect(Fmt.titleFromFileName('.opus'), 'Audio');
    });
    test('accorcia i nomi lunghissimi', () {
      final String title = Fmt.titleFromFileName('${'a' * 200}.mp3');
      expect(title.length, lessThanOrEqualTo(60));
      expect(title, endsWith('…'));
    });
  });

  group('Fmt.preview', () {
    test('testo breve resta intero', () {
      expect(Fmt.preview('Ciao mondo'), 'Ciao mondo');
    });
    test('testo lungo viene troncato alle parole', () {
      final String p = Fmt.preview('parola ' * 100, max: 50);
      expect(p.length, lessThanOrEqualTo(51));
      expect(p, endsWith('…'));
    });
    test('normalizza gli spazi', () {
      expect(Fmt.preview('a   b\n\nc'), 'a b c');
    });
  });

  test('Fmt.dateTime usa il formato italiano', () {
    expect(Fmt.dateTime(DateTime(2026, 10, 15, 9, 5)), '15/10/2026 09:05');
  });
}
