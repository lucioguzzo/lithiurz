# Compilazione

## Prerequisiti

| Strumento | Versione usata | Note |
|-----------|----------------|------|
| Flutter | 3.47.7 stable (Dart 3.13.5) | minimo richiesto: Flutter 3.29 / Dart 3.7 (`whisper_ggml`) |
| JDK | 17 o 21 | |
| Android SDK | **platform 37** (`platforms;android-37.0`), build-tools 37 | `receive_sharing_intent` 1.9 richiede `compileSdk 37`; è già impostato in `android/app/build.gradle.kts` |
| Android NDK | quella richiesta dal plugin Flutter (si scarica da sola) | whisper.cpp è compilato nativamente |
| Xcode | 15+ su macOS | **solo** per iOS |
| Python | 3.11+ | solo per il backend |

## App Flutter

```bash
cd brief
flutter pub get
flutter analyze          # deve restituire "No issues found!"
flutter test             # 85 test unitari
```

### Android

```bash
flutter build apk --debug                       # APK di prova
flutter build apk --release                     # APK firmato (vedi sotto)
flutter build appbundle --release               # AAB per il Play Store
```

Con i propri identificativi AdMob e il backend:

```bash
flutter build appbundle --release \
  --dart-define=BRIEF_BACKEND_URL=https://api.tuodominio.it \
  --dart-define=BRIEF_APP_TOKEN=token-pubblico-dell-app \
  --dart-define=ADMOB_BANNER_ANDROID=ca-app-pub-XXXX/YYYY
```

**Firma.** Senza `android/key.properties` la build di release usa le chiavi
di debug: funziona sul telefono ma **non è pubblicabile**. Per firmarla
davvero, creare il keystore e il file:

```properties
# android/key.properties  (da NON versionare)
storePassword=...
keyPassword=...
keyAlias=upload
storeFile=/percorso/assoluto/upload-keystore.jks
```

**ID applicazione AdMob.** In `android/app/src/main/AndroidManifest.xml` c'è
l'ID **di test** di Google. Va sostituito con il proprio prima della
pubblicazione: senza il tag l'app va in crash all'avvio di MobileAds.

**Dimensione.** L'APK contiene whisper.cpp e FFmpeg per quattro ABI. Per lo
store usare l'App Bundle (`appbundle`), che consegna al telefono solo la
propria architettura; per la distribuzione diretta conviene
`flutter build apk --split-per-abi`.

### iOS

Richiede macOS. I file della Share Extension sono già nel repository, ma il
target va creato una volta in Xcode: la procedura completa è in
[`IOS.md`](IOS.md).

```bash
flutter config --enable-swift-package-manager   # richiesto da receive_sharing_intent
cd brief
flutter build ios --release                     # dopo aver creato il target
```

Il deployment target è **15.6** (richiesto da `whisper_ggml`), già impostato
nel progetto.

## Backend

```bash
cd backend
python -m venv .venv && source .venv/bin/activate
pip install -r requirements-dev.txt
python -m pytest                 # 39 test, senza rete e senza chiavi
cp .env.example .env             # poi scegliere provider e chiave
uvicorn app.main:app --host 0.0.0.0 --port 8000
```

Con Docker:

```bash
cd backend
docker build -t brief-backend .
docker run -p 8000:8000 --env-file .env -v "$PWD/dati:/dati" brief-backend
```

Senza chiavi API il backend parte lo stesso con il provider `echo`, che
produce riassunti fittizi: utile per provare l'app end-to-end.

## Esito delle verifiche in questo ambiente

- `flutter analyze` → nessun problema.
- `flutter test` → tutti i test superati.
- `python -m pytest` (backend) → tutti i test superati.
- **APK di debug: compilato davvero** (`app-debug.apk`, 232 MB con tutte e
  quattro le architetture). Contiene `libwhisper.so` e le librerie FFmpeg,
  dichiara i filtri `SEND`/`SEND_MULTIPLE`, `minSdk 24`, `compileSdk 37`.
  È grande perché è una build di debug non divisa per ABI: per la
  distribuzione si usa l'App Bundle o `--split-per-abi`.
- **APK di release: compilati davvero** con `--split-per-abi` e R8 attivo:
  `app-arm64-v8a-release.apk` 41 MB, `app-armeabi-v7a-release.apk` 51 MB,
  `app-x86_64-release.apk` 44 MB. Firmati con la chiave di debug, perché
  qui non c'è un keystore: installabili su un telefono, **non**
  pubblicabili.
- Nota sull'ambiente: il container usato ha l'IP di uscita limitato
  (HTTP 429) da `repo.maven.apache.org`. È stato aggirato con un
  `~/.gradle/init.gradle` che riscrive l'URL di Maven Central verso il
  mirror ufficiale su Google Cloud Storage — un accorgimento dell'ambiente,
  non del progetto: su una macchina normale e in CI non serve.
- Immagine Docker del backend: **non costruita** qui (nel container non
  c'è un demone Docker). Il `Dockerfile` è verificato dal workflow di CI,
  che esegue `docker build`.
- Build iOS: **non eseguita**, serve macOS con Xcode.
