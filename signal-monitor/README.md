# Monitor Segnale — TIM / Vodafone / WindTre

App Android nativa (Kotlin) che monitora in tempo reale la potenza del segnale
(in dBm) degli operatori italiani **TIM**, **Vodafone** e **WindTre (Wind)**.

## Come funziona

Ogni 2 secondi l'app interroga il modem del telefono
(`TelephonyManager.requestCellInfoUpdate` su Android 10+, `getAllCellInfo` sulle
versioni precedenti) e legge tutte le celle radio visibili (2G/3G/4G/5G).
Ogni cella viene attribuita all'operatore tramite il codice MCC/MNC:

| Operatore | MCC-MNC |
|-----------|---------|
| TIM | 222-01 |
| Vodafone | 222-10, 222-06 |
| WindTre (Wind) | 222-88, 222-99 |

Per ciascun operatore viene mostrata la cella con il segnale migliore: valore in
dBm, barra di intensità, qualità (Ottimo/Buono/Discreto/Debole) e tecnologia
(2G/3G/4G/5G). In fondo c'è l'elenco completo delle celle rilevate.

## Limitazioni importanti

- **Android mostra con certezza solo la rete della SIM attiva.** Le celle degli
  altri operatori compaiono solo se il modem le riporta come "celle vicine":
  dipende dal chipset e dal firmware del telefono. Su molti dispositivi si
  vedono, su altri no. Con due SIM di operatori diversi si monitorano
  entrambi in modo affidabile.
- Servono i permessi di **posizione** (richiesto da Android per le info di
  rete) e **telefono**; la posizione di sistema deve essere attiva.

## Compilare l'APK

Requisiti: JDK 17+ e Android SDK (piattaforma 34).

```bash
cd signal-monitor
./gradlew assembleDebug
# APK in: app/build/outputs/apk/debug/app-debug.apk
```

In alternativa, il workflow GitHub Actions
`.github/workflows/build-signal-monitor.yml` compila l'APK a ogni push nella
cartella `signal-monitor/` e lo pubblica come artifact scaricabile
(scheda **Actions** → run → *signal-monitor-debug-apk*).

## Installazione

Copia `app-debug.apk` sul telefono e aprilo (serve abilitare l'installazione da
origini sconosciute). Al primo avvio concedi i permessi richiesti.
