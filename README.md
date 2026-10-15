# VoceNota — trascrizione e riassunto di audio (Android + iOS)

App Flutter che riceve un audio dal menu **Condividi** di WhatsApp, Telegram,
email o file manager e restituisce **trascrizione**, **riassunto**, **punti
principali** e **azioni da svolgere**, con lettura vocale, copia,
condivisione, esportazione e cronologia locale.

La trascrizione avviene **sul dispositivo** con whisper.cpp: l'audio non
lascia mai il telefono. Solo il riassunto, e solo se l'utente acconsente,
passa dal backend — che riceve **testo, mai audio**.

> In questo repository convive anche il progetto precedente
> **Baby Cry Translator** (cartella `app/`), lasciato invariato.

## Struttura

| Cartella    | Contenuto |
|-------------|-----------|
| `vocenota/` | App Flutter (Android + iOS) |
| `backend/`  | Servizio FastAPI per i riassunti, con quote e tetto di spesa |
| `docs/`     | Architettura, compilazione, iOS, privacy, pubblicazione, attività residue |
| `app/`      | Progetto precedente (Baby Cry Translator), non toccato |

## Avvio rapido

```bash
# App
cd vocenota
flutter pub get
flutter test                 # 85 test unitari
flutter build apk --debug    # Android

# Backend
cd ../backend
pip install -r requirements-dev.txt
python -m pytest             # 39 test
uvicorn app.main:app --reload
```

Dettagli completi in [`docs/COMPILAZIONE.md`](docs/COMPILAZIONE.md).
Se vuoi solo l'APK senza compilare nulla sul tuo computer:
[`COME_OTTENERE_APK_VOCENOTA.md`](COME_OTTENERE_APK_VOCENOTA.md).

## Che cosa fa, in concreto

- **Ingresso condiviso** — intent `ACTION_SEND` / `ACTION_SEND_MULTIPLE` su
  Android, Share Extension su iOS, più import manuale dal file manager. Il
  file viene copiato subito nella sandbox dell'app, perché l'URI condiviso
  può sparire appena l'app di origine si chiude.
- **Controlli preventivi** — formato, dimensione (max 500 MB), durata (max 4
  ore) e presenza di una traccia audio, letti con ffprobe.
- **Trascrizione locale** — whisper.cpp (`whisper_ggml`) con modelli Tiny,
  Base e Small multilingua. Il modello si scarica una volta e poi funziona
  offline; quello consigliato dipende dalla RAM del dispositivo.
- **Audio lunghi** — oltre 8 minuti l'audio viene diviso in blocchi da ~5
  minuti, tagliati **sui silenzi** per non spezzare le parole. Ogni blocco
  completato viene salvato su disco: chiudendo l'app o annullando si riprende
  da dove si era rimasti. I testi dei blocchi vengono ricuciti rimuovendo le
  frasi ripetute al confine.
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
| Test Dart (`flutter test`) | ✅ 85 test |
| Test backend (`pytest`) | ✅ 38 test |
| Build APK Android (debug) | ✅ compilata, con `libwhisper.so` e FFmpeg a bordo |
| Build iOS | ❌ **non compilata**: richiede macOS e Xcode. Il codice e i file
della Share Extension ci sono, i passaggi manuali sono in [`docs/IOS.md`](docs/IOS.md) |

Cosa manca per andare in produzione: [`docs/ATTIVITA_RESIDUE.md`](docs/ATTIVITA_RESIDUE.md).
