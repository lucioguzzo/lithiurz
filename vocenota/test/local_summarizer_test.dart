import 'package:flutter_test/flutter_test.dart';
import 'package:vocenota/core/constants.dart';
import 'package:vocenota/domain/summary.dart';
import 'package:vocenota/services/summary/local_summarizer.dart';

const String verbale = '''
Ciao Marco, ti aggiorno sulla riunione di stamattina con il fornitore.
Abbiamo discusso il rinnovo del contratto di manutenzione, che scade il
trenta novembre. Il fornitore ha proposto un aumento del dodici per cento,
che mi sembra francamente eccessivo. Dobbiamo preparare una controproposta
entro venerdì, altrimenti perdiamo la finestra di rinnovo. Ti chiedo di
verificare i consumi degli ultimi sei mesi, così argomentiamo con i numeri.
Ricordati anche di chiamare l'ufficio acquisti per sapere se hanno altri
preventivi. Il contratto di manutenzione è comunque centrale per noi.
''';

void main() {
  const LocalSummarizer summarizer = LocalSummarizer();

  group('splitSentences', () {
    test('divide sulla punteggiatura forte', () {
      final List<String> s = LocalSummarizer.splitSentences(
        'Il contratto scade a novembre. Serve una controproposta! '
        'Chi se ne occupa? Ne parliamo domani in riunione',
      );
      expect(s, hasLength(4));
      expect(s.first, 'Il contratto scade a novembre.');
      expect(s.last, 'Ne parliamo domani in riunione');
    });

    test('riaggrega i frammenti troppo corti', () {
      final List<String> s = LocalSummarizer.splitSentences('Va bene. Sì.');
      expect(s, hasLength(1));
      expect(s.single, 'Va bene. Sì.');
    });

    test('testo vuoto non produce frasi', () {
      expect(LocalSummarizer.splitSentences('   '), isEmpty);
    });
  });

  group('summarize', () {
    test('produce riassunto, punti e azioni da una trascrizione reale', () {
      final SummaryBundle bundle = summarizer.summarize(verbale);

      expect(bundle.engine, SummaryEngine.locale);
      expect(bundle.text, isNotEmpty);
      expect(bundle.keyPoints, isNotEmpty);
      expect(bundle.actions, isNotEmpty);
      // Le azioni devono pescare le frasi con un impegno esplicito.
      final String azioni =
          bundle.actions.map((ActionItem a) => a.text).join(' ').toLowerCase();
      expect(azioni, anyOf(contains('entro'), contains('ricordati'),
          contains('verificare'), contains('chiamare')));
    });

    test('lo stile dettagliato è più lungo di quello breve', () {
      final SummaryBundle breve =
          summarizer.summarize(verbale, style: SummaryStyle.breve);
      final SummaryBundle dettagliato =
          summarizer.summarize(verbale, style: SummaryStyle.dettagliato);
      expect(dettagliato.text.length, greaterThan(breve.text.length));
    });

    test('le frasi del riassunto provengono dal testo originale', () {
      final SummaryBundle bundle = summarizer.summarize(verbale);
      for (final String frase in bundle.text.split(RegExp(r'(?<=[.!?])\s+'))) {
        if (frase.trim().isEmpty) continue;
        final String normalizzata = frase.replaceAll(RegExp(r'\s+'), ' ').trim();
        expect(
          verbale.replaceAll(RegExp(r'\s+'), ' '),
          contains(normalizzata),
          reason: 'il riassunto estrattivo non deve inventare testo',
        );
      }
    });

    test('su testo vuoto restituisce un riassunto vuoto senza errori', () {
      final SummaryBundle bundle = summarizer.summarize('   ');
      expect(bundle.isEmpty, isTrue);
    });

    test('non duplica la stessa azione', () {
      const String ripetuto = 'Dobbiamo chiamare il cliente. '
          'Dobbiamo chiamare il cliente. Poi si vedrà.';
      final SummaryBundle bundle = summarizer.summarize(ripetuto);
      expect(bundle.actions.map((ActionItem a) => a.text).toSet().length,
          bundle.actions.length);
    });
  });

  group('SummaryBundle', () {
    test('viaggia integro attraverso JSON', () {
      final SummaryBundle original = summarizer.summarize(verbale);
      final SummaryBundle? restored = SummaryBundle.decode(original.encode());
      expect(restored, isNotNull);
      expect(restored!.text, original.text);
      expect(restored.keyPoints, original.keyPoints);
      expect(restored.actions.length, original.actions.length);
      expect(restored.engine, original.engine);
    });

    test('decode tollera dati corrotti', () {
      expect(SummaryBundle.decode('non è json'), isNull);
      expect(SummaryBundle.decode(null), isNull);
      expect(SummaryBundle.decode(''), isNull);
    });

    test('accetta la risposta del backend con azioni testuali semplici', () {
      final SummaryBundle bundle = SummaryBundle.fromJson(<String, dynamic>{
        'summary': 'Riassunto.',
        'key_points': <Object?>['Punto uno', 'Punto due'],
        'actions': <Object?>[
          <String, dynamic>{'text': 'Chiamare', 'owner': 'Luca', 'due': 'lunedì'},
          'Inviare il file',
        ],
        'engine': 'cloud',
      });
      expect(bundle.keyPoints, hasLength(2));
      expect(bundle.actions.first.display, 'Chiamare — Luca (lunedì)');
      expect(bundle.actions.last.text, 'Inviare il file');
      expect(bundle.engine, SummaryEngine.cloud);
    });

    test('il testo esportato contiene tutte le sezioni', () {
      final SummaryBundle bundle = summarizer.summarize(verbale);
      final String testo =
          bundle.toPlainText(title: 'Riunione', transcript: verbale);
      expect(testo, contains('RIASSUNTO'));
      expect(testo, contains('PUNTI PRINCIPALI'));
      expect(testo, contains('TRASCRIZIONE'));
      expect(testo, contains('VoceNota'));
    });
  });
}
