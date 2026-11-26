# SyncAudio

Due smartphone Android, due casse Bluetooth diverse, la stessa musica a tempo.
Uno dei due telefoni **cattura l'audio di qualsiasi app** (YouTube, Spotify, RaiPlay, file MP3…) e lo invia
via Wi-Fi all'altro, che lo riproduce sulla propria cassa. I ruoli si possono scambiare in qualsiasi momento.

> **Stato: prototipo (v0.1).** Il motore di sincronizzazione è testato in simulazione. L'app compila, ma non è
> ancora stata provata su telefoni veri. Il primo obiettivo è proprio questo: vedi "Come provarla".

---

## 1. Cosa si può fare e cosa no

Prima di scrivere codice ho verificato i limiti di Android. Tre cose non dipendono da noi.

### A. "Qualsiasi fonte audio": quasi, ma non al 100%
Android (dalla versione 10) permette a un'app di catturare l'audio delle altre app tramite
`AudioPlaybackCapture`, previo consenso dell'utente (lo stesso popup della registrazione schermo).
**Però ogni app può decidere di vietarlo.** Di solito lo fanno le app con contenuti protetti (DRM), come
Netflix. Su un telefono non rootato non esiste un modo legittimo per aggirare il divieto.
- YouTube, browser, lettori MP3 e la maggior parte delle app di solito si catturano senza problemi.
- Spotify, RaiPlay e altre app di streaming vanno **provate sul campo**: se un'app blocca la cattura, SyncAudio se
  ne accorge (c'è audio in riproduzione ma la cattura è muta) e mostra un avviso.

### B. La cassa del telefono che trasmette non si può "ritardare"
Questo è il punto più delicato. Sul telefono che trasmette (A), la musica continua a uscire **subito** sulla sua
cassa, perché è l'app musicale a suonarla. Un'app normale non può né ritardarla né silenziarla. Il telefono che
riceve (B) può solo suonare *dopo*, cioè quando gli è arrivato l'audio:

```
cassa A:  cattura ──────────────── latenza Bluetooth A (≈150–300 ms) ──► suono
cassa B:  cattura ─ rete+buffer (≈30–60 ms) ─ latenza Bluetooth B ────► suono
```

B è perfettamente a tempo con A solo se `latenza BT di A ≥ latenza BT di B + rete`. Con due casse uguali, B resta
indietro di circa 30–80 ms. Se le casse sono in stanze diverse non si nota. Se sono vicine si sente un leggero eco.

Soluzioni, dalla più semplice:
1. **Mettere sul telefono che trasmette la cassa più lenta.** Le casse hanno latenze molto diverse e lo
   slider di SyncAudio compensa la differenza.
2. **Scegliere il codec.** In *Opzioni sviluppatore → Codec audio Bluetooth* si può usare un codec più lento
   (AAC/LDAC) sul telefono A e uno più rapido (SBC, aptX LL) sul telefono B.
3. **Modalità "sync perfetto" (fase 2, vedi roadmap).** Con [Shizuku](https://shizuku.rikka.app/) l'app ottiene
   gli stessi privilegi di ADB. Così può catturare l'audio **togliendolo** dalla cassa locale (come fa `scrcpy`), e
   poi far suonare *entrambe* le casse con lo stesso ritardo calcolato. Serve Android 13+ e un'attivazione via
   "Debug wireless" una volta per riavvio. Non serve il PC né il root.

### C. La latenza Bluetooth di ogni cassa è sconosciuta
Android dichiara la latenza in modo impreciso e alcune casse non la riportano affatto. Per questo c'è lo slider
**"Regola sincronia"**, che viene salvato per ogni telefono. In fase 2 arriverà la calibrazione automatica: il
telefono emette un "chirp" da ciascuna cassa, lo registra col microfono e misura la differenza.

---

## 2. Come funziona

```
 Telefono A (trasmette)                          Telefono B (riceve)
 ┌───────────────────────────────┐   Wi-Fi/UDP   ┌──────────────────────────────────┐
 │ App musicale ─► mixer Android │               │ SyncReceiver                     │
 │        │            └─► cassa A│   beacon ───► │  ├─ ClockSync (stile NTP):       │
 │  AudioPlaybackCapture         │ ◄── ping/pong │  │   offset + deriva orologi     │
 │        │ PCM 48 kHz, 5 ms     │               │  └─ PlayoutEngine:               │
 │  SyncSource ── audio + PTS ──►│ ────────────► │      "il campione catturato a t  │
 └───────────────────────────────┘               │       deve suonare a t + ritardo"│
                                                 │      AudioTrack ─► cassa B       │
                                                 └──────────────────────────────────┘
```

- **Trasporto:** UDP sulla rete locale. Va bene la stessa Wi-Fi, oppure un telefono che fa da hotspot all'altro
  (serve anche senza internet). La scoperta è automatica via broadcast, con la connessione manuale per IP come
  riserva. I pacchetti sono di 5 ms, in PCM 16 bit stereo a 48 kHz (~1,5 Mbit/s).
- **Orologi:** il ricevitore misura continuamente lo scarto tra il suo orologio e quello della sorgente, con lo
  scambio a 4 timestamp di NTP. Usa solo gli scambi più veloci e una regressione lineare che stima anche la
  deriva dei quarzi (decine di ppm).
- **Riproduzione:** per ogni blocco audio il ricevitore chiede ad Android *quando* uscirà davvero dall'uscita
  (`AudioTrack.getTimestamp`) e sceglie i campioni giusti. Corregge la deriva variando la velocità di qualche
  centinaio di ppm, cosa che non si sente, invece di tagliare campioni, che produrrebbe dei clic.
- **Ruoli simmetrici:** se il ricevitore tocca "Trasmetti", l'altro telefono passa da solo a ricevitore.
- **Più di due telefoni:** funziona già, perché la sorgente invia a tutti i ricevitori collegati.

Nella simulazione (`core/src/test`) gli orologi hanno 50 ppm di deriva, il jitter Wi-Fi arriva a 20 ms e il 2%
dei pacchetti va perso. Anche così l'errore di allineamento a regime è **< 1,5 ms**. La soglia di percezione è
circa 5–10 ms.

### Struttura del codice
| Percorso | Contenuto |
|---|---|
| `core/` | Kotlin puro, senza dipendenze Android, testabile su PC e riutilizzabile su iOS con Kotlin Multiplatform: protocollo, `ClockSync`, `PlayoutEngine`, `SyncSource`/`SyncReceiver` |
| `app/.../AudioIO.kt` | Cattura (`AudioPlaybackCapture`) e riproduzione (`AudioTrack` con timestamp) |
| `app/.../SyncService.kt` | Servizio in primo piano con i ruoli sorgente e ricevitore, lock Wi-Fi a bassa latenza |
| `app/.../MainActivity.kt` | Interfaccia (Compose) |

---

## 3. Come provarla

**APK:** a ogni push GitHub Actions compila l'APK (workflow *SyncAudio APK*, artifact `syncaudio-apk`).
Per compilarlo in locale: `cd syncaudio && ./gradlew :app:assembleDebug`.

**Requisiti:** due telefoni Android 10 o superiore sulla stessa Wi-Fi, oppure uno in hotspot e l'altro collegato.

1. Su entrambi i telefoni collega la rispettiva cassa Bluetooth e apri SyncAudio.
2. Sul telefono B tocca **"Ricevi e riproduci"**.
3. Sul telefono A tocca **"Trasmetti l'audio di questo telefono"** e accetta il popup di cattura. Su Android 14+
   scegli lo schermo intero.
4. Fai partire la musica in qualsiasi app sul telefono A.
5. Sul telefono B regola **"Regola sincronia"** finché le casse suonano insieme. Il trucco: metti le casse vicine e
   cerca il punto in cui il suono "si fonde" invece di fare eco.

Cose da verificare nel primo test, che mi servono per la fase 2:
- quali app si catturano (YouTube, Spotify, RaiPlay, il lettore MP3) e quali danno l'avviso "cattura muta";
- quanto resta indietro B con lo slider al minimo, e se cambia invertendo le casse;
- se l'audio ha buchi: guarda "Audio mancante" e "Margine dati" nella schermata del ricevitore.

---

## 4. Roadmap

**Fase 1 (questa):** cattura universale, trasmissione, sincronizzazione degli orologi, riproduzione allineata,
scambio dei ruoli, regolazione manuale.

**Fase 2: sync perfetto e qualità**
- Modalità Shizuku: cattura con la cassa locale silenziata e riproduzione sincronizzata anche sul telefono A.
- Calibrazione acustica automatica col microfono.
- Codec Opus con FEC per reti Wi-Fi affollate e meno banda.
- Accoppiamento con QR code, volume remoto, metadati del brano.

**Fase 3: iOS**
- **iPhone come ricevitore:** fattibile al 100%. Il modulo `core` diventa Kotlin Multiplatform, oppure si
  riscrive in Swift seguendo lo stesso protocollo.
- **iPhone come trasmettitore:** iOS non permette a un'app di catturare l'audio delle altre app. L'unica strada
  è una *Broadcast Upload Extension* (ReplayKit), cioè la "registrazione schermo" avviata dal Centro di Controllo.
  Ha dei limiti: Apple Music e le app con DRM vengono silenziate, l'estensione ha 50 MB di memoria e vale lo
  stesso problema del punto 1.B, senza un equivalente di Shizuku.

### Alternative già esistenti (da conoscere)
- **Samsung "Dual Audio":** un solo Galaxy collega due casse Bluetooth insieme.
- **LE Audio / Auracast:** su Android recenti con casse compatibili, un telefono può trasmettere a più casse.
- **Modalità "party" dei produttori** (JBL, Sony, Bose…): funzionano solo tra casse della stessa marca.

SyncAudio serve quando le casse sono di marche diverse o vecchie, e non c'è un Samsung.
