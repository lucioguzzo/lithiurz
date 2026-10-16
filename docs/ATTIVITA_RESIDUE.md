# Attività residue

Elenco onesto di ciò che manca o non è stato verificato. In ordine di
importanza.

## Bloccanti per la pubblicazione

| # | Attività | Perché serve |
|---|----------|--------------|
| 1 | **Creare il target Share Extension in Xcode** ([IOS.md](IOS.md)) | Senza, su iOS il menu Condividi non mostra l'app. Richiede macOS |
| 2 | **Compilare e provare su iOS** | Qui non è stato possibile: niente macOS. Nessuna riga di iOS è stata eseguita |
| 3 | **Prove su dispositivi Android reali** | Nel container non c'è né emulatore né telefono: la trascrizione vera non è stata eseguita |
| 4 | **Keystore di firma Android** (`android/key.properties`) | Senza, la release usa le chiavi di debug e lo store la rifiuta |
| 5 | **Account AdMob e identificativi veri** | Ora ci sono gli ID di test; pubblicare con quelli non genera ricavi |
| 6 | **Icone e schermata di avvio** | Oggi ci sono quelle predefinite di Flutter |
| 7 | **Informativa privacy pubblicata su web** | Google e Apple pretendono un URL raggiungibile; il testo è già in `PRIVACY.md` e nell'app |
| 8 | **Distribuire il backend** e puntarci l'app (`BRIEF_BACKEND_URL`) | Senza, il riassunto cloud non è disponibile e l'app ripiega sul locale |

## Motori di trascrizione: stato delle prove

- **Motore di sistema su Android — provato, bocciato.** Il codice Kotlin
  (`SystemSpeechPlugin`) usa `SpeechRecognizer` su dispositivo con sessione
  segmentata e sorgente da file (API da Android 13). Provato su un nubia
  NX713J con Android 13: trascrive solo i primi secondi di ogni file. Su
  36 secondi di audio ha prodotto **15 parole**, contro le 127 di Whisper
  sullo stesso file. Tre tentativi di correzione, tutti nel codice
  attuale, non hanno cambiato il risultato:
  1. ignorare `onPartialResults` (sono revisioni cumulative, non testo
     nuovo) e accumulare solo `onSegmentResults`;
  2. non chiudere la sessione su `onResults`, che in modalità segmentata
     arriva a ogni segmento, ma attendere `onEndOfSegmentedSession`;
  3. portare `EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS` e le
     soglie gemelle a 600 000 ms e ridurre gli spezzoni a 25 secondi.
  Conclusione: su quel telefono l'implementazione OEM applica comunque un
  proprio limite di sessione. Il motore è stato **retrocesso a opzione
  sperimentale** (interruttore nascosto nelle impostazioni, con avviso;
  migrazione una tantum che riporta a Whisper chi lo aveva scelto) e non
  riceverà altro lavoro finché non si potrà provarlo su altri dispositivi.
  Chi volesse riprenderlo parta da lì: serve una prova su almeno tre
  telefoni di marche diverse per capire se il limite è di quel chip o
  dell'API.
- **Motore di sistema su iOS** — `SystemSpeechPlugin.swift` con
  `SFSpeechRecognizer` e `requiresOnDeviceRecognition`. **Mai compilato**:
  serve un Mac. Vale la stessa cautela: va considerato sperimentale finché
  non è provato su un dispositivo vero.
- **Accuratezza di Whisper Small** — sul parlato italiano reale il testo è
  comprensibile ma contiene errori di parola. Il modello è passato da
  `small-q5_1` (181 MB) a **`small-q8_0`** (252 MB): quantizzazione più
  leggera, dettagli dei pesi meglio conservati. **Il guadagno non è ancora
  misurato**: serve riascoltare lo stesso audio con entrambi e contare gli
  errori. Se non bastasse, le leve rimaste sono il riassunto nel cloud (il
  backend è scritto, va distribuito: compensa gli errori ragionando sul
  testo) e il modello `medium` (1,5 GB, tempi tripli: sconsigliato su
  telefono).

## Importanti, non bloccanti

- **Messaggio di consenso UMP/GDPR per la pubblicità** — AdMob in Europa
  richiede una CMP (Google User Messaging Platform). Oggi non è integrata:
  `google_mobile_ads` la supporta, va aggiunta prima di pubblicare in UE.
- **Attestazione del dispositivo** (Play Integrity / DeviceCheck) — oggi le
  quote del backend si appoggiano a un identificativo casuale generato
  dall'app: basta a limitare gli abusi casuali, non uno determinato.
- **Esclusione dei modelli dal backup su iOS** — `whisper_ggml` salva i
  modelli ggml (da 77 MB a 488 MB) in `Library/`, che iOS include nel
  backup iCloud. Apple chiede che i dati riscaricabili siano esclusi: serve
  impostare `isExcludedFromBackup` sulla cartella dei modelli da codice
  Swift all'avvio. Da fare prima di inviare l'app alla revisione.
- **Prove di durata** — un audio di 60+ minuti su telefono di fascia bassa:
  misurare memoria, tempi e comportamento quando il sistema sospende l'app.
- **Notifica persistente su Android** durante l'elaborazione (foreground
  service): permetterebbe di uscire dall'app senza interrompere il lavoro.
  Oggi l'app chiede di restare in primo piano e, se interrotta, riprende.
- **Traduzioni** — l'interfaccia è solo in italiano; nessuna infrastruttura
  di localizzazione (`flutter_localizations`/ARB) è stata introdotta.
- **Test di interfaccia** (widget test) — esistono i test della logica, non
  quelli delle schermate.
- **Accessibilità** — testi scalabili e contrasti sono a posto per
  costruzione (Material 3), ma manca una prova con TalkBack/VoiceOver.

## Migliorie possibili

- Diarizzazione (chi parla) — `whisper_ggml` la offre solo in inglese con il
  modello `small.en-tdrz`.
- Modello linguistico **locale** per il riassunto: l'architettura è già
  predisposta (`SummaryEngine`), manca l'implementazione.
- Modifica manuale della trascrizione prima del riassunto.
- Esportazione in PDF e invio diretto a note/calendario.
- Cache del riassunto per stile, per non richiamare il cloud due volte sullo
  stesso testo.

## Note sulle verifiche già fatte

| Verifica | Esito |
|---|---|
| `flutter analyze` | nessun problema |
| `flutter test` (123 test) | superati |
| `pytest` backend (39 test) | superati |
| Build APK Android (debug) | compilata e ispezionata, anche dopo il rinomino in BRIEF |
| Build APK Android (release con R8, split per ABI) | compilata: 41-51 MB per architettura |
| Backend avviato davvero (uvicorn + richieste HTTP reali) | funzionante; nei log nessun contenuto |
| `docker build` del backend | non eseguito qui (manca il demone Docker); lo fa la CI |
| Build iOS | non eseguita (serve macOS) |
| Trascrizione su audio reale | non eseguita (serve un dispositivo) |
