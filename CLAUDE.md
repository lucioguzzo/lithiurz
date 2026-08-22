# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

"Baby Cry Translator" — a Flutter Android app (Italian-language UI) that records 7 seconds of a baby's cry and classifies it into 5 categories (`hungry`, `tired`, `discomfort`, `belly_pain`, `burping`) using a small MLP trained on the donateacry-corpus. Inference runs fully on-device: the model is plain JSON weights (`app/assets/model.json`), evaluated by hand-written Dart — no TFLite or ML frameworks.

Repository layout:
- `app/` — the Flutter project (Dart-only; see "Android platform files" below)
- `tools/train_model.py` — numpy-only training script that produces `model.json`
- `github/workflows/build-apk.yml` — CI workflow (note the directory name, see below)
- `build_apk.ps1` / `COMPILA_APK.bat` — Windows helper that pushes to GitHub and downloads the CI-built APK

Code comments, UI strings, commit messages, and docs are in Italian — keep new ones in Italian for consistency.

## Commands

All Flutter commands run from `app/`:

```bash
cd app
flutter pub get
flutter test                              # run all tests
flutter test test/classifier_test.dart    # run a single test file
flutter analyze                           # lint (flutter_lints, avoid_print disabled)
```

Retrain the model (requires the donateacry-corpus `cleaned_and_updated_data` folder, not in the repo; only numpy needed):

```bash
python3 tools/train_model.py <corpus_dir> app/assets/model.json          # full 5-fold CV + final model
python3 tools/train_model.py <corpus_dir> app/assets/model.json fold0    # single fold (cached in /tmp/bct_cache)
python3 tools/train_model.py <corpus_dir> app/assets/model.json final    # aggregate cached folds + train final
```

This writes `app/assets/model.json` and a sibling `_metrics.json` (the committed copy lives at `tools/model_metrics.json`).

## Critical invariant: Dart/Python feature parity

`app/lib/services/feature_extractor.dart` and the feature code in `tools/train_model.py` implement the **same pipeline** (8 kHz linear resample → peak normalize → 512-pt FFT frames, hop 256 → 13 MFCC + ZCR + log-RMS + spectral centroid → RMS-based VAD → mean/std pooling = 32 features). They must stay numerically identical, since the model is trained on the Python features and evaluated on the Dart ones.

- The tests in `app/test/` enforce this with golden values computed in Python on a deterministic synthetic signal (tolerance ~1e-6 relative).
- If you change the feature pipeline, change **both** implementations, recompute the golden vectors in Python, update both test files (they share the same 32-value vector), and retrain the model.
- If you change the model architecture (`MLP([32, 32, 16, 5])`, tanh hidden layers, softmax output) in Python, mirror it in `app/lib/services/classifier.dart`, which hard-codes the forward pass (z-score normalization with the stored `feature_mean`/`feature_std`, then dense layers).

## Architecture

Inference flow (in `home_screen.dart`): record 7 s WAV at 8 kHz mono (`record` package) → `FeatureExtractor.decodeWav` (hand-written PCM16 WAV parser) → `FeatureExtractor.extract` → `CryClassifier.predict` → result saved via `HistoryStore` (JSON file in app documents dir, no database).

- `app/lib/services/` — all logic: `feature_extractor.dart`, `classifier.dart`, `history_store.dart`, and `cry_info.dart` (static Italian descriptions/tips per class, keyed by class name; `CryInfo.byKey` falls back to an "unknown" entry).
- `app/lib/screens/` — four screens behind a `NavigationBar` in `main.dart`: home (record/analyze state machine), history, soothing sounds (`audioplayers` + `assets/sounds/`), info.
- No state management framework — plain `StatefulWidget` + `setState`.

## Build & CI quirks

- **Android platform files are not committed.** CI runs `flutter create --org com.lucio --project-name baby_cry_translator --platforms android .` in `app/`, then patches `AndroidManifest.xml` via `sed` (adds `RECORD_AUDIO` permission, sets the app label). To build or run locally you must do the same first. Any new Android-level requirement (permissions, min SDK, etc.) must be added to the workflow's sed/patch step, not to files under `app/android/`.
- The workflow lives in `github/workflows/` (no leading dot) — as-is it will **not** trigger on this repo. The intended flow (see `COME_FARE_APK.md`) is that the user copies the project into their own repo with the directory named `.github/`. Keep this in mind if editing CI: renaming to `.github/` here would activate it.
- In CI, `flutter test` runs with `continue-on-error: true` — a red test does not fail the APK build, so don't rely on CI to catch parity regressions; run tests locally.
- The APK is unsigned-release, distributed as a workflow artifact (`baby-cry-translator-apk`); there is no store deployment or signing config.
