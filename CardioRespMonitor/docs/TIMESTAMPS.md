# Timestamp, sincronizzazione e ricostruzione della timeline

## I due clock

| | `SensorEvent.timestamp` | `System.currentTimeMillis()` |
|---|---|---|
| Unità | nanosecondi | millisecondi |
| Origine | boot del dispositivo | 1970-01-01 UTC (epoch) |
| Chi lo assegna | HAL / sensor hub, **al momento del campionamento** | il kernel, al momento della chiamata |
| Monotono | sì | **no**: salta con NTP, cambio ora manuale, rete cellulare |
| Continua in deep sleep | sì (`elapsedRealtimeNanos`) | sì |
| Uso corretto | intervalli, DSP, frequenza, jitter | etichetta assoluta, confronto con altri dispositivi |

Su Android moderno `event.timestamp` è nel time base di `SystemClock.elapsedRealtimeNanos()`.
Alcuni device vecchi usavano `System.nanoTime()` (uptime, che si ferma in deep sleep):
l'app lo **rileva** al primo evento (`TimeBaseCalibrator.detect`) e lo scrive nei metadati.

Leggere `System.currentTimeMillis()` dentro `onSensorChanged` sarebbe sbagliato due volte:
misura l'istante di *consegna* (che include latenza variabile di HAL, binder, scheduler) e
può saltare durante la registrazione.

## Come viene calcolato `timestamp_unix_ms`

1. Al primo evento si misura `offset_ns = currentTimeMillis·10⁶ − elapsedRealtimeNanos()`,
   ripetendo la lettura 15 volte e tenendo la coppia con la finestra più stretta
   (incertezza salvata in `unix_offset_uncertainty_ns_at_start`, tipicamente ≈ 1 ms).
2. Per ogni campione: `timestamp_unix_ms = floor((timestamp_android_ns + offset_ns) / 10⁶)`.
3. L'offset è **fisso** per tutta la sessione: la colonna unix ha la stessa regolarità della
   colonna android. Allo stop l'offset viene rimisurato: `clock_drift_ns` nei metadati indica
   quanto il wall clock si è spostato (NTP) durante la sessione.

## Ricostruire la timeline (es. Python)

```python
import pandas as pd, numpy as np, json
df  = pd.read_csv("samples.csv")
meta = json.load(open("metadata.json"))

# Tempo relativo in secondi: SEMPRE dal timestamp sensore
t = (df.timestamp_android_ns - df.timestamp_android_ns.iloc[0]) / 1e9

# Frequenza reale e buchi
dt = np.diff(df.timestamp_android_ns) / 1e6           # ms
fs = 1e3 / np.median(dt)
gaps = np.where(dt > 1.5 * np.median(dt))[0]

# Griglia uniforme per la DSP
tu = np.arange(t.iloc[0], t.iloc[-1], 1 / fs)
acc_z_u = np.interp(tu, t, df.acc_z)

# Allineamento con un dispositivo esterno (ECG) in tempo UTC
utc = pd.to_datetime(df.timestamp_unix_ms, unit="ms", utc=True)
```

Per la precisione sub-millisecondo con dispositivi esterni non affidarsi al wall clock:
usare un evento fisico comune (tap sul telefono visibile anche nell'ECG/video) e stimare
l'offset per cross-correlazione.

## raw_events.csv vs samples.csv

- `raw_events.csv`: ogni evento come consegnato (`sensor,timestamp_android_ns,timestamp_unix_ms,x,y,z`).
  Due flussi interlacciati, ciascuno con i propri timestamp.
- `samples.csv`: un riga per campione dell'accelerometro, giroscopio interpolato a quell'istante.
