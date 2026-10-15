# Architettura

## Quadro d'insieme

```
   WhatsApp / Telegram / Mail / File
                 │  (menu Condividi)
                 ▼
   ┌──────────────────────────────────────────┐
   │  ShareIntakeService                      │  copia il file nella
   │  (receive_sharing_intent + file_picker)  │  sandbox dell'app
   └───────────────┬──────────────────────────┘
                   ▼
   ┌──────────────────────────────────────────┐
   │  AudioValidator + MediaTools (ffprobe)   │  formato, durata, dimensione
   └───────────────┬──────────────────────────┘
                   ▼
   ┌──────────────────────────────────────────┐
   │  TranscriptionService                    │
   │   • planChunks  (taglio sui silenzi)     │  SUL DISPOSITIVO
   │   • ffmpeg → WAV 16 kHz mono             │  l'audio non esce mai
   │   • whisper.cpp (whisper_ggml)           │
   │   • TranscriptMerger                     │
   │   • JobStore (ripresa dopo interruzione) │
   └───────────────┬──────────────────────────┘
                   ▼  (solo testo)
   ┌──────────────────────────────────────────┐
   │  SummaryService                          │
   │   ├─ LocalSummarizer (estrattivo)        │  predefinito, offline
   │   └─ SummaryApiClient → backend FastAPI  │  solo con consenso
   └───────────────┬──────────────────────────┘
                   ▼
   ┌──────────────────────────────────────────┐
   │  NoteRepository (SQLite)  ·  TtsService  │
   │  ExportService  ·  AdsService            │
   └──────────────────────────────────────────┘
```

## Livelli dell'app

```
lib/
  core/          costanti, errori tipizzati, token di annullamento, formattazioni
  domain/        modelli immutabili (Note, SummaryBundle, AudioSource, stati)
  data/          SharedPreferences (impostazioni) e SQLite (cronologia)
  services/      un sottopacchetto per capacità:
    audio/         pianificazione blocchi, ffprobe/ffmpeg, ricucitura testi
    share/         ingresso dai menu di condivisione e import manuale
    transcription/ modelli whisper, orchestrazione, persistenza del lavoro
    summary/       riassunto locale, client del backend, scelta del motore
    tts/ export/ ads/
    pipeline/      ProcessingController: unisce tutto ed espone lo stato
  ui/            tema, schermate, widget condivisi
```

La regola seguita: **le decisioni sono in funzioni pure**, le chiamate native
stanno dietro una classe sottile. Per questo `planChunks`, `parseSilenceLog`,
`TranscriptMerger`, `LocalSummarizer` e `Fmt` sono verificabili senza
dispositivo, e `ProcessingController` è testabile sostituendo validatore e
trascrittore.

Non c'è un contenitore di dependency injection: i servizi sono costruiti in
`AppServices.bootstrap` e passati con un `InheritedWidget` (`ServicesScope`).
Per un'app di queste dimensioni è più leggibile di un framework.

## Trascrizione di audio lunghi

whisper.cpp elabora internamente finestre di 30 secondi, ma una singola
chiamata su 40 minuti è un blocco opaco: niente avanzamento utile, niente
annullamento, nessun recupero se il sistema sospende l'app. Perciò:

1. **Pianificazione** (`planChunks`) — oltre 8 minuti si creano blocchi di
   ~5 minuti. Il confine teorico viene spostato sul **silenzio più vicino**
   entro 25 secondi (`silencedetect` di ffmpeg), così non si taglia a metà
   una parola. Una coda inferiore a 30 secondi viene fusa nel blocco
   precedente.
2. **Estrazione** — ogni blocco diventa un WAV 16 kHz mono (il formato che
   whisper.cpp si aspetta), eliminato appena trascritto.
3. **Decodifica** — `maxContext: 0` fra blocchi distinti, così una frase
   allucinata non si propaga per tutta la registrazione. Il modello resta
   caricato in memoria fra un blocco e l'altro e viene liberato alla fine.
4. **Persistenza** — dopo ogni blocco lo stato va su disco (`JobStore`).
   Annullando o chiudendo l'app, alla riapertura compare "Riprendi".
5. **Ricucitura** (`TranscriptMerger`) — la coda del testo accumulato viene
   confrontata con la testa del blocco successivo (minimo 3 parole, a meno
   di maiuscole e punteggiatura) e la ripetizione viene rimossa.

## Sintesi

| | Locale | Cloud |
|---|---|---|
| Dove | sul telefono | backend → fornitore IA |
| Cosa esce dal dispositivo | niente | solo il testo trascritto |
| Metodo | estrattivo: frequenza dei termini, posizione, lunghezza; frasi d'azione per parole spia | modello linguistico, mappa/riduci sui testi lunghi |
| Consenso | non serve | esplicito, revocabile |

Il locale non inventa testo: riusa frasi della trascrizione (un test lo
verifica). Il cloud produce JSON con `summary`, `key_points`, `actions`.

La predisposizione per un **modello linguistico locale** futuro è il tipo
`SummaryEngine` più l'interfaccia di `SummaryService`: basta aggiungere una
terza implementazione, senza toccare né la UI né la pipeline.

## Backend

```
app/
  main.py        endpoint FastAPI, gestione errori, nessun log di contenuti
  config.py      tutta la configurazione da variabili d'ambiente
  quota.py       quote per dispositivo, limite di frequenza, tetto di spesa
  storage.py     contatori su SQLite (solo numeri, mai testi)
  chunking.py    suddivisione del testo (funzioni pure)
  prompts.py     prompt di mappa, riduzione e richiesta singola
  summarize.py   orchestrazione mappa/riduci e normalizzazione della risposta
  providers/     anthropic, openai (e compatibili), echo (sviluppo e test)
```

Difese sui costi, in ordine di intervento:

1. dimensione massima della richiesta (`max_input_chars`);
2. limite di frequenza per dispositivo (finestra scorrevole);
3. quota giornaliera per dispositivo (richieste e caratteri);
4. stima del costo di ogni risposta sui token realmente consumati;
5. **interruttore automatico**: superato il budget giornaliero il cloud si
   spegne e l'app ripiega sul riassunto locale;
6. interruttore manuale via `POST /admin/kill-switch`.

Il fornitore `echo` permette di far girare backend e test senza chiavi API e
senza rete.
