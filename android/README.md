# GUZZOBOT — client AI nativo e multimodale per Android

App Android nativa (Kotlin + Jetpack Compose) con l'estetica e il flusso di una
chat moderna, ma **senza server intermedi**: parla direttamente con i provider di
modelli che configuri tu. Nessun account, nessun backend da mantenere, le
conversazioni e gli allegati restano sul telefono.

## Installare subito

In `android/dist/guzzobot-5.6.apk` c'è l'APK già compilato: un file unico,
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

## Il primo avvio

GUZZOBOT non ha server propri, quindi senza un modello configurato non può dire
niente: è il muro su cui si ferma chi non ha voglia di smanettare. Al primo
lancio ci sono tre schermate per smontarlo, e **ognuna si salta**.

1. **Cos'è** — che l'app parla direttamente col modello che scegli tu, che le
   conversazioni restano sul telefono, e che si può partire gratis.
2. **Partire gratis con Gemini** — il pulsante apre la pagina della chiave nel
   browser del telefono, dove **l'account Google è già connesso**: nessun
   accesso da rifare. Si tocca *Crea chiave API*, si copia, si torna. Al
   ritorno l'app **guarda gli appunti** e, se trovano qualcosa con la forma di
   una chiave Google, la propone con un tocco: niente da trascrivere. Poi la
   prova davvero contro il provider e dice quanti modelli ha trovato, o il
   motivo vero del rifiuto.
3. **Due permessi** — notifiche e microfono, spiegati per quello che fanno,
   chiesti uno alla volta. Android concede una sola richiesta dopo un rifiuto:
   da quel momento l'app manda alle impostazioni di sistema, invece di
   ripresentare una finestra che non comparirà più. Per documenti e foto non
   serve nessun permesso: il file lo scegli ogni volta tu, e l'app vede solo
   quello.

Chi aggiorna avendo già una chiave configurata non lo vede: per lui il primo
avvio è già passato.

### Perché non c'è un «accedi con Google»

Sarebbe la cosa più comoda e **non è possibile**, non per pigrizia nostra.

- Le chiavi di Google AI Studio si creano a mano nella console di Google;
  **non esiste nessun OAuth né API che permetta a un'app di terzi di farsene
  rilasciare una** per conto dell'utente. Google ha anche chiuso, nel giugno
  2026, l'accesso «Login with Google» che la CLI di Gemini usava.
- L'alternativa, Vertex AI, si autentica contro il progetto Google Cloud **di
  chi sviluppa l'app**, con fatturazione attiva: l'uso finirebbe sul nostro
  conto, non sarebbe gratuito per nessuno.
- E un'app non verificata che chiede l'accesso all'account Google spaventerebbe
  di più di quanto semplifichi, ottenendo in cambio niente di utilizzabile.

Quindi la chiave resta da creare. Quello che si può togliere — trovare la
pagina, capire cosa premere, trascrivere quaranta caratteri — è tolto.

## Partire gratis

*Impostazioni › Modelli gratuiti* esiste perché l'app senza una chiave non
parla con niente, e questo è un muro brutto al primo avvio.

Una precisazione che va fatta subito, però: **il gratuito senza chiave, nel
cloud, non esiste** — chi regala calcolo senza identificare nessuno viene
svuotato in ore. Quello che si abbrevia qui è l'iscrizione, non la credenziale:
per ogni provider c'è il pulsante che apre la pagina giusta, il campo in cui
incollare, e poi l'app chiede da sé a `/models` quali modelli esistono davvero.
Nessuna carta di credito, in nessuno dei casi.

| Provider | Cosa dà di gratuito | Verificato su |
|---|---|---|
| **Google AI Studio (Gemini)** | I modelli Flash in chat, **gratis** e senza carta; l'unico gratuito che **vede** le immagini. **Generarle no.** Sul piano gratuito i dati sono usati per migliorare i prodotti Google | [listino Gemini](https://ai.google.dev/gemini-api/docs/pricing) |
| **Groq** | Piano gratuito senza carta, trascrizione audio inclusa. I limiti non sono pubblicati: stanno nella console | [FAQ fatturazione Groq](https://console.groq.com/docs/billing-faqs) |
| **OpenRouter** | I modelli col suffisso `:free` — 20 richieste al minuto, 50 al giorno | [limiti OpenRouter](https://openrouter.ai/docs/api-reference/limits) |
| **Z.ai (GLM)** | **Solo** GLM-4.7-Flash, GLM-4.5-Flash e GLM-4.6V-Flash. GLM-5.3-Flash si paga, nonostante il nome | [listino Z.ai](https://docs.z.ai/guides/overview/pricing) |
| **Mistral** | Piano «Free» attivo di default, senza carta. I limiti stanno nel pannello | [attivazione Studio](https://docs.mistral.ai/getting-started/quickstarts/studio/activate-and-generate-api-key) |
| **Pollinations** | **Immagini senza alcuna chiave**: una alla volta, 45 secondi d'attesa, col logo, a un indirizzo pubblico | [documentazione API](https://github.com/pollinations/pollinations/blob/master/APIDOCS.md) |
| **Ollama** | Senza chiave, ma vuole un computer acceso nella stessa rete | — |

**Cerebras c'era e non c'è più.** I suoi documenti dicono che non esiste più
una quota a costo zero rinnovabile, e che i cinque dollari di prova arrivano
solo dopo aver inserito un metodo di pagamento. In una sezione che promette
«nessuna carta di credito» non può starci, quindi resta fra i provider ma
fuori dai gratuiti.

L'avviso in cima alla schermata non si chiude, e dice cosa si rompe davvero in
GUZZOBOT, non un generico "sono più lenti": i limiti al minuto e al giorno che
un obiettivo dell'agente brucia in fretta; le immagini che solo Gemini accetta;
la generazione di immagini, che gratis esiste solo con Pollinations — nemmeno
Gemini la dà, le fa pagare tutte — e la lettura ad alta voce, che gratis non
esiste da nessuna parte; il tool calling
irregolare, che può far sbagliare agente e connettori; e dove accade, i prompt
usati per addestrare.

Nel selettore dei modelli ogni voce gratuita porta l'etichetta *gratuito*, come
già accade per *vede le immagini*. E al primo avvio senza nessuna chiave la
schermata di benvenuto manda qui, invece di mostrare un elenco di provider
vuoti.

Due cose che questa sezione **non** fa, per scelta: non include endpoint
"senza registrazione" trovati in giro — sono quasi sempre rivendita della
capacità di qualcun altro, possono registrare i prompt e spariscono senza
preavviso — e non contiene una chiave dello sviluppatore, che verrebbe estratta
dall'APK il primo giorno. I piani gratuiti chiudono: **GitHub Models è stato
ritirato il 30 luglio 2026**, e metà dei siti che elencano le "API gratuite" lo
pubblicizza ancora.

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

### Modalità agente

L'interruttore **Agente**, accanto al composer, cambia il modo di lavorare: il
messaggio non è una domanda ma un **obiettivo**. L'agente esplora, decide,
agisce, controlla il risultato e corregge, da solo, finché non dichiara di aver
finito (`agent_finish`), si blocca e chiede (`agent_blocked`), o esaurisce il
tetto di sicurezza: **60 passi, 40 giri di modello, 25 minuti**, con stop
immediato dalla freccia del composer. Quattro errori di fila lo fermano: se
qualcosa non torna, insistere non serve. Ogni operazione resta nella
conversazione, quindi il registro di cosa ha fatto è la chat stessa.

Due accorgimenti perché le corse lunghe restino sensate: i risultati più
vecchi degli strumenti vengono abbreviati nella richiesta (gli ultimi otto
passi restano per intero), e se il modello smette di agire senza concludere
viene richiamato all'ordine, al massimo tre volte.

### Verificare il lavoro: la CI come banco di prova

Sul telefono non esistono shell né compilatore, quindi un agente di codice non
potrebbe provare quello che scrive. La via d'uscita è il cloud: con un'area di
lavoro GitHub l'agente ha anche gli strumenti `git_branch`, `ci_run`,
`ci_status` e `pr_open`, e il flusso che gli viene chiesto di seguire è:

1. crea un branch, così il ramo principale resta intatto;
2. scrive le modifiche (un solo commit per gruppo di file);
3. avvia il workflow con `ci_run`;
4. aspetta l'esito con `ci_status` — l'attesa avviene nell'app, non facendo
   spendere al modello un giro per volta — e riceve conclusione, lavori falliti
   e **le righe utili dei log**, non migliaia di righe;
5. se è rosso corregge e ripete; a verde apre la pull request.

Il token GitHub deve avere i permessi **Contents: read & write**, **Pull
requests: read & write** e **Actions: read & write**; il workflow deve
prevedere `workflow_dispatch`.

### Compiti pianificati

In **Impostazioni → Compiti pianificati** si danno a GUZZOBOT obiettivi
ricorrenti — "ogni mattina controlla i commit nuovi e dimmi se la build è
rotta". Girano con WorkManager, sopravvivono al riavvio e lasciano il risultato
in una conversazione, con una notifica alla fine.

Due limiti dichiarati: Android garantisce la **frequenza** (minimo 15 minuti),
non l'orario esatto, e col telefono in Doze slitta; un worker viene fermato
dopo **dieci minuti**, quindi il budget di un compito pianificato è più stretto
(25 passi, 8 minuti) e, se non basta, l'agente lo dice e si riprende dall'app.

Di default un compito pianificato è **in sola lettura**: legge, analizza e
riferisce. Il permesso di scrivere e committare è un interruttore per singolo
compito, perché lì nessuno è davanti allo schermo a dare conferme.

### Lavorare in background

Con **"Continua a lavorare in background"** attivo, una risposta lunga o una
serie di operazioni proseguono a schermo spento, con una notifica persistente.
Il limite non è dell'app ma di Android: un servizio di tipo `dataSync` ha **sei
ore ogni ventiquattro**, poi il sistema chiama `onTimeout()` e va fermato.
Un agente che lavora col telefono spento non è possibile da un'app Android:
servirebbe un server.

## Parlare, invece di scrivere

GUZZOBOT usa il **motore di sintesi di Android** e il suo riconoscimento
vocale: non costano niente, non consumano crediti e su molti telefoni
funzionano anche senza rete. Per una conversazione parlata è la scelta giusta,
non il ripiego.

**Lettura automatica** (*Impostazioni › Voce*): la risposta viene letta
**mentre arriva**, una frase alla volta, invece che alla fine. Aspettare la
fine renderebbe tutto a scatti; leggere una frase troncata a metà suonerebbe
sbagliato — quindi si legge solo ciò che è concluso, e l'ultimo pezzo alla
chiusura.

Quello che si legge non è il markdown grezzo: i blocchi di codice vengono
**annunciati e non sillabati** («blocco di codice»), di un link si legge il
testo e non l'indirizzo, titoli, asterischi ed elenchi spariscono, le tabelle
si annunciano. E «3.5» resta un numero, non due frasi.

**Conversazione parlata**: tieni premuto il microfono in chat. Parli, GUZZOBOT
risponde a voce e **torna ad ascoltare** da solo — un giro che continua finché
non lo fermi con un tocco. Mentre ascolta vedi quello che sta capendo, parola
per parola. Se il riconoscimento non sente, lo dice ed esce, invece di restare
in ascolto di nessuno.

Dalle impostazioni scegli **lingua, voce, velocità e tono** fra quelli
installati sul telefono, con un pulsante per sentirli prima. Resta disponibile
anche la voce via API (`/audio/speech`), per chi vuole una qualità diversa e
accetta di pagarla.

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
  GPT-4o, Pixtral, Qwen-VL, Gemini, Llama 4, `deepseek-flash`…) viene inviata
  direttamente come `image_url`, ridimensionata a 1152px e ruotata secondo
  l'EXIF. Altrimenti la legge un modello che le vede e la sua descrizione entra
  nel messaggio, calcolata una volta sola e salvata. Se non ne hai scelto uno
  nelle impostazioni, l'app lo trova da sola fra i provider configurati,
  preferendo lo stesso provider del modello in chat: con `deepseek-v4-pro`
  attivo, per esempio, l'immagine la legge `deepseek-flash`, stessa chiave.
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
  OpenRouter, Groq, Mistral, xAI, Z.ai, Moonshot, Google AI Studio, Cerebras,
  OpenAI, Ollama in locale — sette dei quali con un **piano gratuito**. Puoi
  aggiungerne altri con nome, base URL e modelli, o premere *Scarica elenco
  modelli*.
- **Blocco "Ragionamento"** richiudibile per i modelli che lo espongono
  (`deepseek-reasoner` e i reasoning su OpenRouter).
- **Markdown renderizzato**: titoli, elenchi, citazioni, link, blocchi di codice
  con etichetta del linguaggio e copia rapida.
- **Pannello laterale** con le voci principali — Nuova chat, **Agente Bot**,
  Code, **Competenze**, Artefatti, Area di lavoro, Compiti, Impostazioni — e le conversazioni divise
  in **Fissate**, **Progetti** e **Recenti**. Una chat si fissa in alto o si
  sposta in un progetto dal suo menù; i progetti si creano dal pannello e,
  eliminandoli, le conversazioni tornano fra le recenti invece di sparire.
- **Code** è la sezione per chi scrive codice, non una chat: sfoglia l'area di
  lavoro (cartella o repository), apre i file in monospazio selezionabile,
  avvia un workflow e ne legge l'esito, e da qualsiasi punto manda l'agente a
  lavorare su quel file preciso, che gli arriva come contesto dell'obiettivo.
- **Artefatti** raccoglie quello che GUZZOBOT produce: file scritti (con il
  link al commit su GitHub o il percorso nella cartella), immagini generate,
  audio e pull request aperte. Un tocco apre l'elemento dove si trova.
- **Agente Bot** è il posto da cui si comanda l'agente: stato dell'area di
  lavoro, lancio di un obiettivo, tetto di sicurezza (passi e minuti),
  autonomia, lavoro in background e compiti pianificati.
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
- **Connettori** collega server MCP remoti e ne mostra gli strumenti; **Privacy
  e autorizzazioni** raccoglie host, permessi e pulsanti per revocare.
- **Impostazioni**: prompt di sistema, temperatura, token massimi, messaggi di
  contesto, tema chiaro/scuro/sistema, visibilità del ragionamento.

## Creare e modificare immagini

Il pulsante *Genera un'immagine* nel foglio degli allegati fa due cose, e
sceglie da sé quale: **senza allegati** crea un'immagine dal testo che hai
scritto; **con una foto allegata** modifica quella, secondo il testo — «falla
diventare notte», «togli lo sfondo». Il gesto è lo stesso.

Le due strade tecniche però sono diverse, e vale saperlo perché cambia chi può
farle:

- **Creare** passa da `/images/generations`, l'endpoint compatibile con l'API
  OpenAI: funziona con i modelli immagine di Gemini, con `gpt-image-1` di
  OpenAI e con `grok-2-image` di xAI — **tutti a pagamento**. L'unico
  davvero gratuito è **Pollinations**, che non chiede nemmeno una chiave.
- **Modificare** quella strada non la può fare: accetta solo testo. Serve l'API
  nativa di Google, che prende prompt e immagine insieme — quindi la modifica
  è possibile solo con Gemini, e **solo con la fatturazione attiva**.

> **Una correzione.** Fino alla 5.2 questo documento diceva che Gemini
> generava immagini gratis, e l'app sceglieva da sé un suo modello immagini
> appena salvavi la chiave. Era sbagliato: la
> [pagina dei prezzi di Google](https://ai.google.dev/gemini-api/docs/pricing)
> dà **tutti** i suoi modelli di immagini fuori dal piano gratuito, e quello
> che l'app proponeva (`gemini-2.5-flash-image`) è stato dismesso il 2 ottobre
> 2026. Da lì venivano i `503` che sembravano un sovraccarico perenne. Ora
> l'app non sceglie mai da sola un motore a pagamento: con una chiave Gemini
> il ruolo immagini va su Pollinations, e i modelli di Gemini restano
> selezionabili a mano da chi la fatturazione ce l'ha.

Delle due forme di richiesta che Google espone l'app prova la corrente e, se
risponde che non esiste, la precedente. E l'immagine nella risposta non viene
cercata a un indirizzo fisso: si attraversa il JSON raccogliendo i blocchi che
dichiarano di essere immagini, così uno spostamento di campo non rompe tutto.

Il risultato finisce in chat e in **Artefatti**, con il modello che l'ha
prodotto.

**Ripiego gratuito** (*Impostazioni › Multimodale*, spento di default): quando
il modello scelto è sovraccarico o ha esaurito la quota del giorno, l'immagine
viene creata con Pollinations invece di restituire un errore. È spento di
proposito — il risultato porta il logo del servizio e sta a un indirizzo
pubblico, e non è una cosa da far succedere alle spalle di chi usa l'app —
e quando scatta te lo dice, con il nome di chi ha prodotto davvero l'immagine
anche in Artefatti.

Il ripiego distingue due cose che si somigliano: *riprovare con lo stesso*
(sovraccarico) e *cercare altrove* (sovraccarico **o** quota finita). Una
chiave sbagliata o un prompt rifiutato non rientrano in nessuna delle due:
cambiare provider non li aggiusta, e provarci nasconderebbe il motivo vero.
Alle modifiche non si applica: quelle le sa fare solo l'API nativa di Google.

### Senza nessuna chiave: Pollinations

In *Modelli gratuiti* c'è **Pollinations**, l'unico generatore di immagini che
non chiede niente: nessun account, nessuna chiave, si tocca *Usa adesso* e
funziona. Non è un endpoint compatibile con l'API OpenAI ma una GET il cui
percorso *è* il prompt, quindi ha una sua strada nel codice.

Quello che dà in cambio di niente, per intero: **una richiesta alla volta per
indirizzo IP, almeno 45 secondi fra una e l'altra**; **massimo 768×768** (una
misura più grande viene riportata nei limiti conservando le proporzioni); il
**logo del servizio stampato sopra**; e un indirizzo **pubblico** — il risultato
viene messo in cache e chiunque abbia quell'URL rivede la stessa immagine.
Quando il servizio risponde «troppo in fretta», l'app riporta quanti secondi
aspettare invece di un errore generico. E non modifica immagini: per quello
serve Gemini.

Un provider di sole immagini non compare fra i modelli di chat e non viene
impostato come modello attivo: conversarci non si può.

### L'agente le può creare da sé

La generazione di immagini non è più soltanto un pulsante: è uno **strumento**
che il modello può decidere di usare nel mezzo di un lavoro — scrivere
l'articolo e produrne la copertina senza che nessuno cambi schermata. Vale
nella chat con gli strumenti attivi, per l'Agente Bot e per i compiti
pianificati.

Lo strumento compare **solo se un modello per le immagini è configurato e
utilizzabile**: offrirne uno che fallisce a ogni chiamata sprecherebbe un passo
del budget dell'agente a ogni tentativo. Non passa dalla finestra di conferma,
perché non tocca i tuoi file né il repository; quanto può costare lo limita il
tetto di sicurezza, che conta i passi.

## Competenze (Skills)

*Pannello laterale › Competenze*, o *Impostazioni › Competenze*. Una competenza
è un modo di lavorare con un nome: la tocchi, si applica alla conversazione
aperta, e le sue istruzioni si aggiungono al tuo prompt di sistema — **non lo
sostituiscono**. Chi ha scritto «rispondi sempre in dialetto» nelle
impostazioni non se lo vede cancellare da una competenza, e una competenza non
può dire al modello di ignorare chi usa l'app.

Quindici già pronte, divise per ambito:

- **Scrittura** — Correttore di bozze (corregge senza riscrivere), Riscrivi più
  chiaro, Email professionale.
- **Studio** — Spiega da zero, Schede di studio (domanda/risposta da incollare
  in qualunque app di ripasso), Traduci e spiega.
- **Codice** — Revisione del codice (cerca i difetti veri, non lo stile),
  Spiega questo codice, Scrivi i test, Messaggio di commit.
- **Casa e ufficio** — Leggi questo documento (bollette, contratti: cosa dicono
  in pratica), Dalla foto alla lista, Verbale di riunione.
- **Analisi** — Riassunto strutturato, Analizza i dati.

### Caricate dal modello, non solo scelte a mano

Una competenza si può ancora applicare dal menù, ma quando gli strumenti sono
attivi **è il modello a caricarsela** quando riconosce il lavoro. Funziona come
un indice: gli strumenti dichiarano *quali* competenze esistono e a cosa
servono, poche righe ciascuna, e solo quando ne carica una riceve le istruzioni
per esteso — mettere tutto nel prompt costerebbe a ogni messaggio, anche quando
non serve niente. La competenza caricata **resta applicata** alla conversazione,
così non va ricaricata a ogni turno.

Richiede un modello che sappia usare gli strumenti. Dove non li sa usare,
l'elenco delle competenze torna nel prompt di sistema e il modello può
suggerirtene una.

Ognuna ha il suo segno nell'elenco — scelto per quello che fa — e una
competenza scritta a mano eredita quello del suo ambito. Ognuna si può
**modificare**, comprese quelle fornite con l'app: il testo
giusto per un lavoro lo conosce chi quel lavoro lo fa. Si possono scriverne di
nuove, disattivare quelle che non servono e cancellarle — e una predefinita
cancellata non torna al prossimo aggiornamento.

Una precisazione, perché il nome è lo stesso di Claude ma la cosa non è
identica: **qui una competenza è istruzioni, non un programma**. In Claude una
skill può portarsi dietro degli script; su un telefono no, e fingere il
contrario darebbe competenze che sembrano funzionare e non fanno niente. Quando
serve eseguire davvero qualcosa, la strada è l'agente con la CI di GitHub, che
c'è già. Il vantaggio resta grosso e vale su qualunque modello, anche gratuito,
perché non chiede niente di speciale al modello: è la differenza fra chiedere
«correggi» e ottenere una correzione fatta come si deve.

## Catalogo dei connettori

*Impostazioni › Catalogo connettori*. Invece di cercare un indirizzo e
incollarlo, si scorre un elenco e si tocca *Aggiungi*. Ogni indirizzo è
verificato sulla documentazione del fornitore, e sono tutti sul trasporto
«streamable HTTP», il solo che il client dell'app sa parlare: un server che
espone soltanto il vecchio HTTP+SSE non funzionerebbe e non viene spacciato per
pronto.

Accanto a ogni connettore c'è un monogramma colorato, non il logo del
servizio: i marchi sono dei rispettivi proprietari, ciascuno con le sue regole
d'uso, e impacchettarli nell'APK significherebbe ridistribuirli. Scaricarli
all'avvio sarebbe peggio — una chiamata a dieci domini solo per disegnare un
elenco, e un elenco grigio quando si è offline. La tinta si ricava dal nome,
quindi resta la stessa a ogni avvio.

**Pronti subito** — un token da incollare, o niente

| Connettore | Cosa dà | Serve | Verificato su |
|---|---|---|---|
| **Zapier** | Gmail, Calendar, Slack, Fogli, Drive e migliaia di app | un token | [docs.zapier.com](https://docs.zapier.com/mcp/overview) |
| **GitHub** | Repository, issue, PR, ricerca nel codice | un token (quello dell'area di lavoro viene riusato) | [github-mcp-server](https://github.com/github/github-mcp-server) |
| **DeepWiki** | La documentazione di qualunque repository pubblico | **niente** | [docs.devin.ai](https://docs.devin.ai/work-with-devin/deepwiki-mcp) |
| **Hugging Face** | Modelli, dataset, spazi | un token | [huggingface.co/blog](https://huggingface.co/blog/building-hf-mcp) |
| **Apify** | Estrazione dati dal web | un token | [docs.apify.com](https://docs.apify.com/platform/integrations/mcp) |
| **Tally** | Moduli e risposte (server in beta) | un token | [developers.tally.so](https://developers.tally.so/api-reference/mcp) |

**Con accesso nel browser**

| Connettore | Autenticazione | Verificato su |
|---|---|---|
| **Notion** | Solo OAuth | [developers.notion.com](https://developers.notion.com/guides/mcp/get-started-with-mcp) |
| **Linear** | OAuth 2.1 con registrazione dinamica, **oppure** un token | [linear.app/docs](https://linear.app/docs/mcp) |
| **Stripe** | OAuth, **oppure** una chiave «agent» | [docs.stripe.com](https://docs.stripe.com/mcp) |
| **Sentry** | Solo OAuth | [mcp.sentry.dev](https://mcp.sentry.dev/) |

Dove il fornitore accetta entrambe le strade, l'app offre anche *Usa un token*.
Non è un ripensamento: il giro di OAuth passa per la registrazione dinamica
del client e per il ritorno dal browser, e se lì qualcosa si inceppa un token
resta una strada che funziona. Un test blocca i dieci indirizzi verificati,
così se una riga cambia è una scelta e non una svista.

**Zapier è la via d'uscita da quel lucchetto**, e per questo sta in cima: un
token solo e dentro ci sono Gmail, Calendar, Slack, Fogli e migliaia di altre
app, perché l'accesso a ciascuna lo tiene Zapier. Il suo URL vale come una
password — chi ce l'ha può eseguire le azioni che hai configurato.

## Connettori (MCP)

Un connettore è un server [MCP](https://modelcontextprotocol.io) remoto: gli
strumenti che espone si aggiungono a quelli dell'app e il modello li può usare
nella stessa conversazione in cui legge i tuoi file. Si gestiscono da
*Impostazioni › Connettori*.

- **Si aggiunge** con nome, indirizzo `https://…`, intestazione di
  autenticazione (di norma `Authorization`, il `Bearer ` lo mette l'app) e
  token. Alla conferma GUZZOBOT fa la stretta di mano, chiede l'elenco degli
  strumenti e lo mostra; se qualcosa non va, il motivo resta scritto sotto il
  connettore invece di sparire in un messaggio fugace.
- **Il token è custodito come le chiavi API**, cifrato dal Keystore: nella
  pagina non ricompare mai, si può solo sostituire.
- **Un interruttore per connettore** lo esclude senza cancellarlo, insieme a
  tutti i suoi strumenti.
- **I nomi vengono resi univoci** (`mcp_<connettore>__<strumento>`), così due
  server che espongono entrambi `search` non si pestano i piedi.
- **I permessi valgono anche qui**: se il server dichiara uno strumento di sola
  lettura (`readOnlyHint`) GUZZOBOT lo esegue senza chiedere; tutti gli altri
  passano per la stessa finestra di conferma delle scritture sui file, con
  nome, argomenti e i pulsanti *Consenti* / *Consenti sempre* / *Rifiuta*.
- **Valgono in chat e per l'agente**, compresi i compiti pianificati, che però
  restano sotto il loro tetto di sicurezza.

Un limite onesto: i server MCP **stdio**, quelli che si avviano con `npx` o
`uvx`, qui non esistono e non possono esistere — un'app Android non lancia
processi di altri programmi. Servono i server remoti, che è anche l'unica forma
sensata da un telefono. Senza connettori l'app funziona esattamente come prima:
nessuna delle due cose dipende dall'altra.

### Le firme del pensiero di Gemini 3

Un dettaglio che vale la pena conoscere, perché senza non funziona niente. I
modelli Gemini 3 agganciano a ogni chiamata di strumento una *firma del
pensiero*, e al giro successivo la pretendono indietro **identica**: se manca,
rifiutano l'intera conversazione con un `400 · Function call is missing a
thought_signature`. Un client che decodifica `tool_calls` in una struttura
fissa la perde per strada, e da quel momento nessuno strumento funziona più —
né le immagini, né l'area di lavoro, né i connettori.

GUZZOBOT conserva l'intero oggetto `extra_content` che accompagna la chiamata,
lo salva insieme al messaggio e lo rimanda tale e quale. Tenere l'oggetto
intero invece del singolo campo è deliberato: così torna indietro qualunque
cosa il provider ci infili, senza rincorrere il nome esatto a ogni versione.

## I modelli cambiano: l'app se ne accorge

Le sigle dei modelli invecchiano in fretta — `deepseek-chat` è stato ritirato,
GLM e Kimi cambiano versione di mese in mese. Per questo l'elenco scritto
nell'app è solo un punto di partenza e la verità è l'endpoint `/models` del
provider:

- una volta a settimana un lavoro in background ricontrolla i provider per cui
  hai una chiave e riallinea gli elenchi;
- lo stesso avviene appena salvi una chiave, e a mano dal selettore dei modelli
  (*Aggiorna l'elenco dei modelli*);
- se il provider serve pochi modelli, il suo elenco sostituisce il nostro; se ne
  serve centinaia, come OpenRouter, si tiene la selezione curata togliendo
  quelli che non esistono più;
- il modello con cui si parte non è «il primo della lista», che arriva in
  ordine alfabetico e può essere un'anteprima incapace di usare gli strumenti:
  vince il primo di quelli scelti dall'app che esiste ancora, poi uno normale;
- se un modello rifiuta gli strumenti, la richiesta viene **rifatta senza**
  invece di restituire un errore: parlare lo sa comunque, e te lo dice;
- quando il provider risponde che è sovraccarico — `503 · high demand`, il
  tipico «riprova più tardi» — l'app **riprova da sé**, due volte, aspettando
  un po' di più a ogni tentativo. Vale per la chat e per le immagini. Se la
  quota del giorno è esaurita invece non riprova: sarebbe tempo perso, e te lo
  dice chiaramente;
- i modelli che non possono reggere una conversazione — embedding, voce, live,
  immagini — restano nell'elenco del provider, perché sono quelli che servono
  ai ruoli multimodali, ma sono nascosti nel selettore della chat, dove
  toccarli darebbe solo un errore dell'API;
- quando configuri un provider, i ruoli ancora vuoti si riempiono da soli con
  quello che quel provider sa fare: con una chiave Gemini la generazione di
  immagini si accende senza passare dalle impostazioni;
- se il modello che stavi usando sparisce, l'app passa al parente più prossimo
  — quello che condivide il prefisso più lungo, `glm-5.4` → `glm-5.3` — e te lo
  dice, invece di lasciarti con un nome morto in mano.

Preconfigurati, oltre ai precedenti: **Z.ai (GLM)** su
`https://api.z.ai/api/paas/v4` e **Moonshot (Kimi)** su
`https://api.moonshot.ai/v1` — con chiavi della piattaforma cinese il dominio è
`api.moonshot.cn`. I nomi dei modelli che trovi preimpostati valgono finché il
primo aggiornamento non li corregge con quelli veri.

## Privacy e autorizzazioni

*Impostazioni › Privacy e autorizzazioni* non è una pagina di promesse: è
l'elenco di cosa esce, cosa resta e chi ha il permesso di fare cosa, con i
pulsanti per revocare.

- **Dove vanno le richieste**: gli host configurati, uno per uno, con il motivo
  per cui ci si parla (il provider di una chiave, un connettore, il repository).
  Non c'è nessun altro indirizzo, e nessun server di Anthropic: il traffico in
  chiaro (HTTP) è permesso soltanto verso `localhost` e `10.0.2.2`, per usare
  Ollama o LM Studio sulla propria macchina.
- **Cosa resta sul telefono**: quante conversazioni e quanti megabyte di
  allegati sono salvati. Il testo dei documenti viene estratto qui, non altrove.
- **Autorizzazioni di sistema**: microfono, notifiche e la cartella di lavoro,
  ciascuna col suo stato. La cartella si revoca da qui, il resto rimanda alle
  impostazioni di Android.
- **Chi decide**: l'interruttore dell'autonomia e il numero di connettori
  attivi, con la scorciatoia per gestirli.
- **Dimenticare**: cancellare tutte le conversazioni, o tutte le chiavi e i
  token dei connettori in un colpo.

Le chiavi API e i token dei connettori stanno in
`EncryptedSharedPreferences`, cifrate con una chiave custodita dal Keystore
hardware ed escluse dai backup. Gli allegati vivono nella cartella privata
dell'app.

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
│  ├─ model/               provider, piani gratuiti, chiavi, impostazioni
│  ├─ local/               Room: conversazioni, messaggi, allegati
│  ├─ prefs/               DataStore (impostazioni) + chiavi cifrate
│  ├─ agent/               ciclo, budget, prompt, strumenti, immagini
│  ├─ mcp/                 connettori MCP: client, nomi, catalogo
│  ├─ skills/              competenze: catalogo e composizione del prompt
│  ├─ workspace/           cartella locale, repository GitHub, Actions, strumenti
│  ├─ files/               import allegati, estrazione testo, PDF e video→immagini
│  │  └─ office/           lettore OLE2 e parser .doc, .xls, .ppt
│  ├─ media/               registratore, player, sintesi vocale di sistema
│  ├─ remote/              chat in streaming, vision, whisper, immagini
│  │                       (creazione e modifica), voce
│  └─ repo/                ChatRepository
├─ service/                servizio in primo piano per il lavoro in background
├─ work/                   compiti pianificati con WorkManager
└─ ui/
   ├─ theme/               palette crema/terracotta, tipografia serif
   ├─ onboarding/          primo avvio: chiave guidata e permessi
   ├─ chat/                chat, composer, allegati, drawer, ViewModel
   ├─ markdown/            parser e renderer markdown
   └─ settings/            impostazioni, ruoli multimodali, provider, modelli
                           gratuiti, competenze, catalogo, connettori, privacy
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
Dei connettori si parla soltanto MCP su HTTP: i server `stdio` (`npx`, `uvx`)
sono fuori portata per un'app Android, e di un server remoto si usano gli
strumenti, non le sue risorse o i suoi prompt. L'accesso OAuth richiede che il
servizio permetta la registrazione automatica dei client. Una competenza è
istruzioni, non codice eseguibile.
Nessuna sincronizzazione tra dispositivi, per scelta: se disinstalli l'app, le
conversazioni se ne vanno con lei.
