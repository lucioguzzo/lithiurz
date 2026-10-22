# Lith — client di chat AI nativo per Android

App Android nativa (Kotlin + Jetpack Compose) con l'estetica e il flusso di una
chat moderna, ma **senza server intermedi**: parla direttamente con il provider
di modelli che configuri tu. Nessun account, nessun backend da mantenere, le
conversazioni restano nel database locale del telefono.

## Cosa fa

- **Chat in streaming**: la risposta appare parola per parola, con pulsante di
  interruzione; se interrompi, la parte già ricevuta resta salvata.
- **Qualsiasi provider compatibile con l'API OpenAI**. Preconfigurati:
  DeepSeek, OpenRouter, Groq, Mistral, xAI, OpenAI, Ollama in locale. Puoi
  aggiungerne altri indicando nome, base URL e modelli, oppure premere
  *Scarica elenco modelli* e farteli proporre dall'endpoint.
- **Blocco "Ragionamento"**: per i modelli che lo espongono (es.
  `deepseek-reasoner`, modelli reasoning su OpenRouter) la catena di pensiero
  arriva in un riquadro richiudibile, separata dalla risposta.
- **Markdown renderizzato**: titoli, elenchi, citazioni, grassetto/corsivo,
  link cliccabili e blocchi di codice con etichetta del linguaggio e copia
  rapida.
- **Storico conversazioni** nel pannello laterale, con ricerca, rinomina,
  condivisione e cancellazione; il titolo viene dal primo messaggio.
- **Rigenera** l'ultima risposta, **modifica** un tuo messaggio (riapre il testo
  nel composer e taglia la conversazione da quel punto), copia singoli messaggi.
- **Impostazioni**: prompt di sistema, temperatura, token massimi, quanti
  messaggi di contesto inviare, tema chiaro/scuro/sistema, visibilità del
  ragionamento.
- **Condividi verso Lith**: l'app compare nel menu "Condividi" di Android per
  mandarle un testo da commentare.

## Privacy

La chiave API sta in `EncryptedSharedPreferences`, cifrata con una chiave
custodita dal Keystore hardware di Android, ed è esclusa dai backup. Le
richieste vanno solo all'host che hai configurato. Il traffico in chiaro (HTTP)
è consentito unicamente verso `localhost` e `10.0.2.2`, per usare Ollama o
LM Studio sulla propria macchina; tutto il resto deve essere HTTPS.

## Installare subito

In `android/dist/lith-1.0.apk` c'è l'APK già compilato (12,8 MB, firmato con la
chiave di debug). Scaricalo sul telefono, aprilo e consenti l'installazione da
origini sconosciute. Richiede Android 8.0 o superiore.

## Primo avvio

1. Apri **Impostazioni** (pannello laterale in basso).
2. Scegli il provider, per esempio **DeepSeek**.
3. Incolla la chiave API (per DeepSeek: <https://platform.deepseek.com/api_keys>)
   e premi **Salva e usa**.
4. Torna in chat: il modello attivo si cambia dal titolo in alto.

Il nome del modello è testo libero: se il provider pubblica un modello nuovo,
basta aggiungerlo all'elenco del provider (una riga per modello) senza
aggiornare l'app.

## Compilare

Serve una JDK 17+ e l'SDK Android (`platforms;android-35`, `build-tools;35.0.0`).

```bash
cd android
echo "sdk.dir=/percorso/android-sdk" > local.properties
./gradlew assembleRelease     # APK in app/build/outputs/apk/release/
```

L'APK di release è firmato con la chiave di debug, così si installa subito da
file senza creare un keystore. Per pubblicarlo su Play Store va sostituito con
una firma propria in `app/build.gradle.kts`.

In alternativa, su GitHub: **Actions → Build Lith APK → Run workflow**, poi
scarica l'artifact `lith-apk`.

## Struttura

```
android/app/src/main/java/com/lucio/lith/
├─ LithApp.kt              Application + container delle dipendenze
├─ MainActivity.kt         edge-to-edge, tema, intent di condivisione
├─ data/
│  ├─ model/               Provider, preset, AppSettings
│  ├─ local/               Room: conversazioni e messaggi
│  ├─ prefs/               DataStore (impostazioni) + chiavi cifrate
│  ├─ remote/              client /chat/completions in streaming (SSE)
│  └─ repo/                ChatRepository
└─ ui/
   ├─ theme/               palette crema/terracotta, tipografia serif
   ├─ chat/                schermata chat, composer, drawer, ViewModel
   ├─ markdown/            parser e renderer markdown
   └─ settings/            impostazioni e editor dei provider
```

## Personalizzare

- **Nome e icona**: `app_name` in `res/values/strings.xml`,
  `res/drawable/ic_launcher_foreground.xml`, colore in `res/values/colors.xml`.
- **Colori**: `ui/theme/Color.kt` (chiaro e scuro) e `Theme.kt`.
- **Provider preconfigurati**: `data/model/Models.kt`.
- **Prompt di sistema predefinito**: `Providers.DEFAULT_SYSTEM_PROMPT`.

## Limiti noti

Solo testo: nessun allegato, immagine o voce. Le tabelle markdown vengono
mostrate come testo semplice. Non c'è sincronizzazione tra dispositivi, per
scelta: se disinstalli l'app, le conversazioni se ne vanno con lei.
