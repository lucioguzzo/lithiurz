# BRIEF - Vocal Sum Up

**Trascrizione e riassunto di audio, Android e iOS.**
© 2026 Lucio Salvatore Guzzo

App Flutter che riceve un audio dal menu **Condividi** di WhatsApp, Telegram,
email o file manager e restituisce **trascrizione**, **riassunto**, **punti
principali** e **azioni da svolgere**, con lettura vocale, copia,
condivisione, esportazione e cronologia locale.

La trascrizione avviene **sul dispositivo** con whisper.cpp: l'audio non
lascia mai il telefono. Solo il riassunto, e solo se l'utente acconsente,
passa dal backend — che riceve **testo, mai audio**.

> In questo repository convive anche il progetto precedente
> **Baby Cry Translator** (cartella `app/`), lasciato invariato.

## Nome e copyright

L'app si chiama **BRIEF - Vocal Sum Up**: è il nome che compare
nell'intestazione, nelle impostazioni, nei testi esportati e che va usato
nelle schede degli store. Sotto l'icona del telefono compare il solo
**BRIEF**, perché Android e iOS troncano le etichette oltre una dozzina di
caratteri e "BRIEF - Vocal Su…" si leggerebbe male.

In fondo a ogni schermata compare la riga **© 2026 Lucio Salvatore Guzzo**. Nome e
anno stanno in `AppInfo.copyrightOwner` e `AppInfo.copyrightYear`
(`brief/lib/core/constants.dart`): per cambiarli basta quella riga.

Identificatori tecnici allineati al nome: pacchetto Dart `brief`,
`applicationId` e bundle id `com.lucio.brief`, gruppo app iOS
`group.com.lucio.brief`.

## Struttura

| Cartella    | Contenuto |
|-------------|-----------|
| `brief/` | App Flutter (Android + iOS) |
| `backend/`  | Servizio FastAPI per i riassunti, con quote e tetto di spesa |
| `tools/`    | Banco di prova del WER italiano, per confrontare i motori |
| `docs/`     | Architettura, compilazione, messa online del backend, iOS, privacy, pubblicazione, attività residue |
| `app/`      | Progetto precedente (Baby Cry Translator), non toccato |

## Avvio rapido

```bash
# App
cd brief
flutter pub get
flutter test                 # 123 test unitari
flutter build apk --debug    # Android

# Backend
cd ../backend
pip install -r requirements-dev.txt
python -m pytest             # 39 test
uvicorn app.main:app --reload
```

Dettagli completi in [`docs/COMPILAZIONE.md`](docs/COMPILAZIONE.md).
Se vuoi solo l'APK senza compilare nulla sul tuo computer:
[`COME_OTTENERE_APK_BRIEF.md`](COME_OTTENERE_APK_BRIEF.md).

## Che cosa fa, in concreto

- **Ingresso condiviso** — intent `ACTION_SEND` / `ACTION_SEND_MULTIPLE` su
  Android, Share Extension su iOS, più import manuale dal file manager. Il
  file viene copiato subito nella sandbox dell'app, perché l'URI condiviso
  può sparire appena l'app di origine si chiude.
- **Controlli preventivi** — formato, dimensione (max 500 MB), durata (max 4
  ore) e presenza di una traccia audio, letti con ffprobe.
- **Whisper come motore di trascrizione** — whisper.cpp con il modello
  **Small quantizzato q8_0** (252 MB invece dei 465 del modello pieno),
  gratuito e offline su qualunque telefono. Tiny e Base erano offerti
  all'inizio ma sul parlato italiano reale sbagliavano troppo: un riassunto
  costruito su una trascrizione sbagliata è peggio di nessun riassunto. Chi
  li aveva scaricati se li vede eliminare al primo avvio. La prima versione
  usava q5_1 (181 MB): comprime di più, ma perde proprio i dettagli che
  servono sui nomi propri e sulle parole dette di fretta, quindi si è
  passati a q8_0. L'app riconosce dalla dimensione del file il modello
  rimasto da una versione precedente e propone di sostituirlo; il download
  nuovo arriva in un file `.part` e prende il posto del vecchio solo
  quando è completo, così una connessione che cade non lascia senza
  modello.
- **Pronta all'uso appena installata** — l'indirizzo del server si cuce
  nella build (`--dart-define=BRIEF_BACKEND_URL=…`), così chi installa
  l'app non deve configurare niente: al primo avvio sceglie fra *cloud*
  (pronto subito) e *solo sul telefono* (scarica il modello), e ha
  finito. Una build senza quel parametro resta perfettamente usabile, ma
  locale: l'opzione cloud non viene nemmeno mostrata, perché non
  potrebbe funzionare.
- **Trascrizione nel cloud, facoltativa** — terzo motore, spento di
  default: il blocco audio va in HTTPS al backend di BRIEF, che lo fa
  trascrivere a Gemini e restituisce il testo. È la scelta più accurata,
  perché il modello è di un ordine di grandezza più grande di quanto stia
  in un telefono, ma è **l'unica in cui l'audio esce dal dispositivo**:
  per accenderla servono un server configurato e un consenso esplicito,
  separato da quello del riassunto, revocabile in qualsiasi momento. Il
  server inoltra l'audio senza salvarlo e registra solo numeri (durata,
  costo stimato) per applicare le quote. I termini di Google impongono il
  piano a pagamento per gli utenti europei; costa fra i 4 e i 13 centesimi
  di dollaro per ora di audio.
- **Motore del telefono: opzione sperimentale, non consigliata** — il
  riconoscitore vocale di sistema (`SpeechRecognizer` da Android 13,
  `SFSpeechRecognizer` su iOS) sulla carta è molto più rapido, perché
  ottimizzato per quel chip. Alla prova sul campo non ha retto: su un
  nubia NX713J con Android 13 chiude l'ascolto dopo i primi secondi e
  restituisce **15 parole su 36 secondi di audio**, contro le 127 di
  Whisper, anche usando la sessione segmentata, spezzoni da 25 secondi e
  soglie di silenzio portate a 10 minuti. Il codice resta nell'app come
  opzione da attivare a mano, nascosta dietro un avviso, per chi avesse un
  telefono che la implementa meglio; **il motore predefinito è Whisper** e
  chi aveva scelto il motore di sistema viene riportato una volta sola a
  Whisper all'aggiornamento. Quando una trascrizione risulta troppo corta
  per la durata dell'audio, la schermata del risultato lo segnala.
- **Trascrizione visibile mentre procede** — oltre 2 minuti l'audio viene
  diviso in blocchi da ~90 secondi, tagliati **sui silenzi** per non
  spezzare le parole, e il testo di ogni blocco compare appena pronto sotto
  una barra di avanzamento. whisper.cpp restituisce il testo solo a fine
  elaborazione: è la suddivisione a rendere visibile il progresso invece di
  una barra ferma per minuti. Ogni blocco completato viene salvato su
  disco, quindi chiudendo l'app o annullando si riprende da dove si era
  rimasti; i testi vengono ricuciti rimuovendo le frasi ripetute al
  confine. Il riassunto parte solo a trascrizione finita.
- **Contro le frasi ripetute** — whisper può entrare in un anello e
  ripetere la stessa frase per righe intere. Tre difese: il rilevatore del
  parlato Silero toglie i silenzi su cui gli anelli nascono, un passaggio
  di pulizia riduce a una copia le sequenze ripetute identiche, e il
  riassunto locale scarta le frasi quasi uguali a una già scelta.
- **Riassunto** — due motori: *locale* (estrattivo, offline, nessun dato
  esce) e *cloud* (modello linguistico via backend, solo dopo consenso
  esplicito). Se il cloud fallisce o la quota è esaurita si ripiega sul
  locale spiegando il motivo. Stili: breve, standard, dettagliato.
- **Lettura vocale** — sintesi nativa del sistema, nessun invio di testo.
- **Esportazione** — copia negli appunti, condivisione, file `.txt` o `.md`.
- **Cronologia** — SQLite locale, con ricerca, eliminazione singola o totale
  e possibilità di disattivarla.
- **Pubblicità** — banner AdMob discreti, mai durante l'elaborazione, con
  **ID di test** finché non si configurano i propri.

## Stato della verifica

| Elemento | Stato |
|----------|-------|
| `flutter analyze` | ✅ nessun problema |
| Test Dart (`flutter test`) | ✅ 123 test |
| Test backend (`pytest`) | ✅ 39 test |
| Build APK Android (debug) | ✅ compilata, con `libwhisper.so` e FFmpeg a bordo |
| Build APK Android (release, R8 attivo) | ✅ compilata: 41 MB per arm64-v8a |
| Build iOS | ❌ **non compilata**: richiede macOS e Xcode. Il codice e i file
della Share Extension ci sono, i passaggi manuali sono in [`docs/IOS.md`](docs/IOS.md) |

Per mettere online il backend (serve al cloud, non al funzionamento
locale): [`docs/DEPLOY.md`](docs/DEPLOY.md).

Cosa manca per andare in produzione: [`docs/ATTIVITA_RESIDUE.md`](docs/ATTIVITA_RESIDUE.md).
