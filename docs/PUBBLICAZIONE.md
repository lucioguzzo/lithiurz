# Preparazione alla pubblicazione

## Google Play

1. **Identificatore** — `com.lucio.vocenota`: cambiarlo prima del primo
   caricamento se serve, dopo non è più possibile.
2. **Firma** — creare il keystore e `android/key.properties`
   (vedi `COMPILAZIONE.md`), poi attivare Play App Signing.
3. **AdMob** — creare l'app su AdMob, sostituire nel manifest
   `com.google.android.gms.ads.APPLICATION_ID` e passare le unità con
   `--dart-define=ADMOB_BANNER_ANDROID=…`. Finché restano gli ID di test
   non si guadagna nulla, ma nemmeno si rischia la sospensione.
4. **Consenso pubblicitario (UE)** — integrare Google UMP. Obbligatorio.
5. **Scheda** — nome, descrizione, grafiche, almeno 2 screenshot per
   formato, icona 512×512, immagine di copertina 1024×500.
6. **Sicurezza dei dati** — compilare come indicato in `PRIVACY.md`.
7. **Pubblico e contenuti** — questionario sui contenuti, app non destinata
   ai minori di 13 anni.
8. **URL dell'informativa** — pubblicare `PRIVACY.md` su una pagina web.
9. **Caricare l'AAB** (`flutter build appbundle --release`), provare in
   test interno prima della produzione.

## App Store

1. Completare la configurazione iOS (`IOS.md`) e compilare su macOS.
2. Identificativi: app e Share Extension annidati, App Group condiviso.
3. **App Privacy** — compilare la scheda come in `PRIVACY.md`.
4. **ATT** — se AdMob usa l'IDFA serve `NSUserTrackingUsageDescription` e il
   prompt di tracciamento; se non lo si vuole, configurare AdMob in modalità
   non personalizzata.
5. **Revisione** — spiegare chiaramente nel modulo di revisione che la
   trascrizione è locale e che al server viene inviato solo testo, con
   consenso. Fornire un audio di prova.
6. Screenshot per tutte le dimensioni richieste; icona 1024×1024 senza
   trasparenza.

## Prima di entrambi

- [ ] Icona e schermata di avvio personalizzate (oggi predefinite Flutter)
- [ ] Pagina delle licenze open source raggiungibile dalle impostazioni
- [ ] Informativa privacy pubblicata online
- [ ] Backend in esercizio, con HTTPS e volume persistente
- [ ] `VOCENOTA_BACKEND_URL` e `VOCENOTA_APP_TOKEN` impostati nella build
- [ ] Prove su almeno tre dispositivi reali, uno di fascia bassa
- [ ] Prova con audio lungo (30+ minuti) e con interruzione dell'app
- [ ] Verifica delle quote: raggiungere il limite e controllare che l'app
      ripieghi sul riassunto locale senza errori visibili
