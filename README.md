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
| `docs/`     | Architettura, compilazione, iOS, privacy, pubblicazione, attività residue |
| `app/`      | Progetto precedente (Baby Cry Translator), non toccato |

## Avvio rapido

```bash
# App
cd brief
flutter pub get
flutter test                 # 121 test unitari
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
- **Due motori di trascrizione**, a scelta nelle impostazioni, entrambi
  gratuiti e offline:
  - **Motore del telefono** — il riconoscitore vocale di sistema, lo stesso
    che usano le app di trascrizione del dispositivo. Molto più rapido
    perché ottimizzato per quel chip. Richiede Android 13+ (da lì
    `SpeechRecognizer` legge da file, non solo dal microfono) o iOS con la
    lingua scaricata; dove manca, l'opzione è disattivata e l'app spiega
    perché.
  - **Whisper** — whisper.cpp con il modello **Small quantizzato q5_1**
    (181 MB invece di 465, circa il doppio più rapido del modello pieno).
    Funziona su qualunque telefono. Tiny e Base erano offerti all'inizio
    ma sul parlato italiano reale sbagliavano troppo: un riassunto
    costruito su una trascrizione sbagliata è peggio di nessun riassunto.
    Chi li aveva scaricati se li vede eliminare al primo avvio.
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
| Test Dart (`flutter test`) | ✅ 121 test |
| Test backend (`pytest`) | ✅ 39 test |
| Build APK Android (debug) | ✅ compilata, con `libwhisper.so` e FFmpeg a bordo |
| Build APK Android (release, R8 attivo) | ✅ compilata: 41 MB per arm64-v8a |
| Build iOS | ❌ **non compilata**: richiede macOS e Xcode. Il codice e i file
della Share Extension ci sono, i passaggi manuali sono in [`docs/IOS.md`](docs/IOS.md) |

Cosa manca per andare in produzione: [`docs/ATTIVITA_RESIDUE.md`](docs/ATTIVITA_RESIDUE.md).
