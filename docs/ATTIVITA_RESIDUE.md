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
| `flutter test` (90 test) | superati |
| `pytest` backend (39 test) | superati |
| Build APK Android (debug) | compilata e ispezionata, anche dopo il rinomino in BRIEF |
| Build APK Android (release con R8, split per ABI) | compilata: 41-51 MB per architettura |
| Backend avviato davvero (uvicorn + richieste HTTP reali) | funzionante; nei log nessun contenuto |
| `docker build` del backend | non eseguito qui (manca il demone Docker); lo fa la CI |
| Build iOS | non eseguita (serve macOS) |
| Trascrizione su audio reale | non eseguita (serve un dispositivo) |
