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
