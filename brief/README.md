# BRIEF - Vocal Sum Up — app Flutter

Trascrizione locale (whisper.cpp) e riassunto di audio condivisi da altre
app. Documentazione completa in [`../docs`](../docs):

- [Architettura](../docs/ARCHITETTURA.md)
- [Compilazione](../docs/COMPILAZIONE.md)
- [Configurazione iOS](../docs/IOS.md)
- [Privacy](../docs/PRIVACY.md) · [Pubblicazione](../docs/PUBBLICAZIONE.md)
- [Attività residue](../docs/ATTIVITA_RESIDUE.md)

```bash
flutter pub get
flutter analyze
flutter test
flutter build apk --debug
```

## Mappa del codice

| Percorso | Responsabilità |
|---|---|
| `lib/core/` | costanti, errori, annullamento, formattazioni |
| `lib/domain/` | modelli immutabili |
| `lib/data/` | impostazioni (SharedPreferences) e cronologia (SQLite) |
| `lib/services/audio/` | pianificazione blocchi, ffprobe/ffmpeg, ricucitura testi |
| `lib/services/share/` | ingresso dai menu Condividi e import manuale |
| `lib/services/transcription/` | modelli whisper, orchestrazione, ripresa |
| `lib/services/summary/` | riassunto locale, client del backend |
| `lib/services/pipeline/` | `ProcessingController`: stato dell'elaborazione |
| `lib/ui/` | tema, schermate, widget |

## Parametri di compilazione

| `--dart-define` | Uso |
|---|---|
| `BRIEF_BACKEND_URL` | server dei riassunti (vuoto = solo locale) |
| `BRIEF_APP_TOKEN` | token pubblico accettato dal backend |
| `ADMOB_BANNER_ANDROID` | unità banner Android (vuoto = ID di test) |
| `ADMOB_BANNER_IOS` | unità banner iOS (vuoto = ID di test) |
