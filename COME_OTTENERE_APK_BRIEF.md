# Come ottenere l'APK di BRIEF

Non serve installare niente sul tuo computer: l'APK lo compila GitHub.

1. Vai su **Actions** nel repository.
2. Scegli **BRIEF — APK di release** nell'elenco a sinistra.
3. Premi **Run workflow** e **incolla l'indirizzo del tuo backend** nel
   campo (per esempio `https://api.tuodominio.it`): è quello che rende
   l'APK pronto all'uso, senza niente da configurare per chi lo installa.
   Se lo lasci vuoto l'app funziona lo stesso, ma solo in locale: Whisper
   sul telefono, con il modello da scaricare al primo utilizzo.
   Come mettere online il backend: [`docs/DEPLOY.md`](docs/DEPLOY.md).
4. Aspetta la fine della compilazione (la prima volta ~15 minuti).
5. In fondo alla pagina della build, sotto **Artifacts**, scarica
   `brief-release-apk`.

Dentro trovi un APK per ogni tipo di telefono. Nel dubbio installa
**`app-arm64-v8a-release.apk`**: è quello giusto per quasi tutti gli
Android degli ultimi anni.

## Installarlo sul telefono

Copia l'APK sul telefono (cavo USB, Google Drive, Telegram…), aprilo e
consenti l'installazione da "origini sconosciute".

Al primo avvio l'app chiede una cosa sola: se trascrivere nel cloud
(pronto subito, serve internet) o solo sul telefono. Scegliendo il
telefono, alla prima trascrizione scarica il modello Small compresso
(252 MB) e il rilevatore di parlato (poco più di 1 MB): si scaricano una
volta sola e dopo tutto funziona anche senza connessione. La scelta si
cambia quando si vuole dalle impostazioni.

## Due avvertenze

- Finché non inserisci i tuoi identificativi AdMob, l'app mostra gli
  **annunci di test** di Google: è giusto così durante lo sviluppo, non
  genera guadagni e non rischia la sospensione dell'account.
- Senza un keystore configurato nei segreti del repository, l'APK è
  firmato con la chiave di debug: si installa sul telefono ma **non è
  caricabile sul Play Store**. I passaggi per la firma vera sono in
  [`docs/COMPILAZIONE.md`](docs/COMPILAZIONE.md).
