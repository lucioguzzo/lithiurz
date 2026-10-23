# GUZZOBOT — client AI nativo e multimodale per Android

App Android nativa (Kotlin + Jetpack Compose) con l'estetica e il flusso di una
chat moderna, ma **senza server intermedi**: parla direttamente con i provider di
modelli che configuri tu. Nessun account, nessun backend da mantenere, le
conversazioni e gli allegati restano sul telefono.

## Installare subito

In `android/dist/guzzobot-3.0.apk` c'è l'APK già compilato: un file unico,
autosufficiente, firmato con la chiave di release del progetto. Scaricalo sul
telefono, aprilo e consenti l'installazione da origini sconosciute. Richiede
Android 8.0 o superiore.

È un APK **universale**: contiene il codice per tutte le architetture
(`arm64-v8a`, `armeabi-v7a`, `x86`, `x86_64`), quindi si installa su qualsiasi
telefono o emulatore senza scegliere la variante giusta. Non è un App Bundle e
non ha bisogno di Play Store, account, companion app o altri file.

> **Chi arriva dalla 2.2 o precedenti deve prima disinstallare GUZZOBOT.**
> Quelle versioni erano firmate con la chiave di debug; Android rifiuta un
> aggiornamento firmato con una chiave diversa (*"App non installata"*).
> Dalla 2.3 in poi gli aggiornamenti si installano sopra, senza disinstallare.

### La chiave di firma

L'APK è firmato con una chiave RSA 4096 dedicata (schemi v2 e v3; la v1 serve
solo sotto Android 7). Android accetta un aggiornamento solo se è firmato con la
**stessa** chiave: se la perdi, l'unico modo di aggiornare l'app è
disinstallarla, perdendo le conversazioni salvate.

La chiave **non sta nel repository** — un file `.jks` con la password accanto,
spinto su un remoto, è un segreto esposto. Vive in due file ignorati da git:

```
android/keystore/guzzobot-release.jks
android/keystore.properties        # storeFile, storePassword, keyAlias, keyPassword
```

Tienili da parte (password manager, backup cifrato). Per ricompilare da un'altra
macchina basta rimetterli in quei percorsi: la build li trova da sola. Senza di
essi il progetto compila comunque, ripiegando sulla chiave di debug — ma l'APK
che ne esce **non** si installa come aggiornamento sopra questo.

In GitHub Actions la chiave arriva da quattro segreti del repository, senza mai
finire nel codice: `GUZZOBOT_KEYSTORE_BASE64` (il `.jks` in base64),
`GUZZOBOT_STORE_PASSWORD`, `GUZZOBOT_KEY_ALIAS`, `GUZZOBOT_KEY_PASSWORD`. Il
workflow ricostruisce il file prima della build e, se i segreti non ci sono,
procede con la chiave di debug.

## Area di lavoro: far lavorare GUZZOBOT sui file

Oltre a rispondere, GUZZOBOT può **leggere e scrivere file**. Il modello non
tocca nulla da solo: chiede uno strumento, l'app lo esegue e gli restituisce il
risultato. Gli strumenti sono cinque — elencare, leggere, scrivere (più file in
una volta), eliminare, cercare — e richiedono un modello che supporti i *tool
calls*: DeepSeek, OpenAI, Groq e Mistral lo fanno.

Due aree possibili, in **Impostazioni → Area di lavoro**:

- **Cartella del telefono.** La scegli col selettore di sistema (Storage Access
  Framework) e il permesso è persistente: resta valido dopo il riavvio. L'app
  non vede niente fuori da quell'albero, e i percorsi con `..` vengono rifiutati.
- **Repository GitHub.** Serve un token fine-grained con permesso di scrittura
  sui contenuti. Legge l'albero e i file (oltre 1 MB passa dai blob) e scrive
  **un solo commit** anche quando il modello cambia più file insieme: blob →
  albero → commit → spostamento del branch, con la Git Data API.

### Chi decide

Di default **ogni scrittura o eliminazione passa da una conferma**, con il
percorso e l'anteprima del contenuto. Dalla finestra puoi consentire la singola
operazione, rifiutarla, oppure dire *"consenti sempre in questa chat"* — che
vale solo per la conversazione in corso. In Impostazioni c'è anche
l'interruttore **"Lascialo operare in autonomia"**, che toglie la conferma
ovunque: letture e ricerche non la chiedono mai, perché non cambiano niente.

### Lavorare in background

Con **"Continua a lavorare in background"** attivo, una risposta lunga o una
serie di operazioni proseguono a schermo spento, con una notifica persistente.
Il limite non è dell'app ma di Android: un servizio di tipo `dataSync` ha **sei
ore ogni ventiquattro**, poi il sistema chiama `onTimeout()` e va fermato.
Un agente che lavora col telefono spento non è possibile da un'app Android:
servirebbe un server.

## Multimodale: come funziona davvero

Una app non può rendere multimodale un modello che non lo è. `deepseek-chat` e
`deepseek-reasoner` **accettano solo testo**: non vedono immagini, non sentono
audio, non generano immagini. GUZZOBOT risolve il problema instradando ogni
modalità al modello capace e riportando il risultato nella conversazione, così
puoi allegare qualsiasi cosa qualunque sia il modello in chat.

In **Impostazioni → Multimodale** assegni quattro ruoli:

> Nota sui modelli DeepSeek: `deepseek-chat` e `deepseek-reasoner` sono stati
> ritirati. I preset ora puntano a **`deepseek-flash`** (DeepSeek-V4.1-Flash) e
> **`deepseek-v4-pro`**; il primo **vede le immagini**, quindi con quello le
> foto vanno dirette al modello in chat senza passare da un modello-ponte.

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
- **Documento.** PDF, DOCX, XLSX, PPTX, **DOC, XLS, PPT**, testo, markdown,
  CSV, JSON, XML e i formati di codice vengono letti **sul telefono** e il testo
  entra nel messaggio, troncato a 120.000 caratteri. PDFBox per i PDF, lettura
  diretta dello zip XML per i formati Office moderni, un lettore OLE2 scritto
  qui per quelli pre-2007 (vedi sotto). Di un foglio di calcolo si ricostruisce
  ogni scheda riga per riga, col nome della scheda e le celle separate da
  tabulazione; di una presentazione il testo di ogni slide più le note del
  relatore. Se un PDF è scansionato e non contiene testo, le pagine vengono
  renderizzate come immagini e trascritte dal modello vision.
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

## I vecchi formati Office

`.doc`, `.xls` e `.ppt` (Office 97-2003) non sono zip ma contenitori binari
OLE2: un piccolo file system con settori, tabella di allocazione e directory.
GUZZOBOT li legge con un parser scritto per l'app, senza dipendenze aggiuntive
(Apache POI non è utilizzabile su Android):

- **.doc** — il flusso `WordDocument` contiene i caratteri, ma l'ordine vero sta
  nella *piece table* del flusso `Table`, perché Word salva in modo
  incrementale. Il parser la segue pezzo per pezzo, gestendo sia i pezzi a un
  byte (CP1252) sia quelli UTF-16, e ripulisce i caratteri di controllo: fine
  cella, interruzioni, codici di campo. Se la piece table manca, ripiega sul
  testo principale da `fcMin` a `fcMac`.
- **.xls** — il flusso `Workbook` è una sequenza di record BIFF8: nomi delle
  schede dai `BOUNDSHEET`, testi dalla tabella condivisa `SST` (ricomposta
  attraverso i record `CONTINUE`, dove una stringa spezzata riparte con un
  nuovo byte di opzioni), valori dai record `LABELSST`, `LABEL`, `RK`, `MULRK`,
  `NUMBER`, `BOOLERR` e `FORMULA`.
- **.ppt** — albero di record: si leggono solo i container delle slide, si
  saltano i master (che contengono i segnaposto "Click to edit…") e le note
  vengono legate alla slide giusta tramite il loro identificatore.

I test usano `.doc`, `.xls` e `.ppt` **veri**, prodotti dai filtri "MS Office
97" di LibreOffice, più un documento Word costruito con due pezzi in ordine
invertito per verificare la piece table.

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
- **Testo selezionabile**: tieni premuto su una domanda o una risposta per
  prendere solo il pezzo che ti serve, con la barra di copia di Android. I
  blocchi di codice hanno anche il loro pulsante di copia.
- **Indietro per gradi**: il tasto indietro (o il gesto) chiude prima il
  pannello laterale, poi ferma una risposta in corso, poi riporta all'elenco
  delle conversazioni conservando la bozza nel composer; solo dalla schermata
  iniziale, e dopo un avviso, chiude l'app.
- **Apertura** con il nome, il glifo e il copyright, che svanisce sulla chat; un
  tocco la salta.
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

L'APK di release viene firmato con la chiave in `keystore/`, o con quella di
debug se la chiave non c'è (così il progetto compila anche per chi clona senza
di essa). La minificazione R8 è spenta di proposito: PDFBox, Room e
kotlinx.serialization usano la reflection e qui non c'è un dispositivo su cui
verificare lo shrinking, quindi un APK di 21 MB è preferibile a una sorpresa a
runtime.

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
│  ├─ workspace/           cartella locale, repository GitHub, strumenti
│  ├─ files/               import allegati, estrazione testo, PDF e video→immagini
│  │  └─ office/           lettore OLE2 e parser .doc, .xls, .ppt
│  ├─ media/               registratore, player, sintesi vocale di sistema
│  ├─ remote/              chat in streaming, vision, whisper, immagini, voce
│  └─ repo/                ChatRepository
├─ service/                servizio in primo piano per il lavoro in background
└─ ui/
   ├─ theme/               palette crema/terracotta, tipografia serif
   ├─ chat/                chat, composer, allegati, drawer, ViewModel
   ├─ markdown/            parser e renderer markdown
   └─ settings/            impostazioni, ruoli multimodali, editor provider
```

## Personalizzare

- **Nome e icona**: `app_name` in `res/values/strings.xml`; il marchio è
  `res/drawable-nodpi/logo_mark.png` (splash e intestazione del pannello) e
  `res/mipmap-*/ic_launcher_foreground.png` (icona), su fondo
  `ic_launcher_background` in `res/values/colors.xml`.
- **Colori**: `ui/theme/Color.kt` e `Theme.kt`.
- **Provider preconfigurati**: `data/model/Models.kt`.
- **Modelli riconosciuti come vision**: `ModelCapabilities` in
  `data/model/Attachment.kt` (o attiva l'interruttore nelle impostazioni).

## Limiti noti

Le tabelle markdown sono mostrate come testo semplice. Un video viene capito dai
fotogrammi e dal parlato, non fotogramma per fotogramma: movimenti rapidi tra
due campioni possono sfuggire. L'audio viene sempre trascritto, non inviato come
forma d'onda ai modelli che accettano audio nativo. Dei vecchi formati Office si
estrae il testo, non la formattazione: di un `.xls` si perdono formule (resta il
valore calcolato) e formati numerici, di un `.ppt` la disposizione delle forme.
Nessuna sincronizzazione tra dispositivi, per scelta: se disinstalli l'app, le
conversazioni se ne vanno con lei.
