# iOS — completare la configurazione

Il codice iOS è nel repository, ma **due cose vanno fatte in Xcode una volta
sola**, perché richiedono di modificare il progetto (`project.pbxproj`):
creare il target della Share Extension e attivare l'App Group.

> Dichiarazione onesta: questa parte **non è stata compilata né provata**.
> Serve un Mac con Xcode. I file sorgente ci sono tutti e seguono la
> documentazione ufficiale di `receive_sharing_intent` 1.9.0.

## File già presenti

```
ios/Runner/Info.plist               AppGroupId, schema URL, tipi di documento audio
ios/Runner/Runner.entitlements      gruppo app  group.com.lucio.brief
ios/Share Extension/
    ShareViewController.swift       eredita da RSIShareViewController
    Info.plist                      attivazione su file/allegati
    MainInterface.storyboard        storyboard minima richiesta dal sistema
    ShareExtension.entitlements     stesso gruppo app
```

## Passi in Xcode

1. **Swift Package Manager**
   ```bash
   flutter config --enable-swift-package-manager
   cd brief && flutter pub get
   ```
   `receive_sharing_intent` è distribuito solo come Swift Package.

2. **Creare il target** — `open ios/Runner.xcworkspace`, poi
   *File → New → Target… → Share Extension*, nome **Share Extension**,
   lingua Swift. Xcode genera dei file: **sostituirli** con quelli già
   presenti in `ios/Share Extension/` (o cancellare i generati e aggiungere
   i nostri al target).

3. **Stesso deployment target** di Runner: **15.6**.

4. **App Group** — scheda *Signing & Capabilities*, su **entrambi** i target
   (Runner e Share Extension): *+ Capability → App Groups*, e selezionare
   `group.com.lucio.brief` (o il proprio, aggiornando i due
   `.entitlements`).

5. **Variabile CUSTOM_GROUP_ID** — *Build Settings → + → Add User-Defined
   Setting* su **entrambi** i target: nome `CUSTOM_GROUP_ID`, valore
   `group.com.lucio.brief`. I due `Info.plist` la leggono.

6. **Collegare il package all'estensione** — target Share Extension →
   *General → Frameworks and Libraries → +* → libreria
   `receive-sharing-intent`.

7. **Ordine delle Build Phases** — nel target Runner spostare
   *Embed Foundation Extension* **sopra** *Thin Binary*, altrimenti la
   compilazione fallisce con `no such module 'receive_sharing_intent'`.

8. **Firma** — team di sviluppo su entrambi i target; i due bundle id devono
   essere annidati, es. `com.lucio.brief` e
   `com.lucio.brief.ShareExtension`.

## Prove da fare sul dispositivo

| Scenario | Atteso |
|---|---|
| WhatsApp → nota vocale → Condividi → BRIEF, **app chiusa** | l'app si apre, mostra l'anteprima, poi trascrive |
| Stesso flusso con **app in background** | stesso risultato, senza riavvio |
| Stesso flusso con **app già aperta** | l'anteprima compare subito |
| Condivisione di 2+ file | elabora il primo e lo dice chiaramente |
| File non audio (PDF) | messaggio d'errore, nessun blocco |
| Audio di 30+ minuti | suddivisione in blocchi, avanzamento, ripresa dopo chiusura |
| Lettura vocale | voce italiana; se manca, il sistema propone di scaricarla |

## Limiti noti su iOS

- **Formati** — la conversione passa da FFmpeg (incluso in `whisper_ggml`),
  quindi anche Ogg/Opus di WhatsApp funziona. L'**anteprima** ascoltabile usa
  invece il lettore di sistema, che Ogg/Opus non supporta: in quel caso
  l'anteprima si disattiva da sola e lo schermo lo dice, mentre trascrizione
  e riassunto funzionano lo stesso.
- **Background** — iOS sospende l'app quando passa in secondo piano. Per
  questo ogni blocco completato viene salvato: al rientro compare "Riprendi".
  Un'elaborazione lunga va tenuta in primo piano.
- **Memoria** — su iPhone con 3 GB di RAM il modello Small può essere
  terminato dal sistema. L'app lo segnala ("memoria limitata") ma non offre
  alternative: Tiny e Base sono stati rimossi perché trascrivevano male.
