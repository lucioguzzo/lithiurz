# CardioResp Monitor

App Android nativa (Kotlin, Jetpack Compose, Clean Architecture + MVVM) per acquisire
**accelerometro** e **giroscopio** con la massima qualità temporale possibile, visualizzarli in
tempo reale, registrarli in CSV e predisporre l'integrazione di futuri algoritmi di stima
della frequenza cardiaca e respiratoria (**non implementati** in questa fase).

Documentazione:
- [`docs/ARCHITECTURE_REVIEW.md`](docs/ARCHITECTURE_REVIEW.md): revisione critica e miglioramenti per la validazione scientifica
- [`docs/TIMESTAMPS.md`](docs/TIMESTAMPS.md): `event.timestamp` vs `currentTimeMillis`, sincronizzazione, ricostruzione della timeline
- [`docs/FUTURE_ALGORITHMS.md`](docs/FUTURE_ALGORITHMS.md): dove e come inserire gli algoritmi HR/RR

## Funzionalità

- START / STOP, indicatore RECORDING, notifica permanente con cronometro e pulsante STOP
- Frequenza selezionabile: Fastest, 500, 200, 100, 50 Hz (`registerListener` con `samplingPeriodUs` esplicito)
- Opzioni: sensori *uncalibrated*, batching FIFO hardware
- Grafici real-time X/Y/Z (finestra 5 s, ~30 FPS, decimazione min/max per pixel)
- Contatori: campioni, frequenza effettiva, durata
- Diagnostica: frequenza effettiva, Δt medio, deviazione standard (jitter RMS), jitter di picco,
  Δt min/max, campioni persi stimati, buchi, timestamp non monotoni, overflow del ring buffer,
  campioni senza gyro, drop della coda algoritmi, time base rilevato
- Per ogni sessione: `samples.csv`, `raw_events.csv`, `metadata.json`
- EXPORT CSV / EXPORT ZIP tramite Storage Access Framework

## Formato CSV

`samples.csv`:
```
timestamp_android_ns,timestamp_unix_ms,acc_x,acc_y,acc_z,gyro_x,gyro_y,gyro_z
183746251234567,1791417600123,0.0123,0.2201,9.7964,0.0012,-0.0031,0.0004
```
Unità: m/s², rad/s. Separatore `,`, punto decimale `.`, valori mancanti `NaN`.

## Architettura

```
 presentation/  MainActivity, MainViewModel, Compose UI   ── osserva ──┐
 domain/        modelli, interfacce repository, use case               │
 data/          AcquisitionRepositoryImpl, AcquisitionSession  ◄───────┘
 sensor/        SensorDataSource, SensorRingBuffer, SampleAligner,
                TimeBaseCalibrator, StreamStatsCalculator, ChartBuffer
 storage/       CsvRecordingWriter, RawEventWriter, SessionMetadata, SessionStorage, SafExporter
 service/       AcquisitionService (Foreground Service), AcquisitionNotification
 future_algorithms/  SignalProcessor, ProcessorRegistry, SignalProcessingPipeline, dsp/, heart/, respiration/
 di/            AppContainer (DI manuale)
```

Dipendenze: `presentation → domain ← data → sensor/storage/future_algorithms`.
La UI non conosce `SensorManager`, il Service non conosce la UI.

### Thread

| Thread | Priorità | Lavoro |
|---|---|---|
| `SensorAcquisition` (HandlerThread) | URGENT_DISPLAY | `onSensorChanged`: copia 3 float + timestamp nel ring buffer. Nient'altro. |
| `AcquisitionPipeline` (executor single-thread → dispatcher) | DISPLAY | drain ogni 4 ms, statistiche, allineamento acc/gyro, scrittura CSV, buffer grafico |
| `SignalProcessing` (executor single-thread) | normale | algoritmi futuri, alimentati da `Channel` |
| Main/UI | – | Compose; legge uno snapshot del buffer grafico ogni 33 ms |

Un HandlerThread dedicato è preferibile a un dispatcher di coroutine per i listener perché
`registerListener` richiede un `Handler`/`Looper`; i dispatcher condivisi (`Default`, `IO`)
possono essere saturati da altro lavoro dell'app. I thread single-thread rendono tutto lo stato
della pipeline confinato: nessun lock salvo il ring buffer SPSC.

### Buffering: ring buffer vs Channel

| | Ring buffer SPSC (array primitivi) | `Channel<SensorSample>` |
|---|---|---|
| Allocazioni | zero | un oggetto per campione |
| Blocco del produttore | mai | mai con `trySend` |
| Capacità | fissa (65 536 eventi ≈ 65 s a 1 kHz) | configurabile |
| Consumo | polling | suspend |
| Usato per | **sensore → pipeline** (percorso critico) | **pipeline → algoritmi** (non critico) |

### Rate di acquisizione vs rate di rendering

Il thread pipeline scrive ogni campione in un `ChartBuffer` circolare. Il `MainViewModel`, solo
mentre si registra e solo se la UI è visibile, emette ogni 33 ms uno snapshot (copia) degli
ultimi 5 s. Il grafico decima per pixel. Sensore a 500 Hz, schermo a 30 FPS: nessuno dei due
aspetta l'altro.

## Compilazione in Android Studio

Requisiti: **Android Studio Ladybug (2024.2) o più recente**, JDK 17+ (quello incluso in Android Studio va bene),
Android SDK Platform 35.

1. `File → Open…` e selezionare la cartella **`CardioRespMonitor`** (quella con `settings.gradle.kts`).
2. Attendere il *Gradle Sync* (scarica AGP 8.7.3, Kotlin 2.0.21, Compose BOM 2024.12.01).
   Se Android Studio chiede di installare la SDK Platform 35, accettare.
3. Collegare uno smartphone **reale** (l'emulatore ha sensori simulati e timing non rappresentativo)
   con *Opzioni sviluppatore → Debug USB* attivo.
4. Selezionare la configurazione `app` e premere **Run ▶**.
5. Per una build ottimizzata: `Build → Generate App Bundles or APKs → Build APK(s)` oppure
   da terminale:
   ```bash
   ./gradlew testDebugUnitTest   # test unitari
   ./gradlew assembleRelease     # app/build/outputs/apk/release/app-release.apk
   ```
   La release è firmata con la chiave di debug per comodità di test: sostituire la
   `signingConfig` in `app/build.gradle.kts` prima di distribuire.

### Prima di una registrazione seria

- Usare la build **release** (R8 attivo, niente overhead di debug).
- Escludere l'app dall'ottimizzazione batteria: *Impostazioni → App → CardioResp Monitor → Batteria → Senza restrizioni*.
  Su Xiaomi/Huawei/Samsung disattivare anche le funzioni proprietarie di "app sleeping".
- Chiudere le altre app, disattivare vibrazione e notifiche (le vibrazioni finiscono nel segnale).
- Fare una registrazione di prova di 2 minuti e controllare la diagnostica: `Overflow ring buffer`
  e `Timestamp non monotoni` devono essere 0, `Campioni persi` deve restare 0 o quasi.
  Annotare frequenza effettiva e jitter RMS: sono la "firma" temporale di quel device e
  vanno riportati nella sezione metodi di qualunque studio.

### Recuperare i file senza export

```bash
adb pull /sdcard/Android/data/com.cardioresp.monitor/files/recordings/
```

## Dipendenze

| Libreria | Versione | Uso |
|---|---|---|
| Android Gradle Plugin | 8.7.3 | build |
| Kotlin + Compose compiler plugin | 2.0.21 | linguaggio, Compose |
| Compose BOM (ui, material3) | 2024.12.01 | UI |
| androidx.activity:activity-compose | 1.9.3 | Activity + launcher SAF/permessi |
| androidx.lifecycle (runtime, viewmodel-compose, service) | 2.8.7 | MVVM, `LifecycleService` |
| kotlinx-coroutines-android | 1.9.0 | dispatcher dedicati, Flow |
| junit | 4.13.2 | test |

Nessuna libreria di grafici esterna e nessun framework DI: meno dipendenze, costo per frame
controllato, build senza annotation processing.
