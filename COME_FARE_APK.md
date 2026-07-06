# Come ottenere l'APK — script automatico

Non riesco a compilare l'APK nel mio ambiente (è isolato e non può scaricare Flutter/Android SDK). Ho quindi preparato uno **script che fa tutto dal tuo PC**: crea un repository GitHub privato, carica il progetto, lo compila sui server di GitHub e ti scarica l'APK finito. Tu devi solo lanciarlo e fare login a GitHub una volta.

## Cosa ti serve
- Un account **GitHub** gratuito (se non ce l'hai: https://github.com/signup).
- Windows con connessione a internet. Lo script installa da solo Git e GitHub CLI se mancano (tramite winget, già presente su Windows 10/11).

## Come si usa
1. Doppio click su **`COMPILA_APK.bat`** nella cartella del progetto.
2. Se è la prima volta, si aprirà il browser per l'accesso a GitHub: autorizza e torna allo script.
3. Aspetta ~10 minuti (lo script mostra l'avanzamento della build).
4. A fine build si apre una cartella **`APK`** con dentro `app-release.apk`.

## Installare l'APK sul telefono
Copia `app-release.apk` sul telefono Android (cavo USB, Google Drive, Telegram…), aprilo e consenti l'installazione da "origini sconosciute". Fatto.

## Se qualcosa va storto
Lo script stampa il link diretto alla build su GitHub (`.../actions/...`): da lì vedi il log ed eventualmente scarichi l'APK a mano dalla sezione **Artifacts**. Il repository creato è **privato**: solo tu lo vedi.

---
In alternativa, senza script: crea un repo su GitHub, carica il contenuto di questa cartella (inclusa `.github`), vai in **Actions → Build APK → Run workflow**, e scarica l'artifact a fine build.
