# Come ottenere l'APK di BRIEF

Non serve installare niente sul tuo computer: l'APK lo compila GitHub.

1. Vai su **Actions** nel repository.
2. Scegli **BRIEF — APK di release** nell'elenco a sinistra.
3. Premi **Run workflow**. Se hai già un backend per i riassunti, incolla
   il suo indirizzo nel campo; altrimenti lascialo vuoto: l'app userà il
   riassunto locale, che funziona senza internet.
4. Aspetta la fine della compilazione (la prima volta ~15 minuti).
5. In fondo alla pagina della build, sotto **Artifacts**, scarica
   `brief-release-apk`.

Dentro trovi un APK per ogni tipo di telefono. Nel dubbio installa
**`app-arm64-v8a-release.apk`**: è quello giusto per quasi tutti gli
Android degli ultimi anni.

## Installarlo sul telefono

Copia l'APK sul telefono (cavo USB, Google Drive, Telegram…), aprilo e
consenti l'installazione da "origini sconosciute".

Alla prima trascrizione l'app chiede di scaricare il modello (circa
150 MB per Base). Si scarica una volta sola; dopo funziona anche senza
connessione.

## Due avvertenze

- Finché non inserisci i tuoi identificativi AdMob, l'app mostra gli
  **annunci di test** di Google: è giusto così durante lo sviluppo, non
  genera guadagni e non rischia la sospensione dell'account.
- Senza un keystore configurato nei segreti del repository, l'APK è
  firmato con la chiave di debug: si installa sul telefono ma **non è
  caricabile sul Play Store**. I passaggi per la firma vera sono in
  [`docs/COMPILAZIONE.md`](docs/COMPILAZIONE.md).
