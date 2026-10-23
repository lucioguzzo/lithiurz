# GUZZOBOT — client AI nativo e multimodale per Android

App Android nativa (Kotlin + Jetpack Compose) con l'estetica e il flusso di una
chat moderna, ma **senza server intermedi**: parla direttamente con i provider di
modelli che configuri tu. Nessun account, nessun backend da mantenere, le
conversazioni e gli allegati restano sul telefono.

## Installare subito

In `android/dist/guzzobot-2.1.apk` c'è l'APK già compilato, firmato con la chiave
di debug. Scaricalo sul telefono, aprilo e consenti l'installazione da origini
sconosciute. Richiede Android 8.0 o superiore.

## Multimodale: come funziona davvero

Una app non può rendere multimodale un modello che non lo è. `deepseek-chat` e
`deepseek-reasoner` **accettano solo testo**: non vedono immagini, non sentono
audio, non generano immagini. GUZZOBOT risolve il problema instradando ogni
modalità al modello capace e riportando il risultato nella conversazione, così
puoi allegare qualsiasi cosa qualunque sia il modello in chat.

In **Impostazioni → Multimodale** assegni quattro ruoli:

| Ruolo | A cosa serve | Esempi |
| --- | --- | --- |
| Legge immagini e PDF scansionati | descrive le foto, fa OCR delle pagine | `gpt-4o`, `qwen/qwen2.5-vl-72b-instruct`, `mistralai/pixtral-large-2411` |
| Trascrive gli audio | note vocali e file audio | `whisper-large-v3-turbo` (Groq), `whisper-1` (OpenAI) |
| Genera immagini | crea immagini dal testo | `gpt-image-1`, `dall-e-3` |
| Legge ad alta voce | sintesi vocale delle risposte | `tts-1`, `gpt-4o-mini-tts` |

Cosa succede quando alleghi qualcosa:

- **Foto o immagine.** Se il modello in chat la vede (riconosciuto dal nome:
  GPT-4o, Pixtral, Qwen-VL, Gemini, Llama 4…) viene inviata direttamente come
  `image_url`, ridimensionata a 1152px e ruotata secondo l'EXIF. Altrimenti la
  legge il modello vision e la sua descrizione entra nel messaggio: DeepSeek
  ragiona su quella. La descrizione viene calcolata una volta sola e salvata.
- **Documento.** PDF, DOCX, XLSX, PPTX, testo, markdown, CSV, JSON, XML e i
  formati di codice vengono letti **sul telefono** e il testo entra nel
  messaggio, troncato a 120.000 caratteri: PDFBox per i PDF, lettura diretta
  dello zip XML per i formati Office. Di un XLSX si ricostruisce ogni foglio
  riga per riga, col nome del foglio e le celle separate da tabulazione; di un
  PPTX il testo di ogni slide più le note del relatore. Se un PDF è scansionato
  e non contiene testo, le pagine vengono renderizzate come immagini e
  trascritte dal modello vision.
- **Audio.** Registri una nota vocale col microfono o alleghi un file: viene
  trascritto dal modello di trascrizione e il testo entra nel messaggio.
- **Video.** Viene smontato in due parti sul telefono: da 2 a 8 **fotogrammi**
  distribuiti sulla durata e la **traccia audio**, estratta senza
  ricodificarla. I fotogrammi vanno al modello vision in un'unica richiesta (in
  ordine cronologico, così può raccontare cosa accade) o diretti al modello in
  chat se li vede; l'audio va al modello di trascrizione. Nel messaggio
  finiscono entrambi: descrizione del video e parlato. Limiti: 400 MB per file,
  24 MB per la traccia audio da trascrivere.
- **Genera immagine.** Dal menu `+`: il testo nel composer diventa il prompt,
  l'immagine prodotta resta nella conversazione e si condivide da un tocco.
- **Ascolta.** Sotto ogni risposta: usa il modello di voce se configurato,
  altrimenti la sintesi vocale del telefono (gratis e offline).
- **Condividi verso GUZZOBOT.** L'app compare nel menu "Condividi" di Android
  per testo, immagini, PDF, audio e video.

Per non far esplodere i costi, nella richiesta viaggiano al massimo le 8
immagini più recenti (fotogrammi compresi); le più vecchie restano come
riferimento testuale. Descrizioni e trascrizioni si calcolano una volta sola e
restano salvate col messaggio.

## Il resto

- **Chat in streaming**: risposta parola per parola, pulsante di interruzione che
  conserva la parte già ricevuta, rigenerazione, modifica di un tuo messaggio
  (riapre testo e allegati nel composer e taglia la conversazione da lì).
- **Qualsiasi provider compatibile con l'API OpenAI**. Preconfigurati: DeepSeek,
  OpenRouter, Groq, Mistral, xAI, OpenAI, Ollama in locale. Puoi aggiungerne
  altri con nome, base URL e modelli, o premere *Scarica elenco modelli*.
- **Blocco "Ragionamento"** richiudibile per i modelli che lo espongono
  (`deepseek-reasoner` e i reasoning su OpenRouter).
- **Markdown renderizzato**: titoli, elenchi, citazioni, link, blocchi di codice
  con etichetta del linguaggio e copia rapida.
- **Storico conversazioni** con ricerca, rinomina, condivisione, cancellazione.
- **Impostazioni**: prompt di sistema, temperatura, token massimi, messaggi di
  contesto, tema chiaro/scuro/sistema, visibilità del ragionamento.

## Privacy

Le chiavi API stanno in `EncryptedSharedPreferences`, cifrate con una chiave
custodita dal Keystore hardware, ed escluse dai backup. Gli allegati vivono nella
cartella privata dell'app; l'estrazione del testo dai documenti avviene sul
telefono. Le richieste vanno solo agli host che hai configurato: il traffico in
chiaro (HTTP) è permesso soltanto verso `localhost` e `10.0.2.2`, per usare
Ollama o LM Studio sulla propria macchina.

## Primo avvio

1. Apri **Impostazioni** dal pannello laterale.
2. Scegli il provider, per esempio **DeepSeek**, incolla la chiave API
   (<https://platform.deepseek.com/api_keys>) e premi **Salva e usa**.
3. Per la multimodalità aggiungi la chiave di almeno un provider con modelli
   vision/whisper — **Groq** è la via più economica per la trascrizione,
   **OpenRouter** per i modelli vision — e assegna i ruoli in
   **Impostazioni → Multimodale**.

Il nome del modello è testo libero: se il provider pubblica un modello nuovo
basta aggiungerlo all'elenco del provider, senza aggiornare l'app.

## Compilare

Serve una JDK 17+ e l'SDK Android (`platforms;android-35`, `build-tools;35.0.0`).

```bash
cd android
echo "sdk.dir=/percorso/android-sdk" > local.properties
./gradlew test assembleRelease   # APK in app/build/outputs/apk/release/
```

L'APK di release è firmato con la chiave di debug, così si installa subito da
file. Per pubblicarlo su Play Store va sostituito con una firma propria in
`app/build.gradle.kts`.

In alternativa, su GitHub: **Actions → Build GUZZOBOT APK → Run workflow**, poi
scarica l'artifact `guzzobot-apk`.

## Struttura

```
android/app/src/main/java/com/lucio/guzzobot/
├─ GuzzoBotApp.kt          Application + container delle dipendenze
├─ MainActivity.kt         edge-to-edge, tema, intent di condivisione
├─ data/
│  ├─ model/               provider, preset, impostazioni, allegati, capacità
│  ├─ local/               Room: conversazioni, messaggi, allegati
│  ├─ prefs/               DataStore (impostazioni) + chiavi cifrate
│  ├─ files/               import allegati, estrazione testo, PDF e video→immagini
│  ├─ media/               registratore, player, sintesi vocale di sistema
│  ├─ remote/              chat in streaming, vision, whisper, immagini, voce
│  └─ repo/                ChatRepository
└─ ui/
   ├─ theme/               palette crema/terracotta, tipografia serif
   ├─ chat/                chat, composer, allegati, drawer, ViewModel
   ├─ markdown/            parser e renderer markdown
   └─ settings/            impostazioni, ruoli multimodali, editor provider
```

## Personalizzare

- **Nome e icona**: `app_name` in `res/values/strings.xml`,
  `res/drawable/ic_launcher_foreground.xml`, colore in `res/values/colors.xml`.
- **Colori**: `ui/theme/Color.kt` e `Theme.kt`.
- **Provider preconfigurati**: `data/model/Models.kt`.
- **Modelli riconosciuti come vision**: `ModelCapabilities` in
  `data/model/Attachment.kt` (o attiva l'interruttore nelle impostazioni).

## Limiti noti

Le tabelle markdown sono mostrate come testo semplice. Un video viene capito dai
fotogrammi e dal parlato, non fotogramma per fotogramma: movimenti rapidi tra
due campioni possono sfuggire. L'audio viene sempre trascritto, non inviato come
forma d'onda ai modelli che accettano audio nativo. I vecchi formati `.doc`,
`.xls` e `.ppt` (pre-2007, non zip) non vengono letti. Nessuna sincronizzazione
tra dispositivi, per scelta: se disinstalli l'app, le conversazioni se ne vanno
con lei.
