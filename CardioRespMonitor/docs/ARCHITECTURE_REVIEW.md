# Revisione architetturale critica (fatta PRIMA di scrivere il codice)

Questa revisione confronta la specifica iniziale con ciò che serve davvero per una
registrazione IMU utilizzabile in uno studio di validazione. Ogni punto indica **problema**,
**decisione presa** e **dove si trova nel codice**.

## 1. Il `SensorSample` combinato nasconde un problema di sincronizzazione

**Problema.** Accelerometro e giroscopio sono due flussi indipendenti: anche alla stessa
frequenza nominale, i loro timestamp non coincidono (sfasamento costante o deriva, a seconda
del chip). Un campione `acc+gyro` "unico" va quindi *costruito*, non semplicemente letto.
Usare "l'ultimo gyro disponibile" (sample & hold) aggiunge un ritardo variabile tra 0 e un
periodo: per l'SCG/GCG (componenti fino a 30–50 Hz) questo è un errore di fase non trascurabile.

**Decisione.**
- Accelerometro come clock master; giroscopio **interpolato linearmente** all'istante
  dell'accelerometro (`sensor/SampleAligner.kt`).
- Se il gyro ha un buco > 50 ms, o manca, i canali gyro valgono **`NaN`** invece di un valore
  inventato.
- **Si salva sempre anche il log grezzo non interpolato** (`raw_events.csv`): è il dato
  primario per la validazione. Il CSV combinato è un prodotto derivato e può essere rigenerato
  offline con un altro metodo (es. interpolazione spline, resampling polifase).

## 2. Il periodo richiesto non è la frequenza reale

**Problema.** `registerListener(..., samplingPeriodUs)` è solo un *suggerimento*: l'HAL arrotonda
all'ODR supportato dall'IMU (es. 200 Hz → 208 Hz su chip Bosch/ST). Gli algoritmi che usano
`indice / fs_nominale` come tempo avrebbero un errore di scala sistematico (4% → 4% sulla HR).

**Decisione.** Frequenza effettiva e periodo nominale **misurati** (mediana dei primi
intervalli) e salvati in `metadata.json`; gli algoritmi futuri devono usare i timestamp
(`future_algorithms/dsp/UniformResampler.kt` porta i dati su griglia uniforme).

## 3. Jitter di consegna ≠ jitter di campionamento

**Problema.** Il thread UI o un thread lento non cambiano *quando* il sensore campiona: il
timestamp hardware viene assegnato dal sensor hub. Ciò che il software può causare è
**perdita** di eventi (code piene) o, se si usasse `System.currentTimeMillis()` alla ricezione,
un **finto jitter** dovuto alla latenza di consegna.

**Decisione.**
- Timeline basata **solo** su `event.timestamp`.
- Callback minimale (copia in ring buffer lock-free, zero allocazioni) su `HandlerThread`
  dedicato ad alta priorità; tutto il resto su un altro thread.
- Diagnostiche separate per le due cause: `estimatedLostSamples`/`gapCount` (perdite a monte,
  visibili come salti nei timestamp) e `ringBufferOverflows` (perdite lato app).

## 4. Batching FIFO: opzionale ma utile

**Problema.** Con `maxReportLatencyUs = 0` ogni campione sveglia la CPU; se la CPU va in
sleep la FIFO del sensore trabocca. Con il batching i campioni vengono consegnati a blocchi,
ma **con i timestamp originali**: per la registrazione è spesso *migliore*.

**Decisione.** Default 0 (grafico più reattivo), opzione "Batching FIFO 100 ms" in UI.
Allo stop si chiama `SensorManager.flush()` e si attende `onFlushCompleted` prima
dell'unregister, altrimenti gli eventi ancora nella FIFO andrebbero persi.

## 5. Sensori calibrati vs non calibrati

**Problema.** `TYPE_GYROSCOPE` applica una stima del bias aggiornata dinamicamente: quando la
stima cambia, il segnale ha un gradino/deriva lenta, proprio nella banda respiratoria
(0.1–0.7 Hz). Lo stesso vale, in misura minore, per l'accelerometro su alcuni device.

**Decisione.** Opzione `TYPE_*_UNCALIBRATED` (fallback automatico se assenti). Il tipo
usato è scritto in `metadata.json`.

## 6. Limiti di piattaforma da gestire esplicitamente

| Limite | Effetto | Soluzione adottata |
|---|---|---|
| Android 9+: niente sensori continui alle app in background | stop acquisizione a schermo spento | **Foreground Service** |
| Android 12+: >200 Hz solo con `HIGH_SAMPLING_RATE_SENSORS` | FASTEST limitato a 200 Hz | permesso dichiarato nel Manifest |
| Android 14+: tipo di FGS obbligatorio | crash all'avvio del servizio | `foregroundServiceType="health"` (prerequisito soddisfatto da `HIGH_SAMPLING_RATE_SENSORS`) |
| Android 15: limite 6 h per `dataSync` | stop dopo 6 h | si usa `health`, non `dataSync` |
| Doze / CPU sleep | FIFO non svuotata → buchi | `PARTIAL_WAKE_LOCK` + schermo acceso durante la registrazione |
| Throttling termico | drain più lento | Sustained Performance Mode quando supportato |
| ROM aggressive (Xiaomi, Huawei, Samsung) | kill del processo | escludere l'app dall'ottimizzazione batteria (vedi README) |

## 7. Elementi aggiunti per la validazione scientifica

1. `metadata.json` per sessione: modello/vendor/risoluzione/range/FIFO dei sensori, device,
   versione Android e app, configurazione, time base rilevato, offset wall-clock all'inizio
   **e alla fine** (deriva), diagnostiche finali, eventi di cambio accuratezza.
2. Time base di `event.timestamp` **rilevato** (elapsedRealtime vs nanoTime) invece che assunto.
3. Offset wall-clock misurato con incertezza nota (coppia di letture con finestra minima).
4. Scrittura in `NaN` dei dati mancanti, mai riempimento silenzioso.
5. Formattazione numerica indipendente dal `Locale` (in italiano `String.format` scriverebbe `9,81`).
6. `fsync` alla chiusura dei file; flush periodico ogni 5 s (perdita massima in caso di crash).
7. Test unitari su ring buffer (concorrenza), allineamento, statistiche e resampler.

## 8. Ulteriori miglioramenti consigliati (non implementati)

- **Marcatori di evento** (pulsante "evento" che scrive un timestamp in un file `events.csv`):
  indispensabili per sincronizzare con ECG/fascia respiratoria di riferimento, tipicamente
  con una sequenza di 3 colpetti sul telefono visibile in entrambi i segnali.
- **Sincronizzazione con il gold standard**: oltre al tap, considerare NTP prima della
  sessione e registrare l'offset NTP (es. con una libreria SNTP) nei metadati.
- **Calibrazione statica a 6 posizioni** dell'accelerometro per stimare offset/guadagno per asse.
- **Formato binario** (es. file di float32 little-endian + header) se si registra per ore a
  >400 Hz: il CSV è ~3× più grande e più lento da scrivere/leggere.
- **Protocollo di acquisizione standardizzato** (postura, posizione del telefono sul torace,
  superficie, durata minima, assenza di vibrazioni ambientali) documentato insieme ai dati.
- **Considerazioni regolatorie**: se l'obiettivo è un dispositivo medico (MDR/IEC 62304),
  impostare fin da subito tracciabilità requisiti-test e gestione del rischio (ISO 14971).
