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
cd vocenota
flutter pub get
flutter analyze          # deve restituire "No issues found!"
flutter test             # 75 test unitari
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
  --dart-define=VOCENOTA_BACKEND_URL=https://api.tuodominio.it \
  --dart-define=VOCENOTA_APP_TOKEN=token-pubblico-dell-app \
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
cd vocenota
flutter build ios --release                     # dopo aver creato il target
```

Il deployment target è **15.6** (richiesto da `whisper_ggml`), già impostato
nel progetto.

## Backend

```bash
cd backend
python -m venv .venv && source .venv/bin/activate
pip install -r requirements-dev.txt
python -m pytest                 # 38 test, senza rete e senza chiavi
cp .env.example .env             # poi scegliere provider e chiave
uvicorn app.main:app --host 0.0.0.0 --port 8000
```

Con Docker:

```bash
cd backend
docker build -t vocenota-backend .
docker run -p 8000:8000 --env-file .env -v "$PWD/dati:/dati" vocenota-backend
```

Senza chiavi API il backend parte lo stesso con il provider `echo`, che
produce riassunti fittizi: utile per provare l'app end-to-end.

## Esito delle verifiche in questo ambiente

- `flutter analyze` → nessun problema.
- `flutter test` → tutti i test superati.
- `python -m pytest` (backend) → tutti i test superati.
- Build Android: eseguita in un container Linux il cui IP di uscita viene
  limitato (HTTP 429) da `repo.maven.apache.org`. Per aggirarlo è stato usato
  un `~/.gradle/init.gradle` che antepone il mirror ufficiale di Maven
  Central su Google Cloud Storage — un accorgimento dell'ambiente, non del
  progetto: su una macchina normale e in CI non serve.
- Build iOS: **non eseguita**, serve macOS con Xcode.
