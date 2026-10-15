import 'package:flutter_test/flutter_test.dart';
import 'package:brief/services/audio/chunk_planner.dart';

void main() {
  group('parseSilenceLog', () {
    test('estrae le finestre di silenzio complete', () {
      const String log = '''
[silencedetect @ 0x55] silence_start: 12.345
[silencedetect @ 0x55] silence_end: 13.5 | silence_duration: 1.155
[silencedetect @ 0x55] silence_start: 61.0
[silencedetect @ 0x55] silence_end: 62.25 | silence_duration: 1.25
''';
      final List<SilenceWindow> windows = parseSilenceLog(log);
      expect(windows, hasLength(2));
      expect(windows.first.start, const Duration(milliseconds: 12345));
      expect(windows.first.end, const Duration(milliseconds: 13500));
      expect(windows.last.center, const Duration(milliseconds: 61625));
    });

    test('ignora un silenzio iniziato ma mai chiuso', () {
      const String log = 'silence_start: 5.0\nsilence_start: 9.0\n'
          'silence_end: 10.0 | silence_duration: 1.0';
      final List<SilenceWindow> windows = parseSilenceLog(log);
      expect(windows, hasLength(1));
      expect(windows.single.start, const Duration(seconds: 9));
    });

    test('tratta un inizio negativo come zero', () {
      const String log = 'silence_start: -0.5\nsilence_end: 1.0';
      expect(parseSilenceLog(log).single.start, Duration.zero);
    });

    test('su log privo di silenzi restituisce lista vuota', () {
      expect(parseSilenceLog('nessun silenzio qui'), isEmpty);
    });
  });

  group('planChunks', () {
    test('un audio breve resta in un unico blocco', () {
      final List<AudioChunk> chunks =
          planChunks(total: const Duration(seconds: 90));
      expect(chunks, hasLength(1));
      expect(chunks.single.start, Duration.zero);
      expect(chunks.single.end, const Duration(seconds: 90));
    });

    test('oltre due minuti si divide, per mostrare il testo via via', () {
      // La soglia bassa è voluta: whisper.cpp restituisce il testo solo a
      // fine blocco, quindi è la suddivisione a rendere visibile
      // l'avanzamento invece di una barra ferma per minuti.
      final List<AudioChunk> chunks =
          planChunks(total: const Duration(minutes: 3));
      expect(chunks.length, greaterThan(1));
      expect(chunks.last.end, const Duration(minutes: 3));
    });

    test('un vocale di dieci minuti produce blocchi frequenti', () {
      final List<AudioChunk> chunks =
          planChunks(total: const Duration(minutes: 10));
      // Un pezzo di testo ogni minuto e mezzo circa.
      expect(chunks.length, inInclusiveRange(5, 9));
      for (final AudioChunk c in chunks) {
        expect(c.length, lessThanOrEqualTo(const Duration(seconds: 130)));
      }
    });

    test('durata nulla non produce blocchi', () {
      expect(planChunks(total: Duration.zero), isEmpty);
    });

    test('audio lungo viene diviso coprendo tutta la durata', () {
      const Duration total = Duration(minutes: 32);
      final List<AudioChunk> chunks = planChunks(total: total);

      expect(chunks.length, greaterThan(4));
      expect(chunks.first.start, Duration.zero);
      expect(chunks.last.end, total);
      for (int i = 1; i < chunks.length; i++) {
        // Nessun buco e nessuna sovrapposizione fra blocchi contigui.
        expect(chunks[i].start, chunks[i - 1].end);
        expect(chunks[i].index, i);
      }
      // Ogni blocco resta vicino alla durata obiettivo.
      for (final AudioChunk c in chunks) {
        expect(c.length.inSeconds, greaterThan(0));
        expect(c.length, lessThanOrEqualTo(const Duration(minutes: 6)));
      }
    });

    test('il confine si sposta sul silenzio più vicino', () {
      final List<AudioChunk> chunks = planChunks(
        total: const Duration(minutes: 12),
        target: const Duration(minutes: 5),
        drift: const Duration(seconds: 30),
        threshold: const Duration(minutes: 8),
        silences: <SilenceWindow>[
          // Centro a 4:50: dentro la tolleranza di 30 s dal confine a 5:00.
          const SilenceWindow(
            Duration(seconds: 288),
            Duration(seconds: 292),
          ),
        ],
      );
      expect(chunks.first.end, const Duration(seconds: 290));
      expect(chunks[1].start, const Duration(seconds: 290));
    });

    test('un silenzio troppo lontano non sposta il confine', () {
      final List<AudioChunk> chunks = planChunks(
        total: const Duration(minutes: 12),
        target: const Duration(minutes: 5),
        drift: const Duration(seconds: 10),
        threshold: const Duration(minutes: 8),
        silences: <SilenceWindow>[
          const SilenceWindow(
            Duration(seconds: 200),
            Duration(seconds: 203),
          ),
        ],
      );
      expect(chunks.first.end, const Duration(minutes: 5));
    });

    test('la coda troppo corta viene fusa nel blocco precedente', () {
      // 10 minuti e 10 secondi: il residuo di 10 s non diventa un blocco.
      final List<AudioChunk> chunks = planChunks(
        total: const Duration(minutes: 10, seconds: 10),
        target: const Duration(minutes: 5),
        threshold: const Duration(minutes: 8),
        minTail: const Duration(seconds: 30),
      );
      expect(chunks, hasLength(2));
      expect(chunks.last.end, const Duration(minutes: 10, seconds: 10));
      expect(chunks.last.length, greaterThan(const Duration(minutes: 5)));
    });

    test('serializzazione e deserializzazione di un blocco', () {
      const AudioChunk chunk = AudioChunk(
        index: 3,
        start: Duration(seconds: 90),
        end: Duration(seconds: 180),
      );
      final AudioChunk restored = AudioChunk.fromJson(chunk.toJson());
      expect(restored.index, 3);
      expect(restored.start, const Duration(seconds: 90));
      expect(restored.end, const Duration(seconds: 180));
    });
  });
}
