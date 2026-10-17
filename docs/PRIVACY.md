# Privacy e adempimenti

Testo di riferimento per l'informativa. La versione mostrata nell'app è in
`brief/lib/ui/screens/privacy_screen.dart`: **le due vanno tenute
allineate**. Gli store richiedono anche un URL pubblico raggiungibile.

## Principio seguito: minimizzazione

| Dato | Dove sta | Quanto resta | Esce dal dispositivo? |
|---|---|---|---|
| Audio condiviso | cartella privata dell'app | cancellato a fine elaborazione (salvo scelta contraria) | solo se si attiva la trascrizione nel cloud |
| File temporanei dei blocchi | cartella temporanea | cancellati subito dopo ogni blocco | come sopra: inviati al server solo con il motore cloud |
| Trascrizione | SQLite locale | finché l'utente non la cancella; disattivabile | solo se si sceglie il riassunto cloud |
| Riassunto | SQLite locale | come sopra | no |
| Identificativo dispositivo | SharedPreferences | fino alla disinstallazione | sì, al solo backend, per le quote |
| Contatori di utilizzo | SQLite sul server | storico numerico | — |

Il backend **non registra** nei log né il testo ricevuto né il riassunto
prodotto, e non scrive mai l'audio su disco: tiene solo numeri (lunghezza,
secondi di audio, numero di blocchi, costo stimato).

## Basi giuridiche (GDPR)

- **Trattamento sul dispositivo**: non c'è trasferimento verso il titolare,
  quindi non serve una base giuridica ulteriore rispetto all'esecuzione del
  servizio richiesto dall'utente.
- **Riassunto cloud**: *consenso* (art. 6.1.a). L'app lo chiede in modo
  esplicito, spiega cosa viene inviato e consente di revocarlo. Senza
  consenso il cloud non viene mai contattato.
- **Trascrizione nel cloud**: *consenso* separato (art. 6.1.a), chiesto a
  parte perché l'oggetto è diverso: non un testo già trascritto ma la
  registrazione, cioè la voce — un dato che può rivelare molto di più e
  che spesso contiene anche la voce di terzi. L'app lo chiede con un
  avviso dedicato, lo mostra datato nelle impostazioni e lo revoca
  spegnendo l'interruttore. Il motore è disattivato finché non lo si
  accende.
- **Voce di terzi**: chi trascrive la registrazione di un'altra persona ne
  tratta i dati. L'avviso dell'app lo dice esplicitamente; nell'informativa
  pubblicata va ricordato che la responsabilità è dell'utente.
- **Pubblicità**: consenso tramite una CMP conforme (Google UMP). **Non è
  ancora integrata**: va aggiunta prima di pubblicare nello SEE
  (vedi `ATTIVITA_RESIDUE.md`).

## Responsabili del trattamento

Chi pubblica l'app è titolare. Vanno indicati nell'informativa:

- il fornitore IA scelto (Anthropic oppure OpenAI o altri), come
  responsabile per il testo inviato — verificare che i termini escludano
  l'addestramento sui dati trasmessi e dichiarare la sede dei server;
- il fornitore dell'infrastruttura del backend;
- Google (AdMob) per la pubblicità.

## Licenze dei componenti

| Componente | Licenza | Implicazioni |
|---|---|---|
| whisper.cpp (`whisper_ggml`) | MIT | uso commerciale libero, citare la licenza |
| Modelli ggml di Whisper | MIT (OpenAI) | distribuzione e uso commerciale consentiti |
| FFmpeg (build `min` di FFmpegKit) | LGPL 2.1+ | collegamento dinamico: consentito nel software proprietario. **Non** usare le build `-gpl`, che imporrebbero la GPL all'intera app. Vanno pubblicati l'avviso di licenza e il modo per sostituire la libreria |
| Flutter e plugin usati | BSD/MIT/Apache 2.0 | citare le licenze |
| Google Mobile Ads | termini AdMob | serve una CMP nello SEE |

L'app mostra l'elenco completo delle licenze da *Impostazioni → Privacy e
dati → Licenze dei componenti* (`showLicensePage` di Flutter).

## Fornitori coinvolti quando il cloud è attivo

| Fornitore | Cosa riceve | Quando |
|---|---|---|
| Backend di BRIEF (titolare) | testo del riassunto, oppure blocchi audio | solo col motore/riassunto cloud attivo |
| Google (API Gemini) | blocchi audio, per restituirne il testo | solo con la trascrizione nel cloud |
| Fornitore dei riassunti | testo trascritto | solo col riassunto cloud |

**Vincolo dei termini Gemini.** Per gli utenti di SEE, Svizzera e Regno
Unito l'API va usata **solo nella versione a pagamento**
([termini](https://ai.google.dev/gemini-api/terms)): sul piano gratuito
Google può usare i contenuti per migliorare i propri prodotti e sottoporli
a revisione umana, il che è incompatibile con questa informativa. Prima di
pubblicare: chiave di un progetto con fatturazione attiva, e verifica che
l'accordo di trattamento dei dati di Google Cloud copra il trasferimento.

## Dove i modelli vengono scaricati

I modelli ggml arrivano da Hugging Face
(`huggingface.co/ggerganov/whisper.cpp`) al primo utilizzo. È una
connessione verso terzi: va dichiarata nell'informativa. Chi preferisce
evitarla può includere il modello fra gli asset dell'app o ospitarlo su un
proprio dominio.

## Schede privacy degli store

**Google Play — Sicurezza dei dati.** Da aggiornare ora che esiste la
trascrizione nel cloud: dichiarare **registrazioni audio condivise** (non
raccolte in modo persistente) con il titolare e il suo fornitore, solo con
consenso e per la funzionalità richiesta;
"testo generato dall'utente" **condiviso** (non raccolto in modo
persistente) solo con consenso, per la funzionalità dell'app; identificativi
raccolti per prevenire gli abusi; dati pubblicitari secondo AdMob. Dati in
transito cifrati; l'utente può chiedere la cancellazione (dall'app).

**Apple — Nutrition label.** "Dati non collegati all'utente": identificativo
del dispositivo (funzionalità), contenuti dell'utente (solo con consenso,
per la funzionalità). Nessun tracciamento fra app se non si attiva l'IDFA;
se AdMob lo usa, serve il prompt ATT.
