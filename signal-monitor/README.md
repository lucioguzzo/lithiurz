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

## Attribuzione per frequenza

Android spesso consegna le celle vicine **senza codice operatore** (MCC/MNC
vuoti per le celle non registrate). In quel caso l'app deduce il gestore dal
canale radio (EARFCN/NR-ARFCN/UARFCN/ARFCN): in Italia ogni blocco di frequenze
è assegnato a un operatore preciso, quindi il canale lo identifica. Esempi in
banda 800 MHz: WindTre 6200, TIM 6300, Vodafone 6400. La mappa completa è in
`BandMap.kt`, ricavata dalle assegnazioni di spettro italiane. Queste letture
sono contrassegnate con `*`.

## Limitazioni importanti

- **Il modem riporta alle app quasi solo le celle della rete su cui il telefono
  è registrato.** Non è un limite dell'app: `getAllCellInfo` restituisce la
  cella servente e i suoi vicini, non le reti degli altri operatori. Per questo
  su molti telefoni TIM/Vodafone/WindTre restano vuoti tranne quello della SIM.
- L'unica API che esegue una vera ricerca di **tutte** le reti è
  `TelephonyManager.requestNetworkScan`, che richiede il permesso di sistema
  `MODIFY_PHONE_STATE`: Android lo riserva alle app di sistema e a quelle con
  privilegi operatore. L'app include il pulsante **Scansione completa reti** che
  la tenta comunque e riporta l'esito — funziona se l'app viene installata come
  app di sistema o su build OEM che lo consentono.
- Il modo affidabile per monitorare due operatori insieme resta un telefono
  **dual SIM** con SIM di gestori diversi: l'app legge ogni SIM attiva.
- Servono i permessi di **posizione** (richiesto da Android per le info di
  rete) e **telefono**; la posizione di sistema deve essere attiva.

## Memoria delle celle

Le celle rilevate restano in elenco per 5 minuti dall'ultimo avvistamento,
marcate con il tempo trascorso (`visto 2 min fa`) e con la scheda attenuata.
Questo permette di catturare gli operatori che compaiono solo per pochi istanti
(ad esempio durante un cambio cella o una ricerca di rete).

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
