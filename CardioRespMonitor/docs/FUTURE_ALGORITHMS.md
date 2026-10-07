# Dove integrare gli algoritmi di frequenza cardiaca e respiratoria

L'acquisizione non va toccata. Tutto il codice nuovo va in
`app/src/main/java/com/cardioresp/monitor/future_algorithms/`.

## Flusso dei dati

```
AcquisitionSession (thread AcquisitionPipeline)
   └─ SampleAligner ─► samples.csv
                    └─► SignalProcessingPipeline.submit()  (trySend, mai bloccante)
                              │ Channel(16 384)
                              ▼  thread "SignalProcessing"
                         per ogni SignalProcessor registrato: process(sample)
                              │
             HeartRateProcessor.estimates / RespirationRateProcessor.estimates (StateFlow)
                              ▼
       AcquisitionRepository.heartRate / respirationRate ─► MainViewModel ─► UI (card "Stime")
```

## Passi per aggiungere un algoritmo di frequenza cardiaca

1. **Creare** `future_algorithms/heart/ScgHeartRateProcessor.kt`:

```kotlin
class ScgHeartRateProcessor : HeartRateProcessor {
    private val _estimates = MutableStateFlow<HeartRateEstimate?>(null)
    override val estimates: StateFlow<HeartRateEstimate?> = _estimates

    private lateinit var resampler: UniformResampler
    private val window = FloatArray(10 * 100)   // 10 s @ 100 Hz
    private var filled = 0
    private var lastGridNs = 0L

    override fun onSessionStart(context: ProcessingContext) {
        _estimates.value = null
        filled = 0
        resampler = UniformResampler(
            targetRateHz = 100.0,
            onGap = { _, _ -> filled = 0 },            // buco: si ricomincia la finestra
            output = { t, v -> push(t, v[2]) }         // v[2] = acc_z
        )
    }

    override fun process(sample: SensorSample) = resampler.push(sample)

    private fun push(tNs: Long, accZ: Float) {
        // 1) filtraggio passa-banda, 2) finestra scorrevole,
        // 3) ogni 1 s: autocorrelazione / spettro -> bpm + confidenza
        // _estimates.value = HeartRateEstimate(tNs, bpm, confidence, 10f, "SCG-autocorr v1")
    }
}
```

2. **Registrarlo** in `future_algorithms/ProcessorRegistry.kt` sostituendo
   `PlaceholderHeartRateProcessor()` con `ScgHeartRateProcessor()`.

Fine: la UI mostra automaticamente il valore nella card "Stime cardiorespiratorie",
perché `AcquisitionRepositoryImpl` prende le `estimates` del primo `HeartRateProcessor`
registrato.

## Respirazione

Identico, implementando `RespirationRateProcessor` in
`future_algorithms/respiration/` e sostituendo `PlaceholderRespirationRateProcessor()`
nel registry. Suggerimenti nel KDoc del segnaposto.

## Utility DSP condivise

Filtri (biquad/Butterworth), FFT, finestre, detrend vanno in `future_algorithms/dsp/`
accanto a `UniformResampler`, con test in `app/src/test/.../future_algorithms/dsp/`.

## Regole da rispettare

| Regola | Motivo |
|---|---|
| Usare `sensorTimestampNs`, mai l'indice del campione | la frequenza reale ≠ nominale; ci sono buchi |
| Gestire i `NaN` del giroscopio | sono buchi reali, non zeri |
| Calcoli pesanti ammessi, ma throughput medio ≥ frequenza di campionamento | altrimenti la coda (≈30 s a 500 Hz) si riempie e i campioni in eccesso sono scartati per gli algoritmi (`processorQueueDrops`); la registrazione CSV non ne risente |
| Nessuna sincronizzazione necessaria | `process` è sempre chiamato dallo stesso thread |
| Ogni stima con `confidence` e nome/versione `algorithm` | indispensabile per la validazione (Bland-Altman vs ECG / fascia) |

## Salvare le stime (passo successivo opzionale)

Per confrontarle offline con il gold standard: creare in `storage/` un `EstimatesCsvWriter`
e un processore `EstimatesRecorder : SignalProcessor` che osserva le `estimates` e le scrive in
`estimates.csv` nella cartella della sessione. Anche questo si aggiunge al registry senza
modificare il resto.

## Validare offline prima che on-device

Gli algoritmi sono classi Kotlin pure: si possono eseguire in un test JVM sui file
`samples.csv` già registrati (lettura CSV → `SensorSample` → `process`) e confrontare le
stime con il riferimento, senza telefono.
