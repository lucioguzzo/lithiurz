import 'package:flutter_test/flutter_test.dart';
import 'package:brief/services/audio/transcript_merger.dart';

void main() {
  const TranscriptMerger merger = TranscriptMerger();

  test('unisce i blocchi con uno spazio singolo', () {
    expect(
      merger.merge(<String>['Primo blocco.', 'Secondo blocco.']),
      'Primo blocco. Secondo blocco.',
    );
  });

  test('ignora i blocchi vuoti', () {
    expect(
      merger.merge(<String>['Testo.', '   ', '', 'Altro.']),
      'Testo. Altro.',
    );
  });

  test('rimuove la frase ripetuta a cavallo fra due blocchi', () {
    const String a = 'Ci vediamo domani mattina in ufficio alle nove.';
    const String b = 'Ci vediamo domani mattina in ufficio alle nove. '
        'Porta il preventivo.';
    expect(merger.merge(<String>[a, b]),
        'Ci vediamo domani mattina in ufficio alle nove. Porta il preventivo.');
  });

  test('la sovrapposizione è riconosciuta a meno di maiuscole e punteggiatura',
      () {
    const String a = 'allora, dicevo che il contratto scade a fine mese';
    const String b = 'Il contratto scade a fine mese. Dobbiamo rinnovarlo.';
    final String merged = merger.merge(<String>[a, b]);
    expect(merged, isNot(contains('mese. Il contratto scade')));
    expect(merged, contains('Dobbiamo rinnovarlo.'));
  });

  test('non taglia nulla quando non c\'è sovrapposizione', () {
    const String a = 'Parliamo del budget del prossimo trimestre.';
    const String b = 'Passiamo adesso al piano di assunzioni.';
    expect(merger.merge(<String>[a, b]), '$a $b');
  });

  test('una ripetizione di sole due parole non viene rimossa', () {
    // Soglia minima: tre parole, per non mangiare parole legittime.
    const String a = 'Il progetto va avanti bene';
    const String b = 'bene, direi che possiamo chiudere.';
    expect(merger.merge(<String>[a, b]), contains('bene bene'));
  });

  test('un blocco interamente ripetuto non viene aggiunto due volte', () {
    const String a = 'Ti confermo la riunione di martedì alle dieci.';
    expect(merger.merge(<String>[a, a]), a);
  });

  group('collapseRepetitions', () {
    // Caso reale: su un vocale con pause whisper è entrato in un anello e
    // ha ripetuto la stessa frase quattro volte di fila.
    const String anello =
        'Il problema è che se prendo lo screenshot della chat, '
        'ignorando il fatto che tu stai a sinistra dello schermo e a destra '
        'per te viceversa, fa confusione, '
        'ignorando il fatto che tu stai a sinistra dello schermo e a destra '
        'per te viceversa, fa confusione, '
        'ignorando il fatto che tu stai a sinistra dello schermo e a destra '
        'per te viceversa, fa confusione.';

    test('riduce a una sola copia la frase ripetuta a ciclo', () {
      final String pulito = TranscriptMerger.collapseRepetitions(anello);
      final int occorrenze =
          'ignorando'.allMatches(pulito.toLowerCase()).length;
      expect(occorrenze, 1);
      expect(pulito, contains('Il problema è che'));
      expect(pulito, contains('fa confusione'));
    });

    test('la pulizia avviene anche passando da merge', () {
      final String unito = merger.merge(<String>[anello]);
      expect('viceversa'.allMatches(unito.toLowerCase()).length, 1);
    });

    test('non tocca le ripetizioni brevi del parlato', () {
      const String parlato = 'No, no, no. Sì sì, va bene.';
      expect(TranscriptMerger.collapseRepetitions(parlato), parlato);
    });

    test('non tocca un testo senza ripetizioni', () {
      const String testo =
          'Ti confermo la riunione di giovedì alle dieci in sede. '
          'Porta il preventivo aggiornato.';
      expect(TranscriptMerger.collapseRepetitions(testo), testo);
    });

    test('riduce anche una ripetizione lunghissima', () {
      const String frase = 'questo è un esempio di frase ripetuta molte volte';
      final String loop = List<String>.filled(8, frase).join(' ');
      final String pulito = TranscriptMerger.collapseRepetitions(loop);
      expect(pulito, frase);
    });

    test('tiene le ripetizioni non contigue, che possono essere reali', () {
      const String testo = 'Ci vediamo domani in ufficio. Porta il contratto. '
          'Ci vediamo domani in ufficio.';
      final String pulito = TranscriptMerger.collapseRepetitions(testo);
      expect(pulito, contains('Porta il contratto'));
      expect('Ci vediamo'.allMatches(pulito).length, 2);
    });

    test('un testo troppo corto resta intatto', () {
      expect(TranscriptMerger.collapseRepetitions('sì sì'), 'sì sì');
      expect(TranscriptMerger.collapseRepetitions(''), '');
    });
  });

  group('collapseGrowingPrefixes', () {
    // Caso reale: il riconoscitore di sistema consegnava i risultati
    // parziali, ognuno una revisione più lunga del precedente, e venivano
    // accodati tutti.
    const String parziali =
        'molto molto interessante molto interessante questa storia della '
        'lettera segre molto interessante questa storia della lettera '
        'segreta di Napoleone che viene molto interessante questa storia '
        'della lettera segreta di Napoleone che viene decifrata da Astra';

    test('tiene solo la revisione più lunga', () {
      final String pulito =
          TranscriptMerger.collapseGrowingPrefixes(parziali);
      expect(pulito,
          'molto interessante questa storia della lettera segreta di '
          'Napoleone che viene decifrata da Astra');
    });

    test('la pulizia avviene anche passando da merge', () {
      final String unito = merger.merge(<String>[parziali]);
      expect('interessante'.allMatches(unito).length, 1);
      expect(unito, endsWith('decifrata da Astra'));
    });

    test('non tocca un testo che non ricomincia da capo', () {
      const String testo = 'Ti confermo la riunione di giovedì alle dieci. '
          'Porta il preventivo aggiornato e i consumi.';
      expect(TranscriptMerger.collapseGrowingPrefixes(testo), testo);
    });

    test('un testo cortissimo resta intatto', () {
      expect(TranscriptMerger.collapseGrowingPrefixes('ciao a te'),
          'ciao a te');
    });
  });

  test('normalize elimina spazi prima della punteggiatura', () {
    expect(
      TranscriptMerger.normalize('Ciao  ,  come stai ?'),
      'Ciao, come stai?',
    );
  });

  test('normalize comprime le righe vuote in eccesso', () {
    expect(
      TranscriptMerger.normalize('Riga uno.\n\n\n\nRiga due.'),
      'Riga uno.\n\nRiga due.',
    );
  });
}
