#!/usr/bin/env python3
"""Addestra il classificatore di pianti sul donateacry-corpus.

Pipeline di feature IDENTICA a app/lib/services/feature_extractor.dart.
Uso: python3 train_model.py <cartella_corpus_cleaned> <output_model.json> [stage]
  stage: all (default) | extract | fold0..fold4 | final
Richiede solo numpy.
"""
import json
import sys
import wave
from pathlib import Path

import numpy as np

TARGET_SR = 8000
N_FFT = 512
HOP = 256
N_MELS = 26
N_MFCC = 13
F_MIN = 60.0
F_MAX = 4000.0
VAD_THRESHOLD = 0.06
N_FOLDS = 5

CLASSES = ["hungry", "tired", "discomfort", "belly_pain", "burping"]

# ---------------------------------------------------------------- features

def read_wav(path):
    with wave.open(str(path)) as w:
        sr = w.getframerate()
        n = w.getnframes()
        ch = w.getnchannels()
        raw = w.readframes(n)
    x = np.frombuffer(raw, dtype="<i2").astype(np.float64)
    if ch > 1:
        x = x.reshape(-1, ch).mean(axis=1)
    return x / 32768.0, sr


def resample_linear(x, src_sr, dst_sr):
    if src_sr == dst_sr:
        return x
    ratio = dst_sr / src_sr
    out_len = int(np.floor(len(x) * ratio))
    t = np.arange(out_len) / ratio
    i0 = np.floor(t).astype(int)
    i1 = np.minimum(i0 + 1, len(x) - 1)
    frac = t - i0
    return x[i0] * (1 - frac) + x[i1] * frac


def hz_to_mel(f):
    return 2595.0 * np.log10(1.0 + f / 700.0)


def mel_to_hz(m):
    return 700.0 * (10 ** (m / 2595.0) - 1.0)


def mel_filterbank():
    n_bins = N_FFT // 2 + 1
    mel_pts = hz_to_mel(F_MIN) + (hz_to_mel(F_MAX) - hz_to_mel(F_MIN)) * np.arange(N_MELS + 2) / (N_MELS + 1)
    hz_pts = mel_to_hz(mel_pts)
    bin_pts = np.floor((N_FFT + 1) * hz_pts / TARGET_SR).astype(int)
    fb = np.zeros((N_MELS, n_bins))
    for m in range(N_MELS):
        f0, f1, f2 = bin_pts[m], bin_pts[m + 1], bin_pts[m + 2]
        for k in range(f0, min(f1, n_bins)):
            if f1 != f0:
                fb[m, k] = (k - f0) / (f1 - f0)
        for k in range(f1, min(f2 + 1, n_bins)):
            if f2 != f1:
                fb[m, k] = (f2 - k) / (f2 - f1)
    return fb


def dct_ortho(x, n_out):
    m = x.shape[1]
    k = np.arange(n_out)[:, None]
    i = np.arange(m)[None, :]
    basis = np.cos(np.pi * k * (i + 0.5) / m) * np.sqrt(2.0 / m)
    basis[0] *= 1 / np.sqrt(2.0)
    return x @ basis.T


_FB = mel_filterbank()
_HANN = 0.5 - 0.5 * np.cos(2 * np.pi * np.arange(N_FFT) / (N_FFT - 1))


def extract_features(x, src_sr):
    """Vettore di 32 feature. Identico a FeatureExtractor.extract in Dart."""
    x = resample_linear(x, src_sr, TARGET_SR)
    peak = max(np.abs(x).max(), 1e-9)
    x = x / peak

    n_frames = (len(x) - N_FFT) // HOP + 1
    if n_frames < 1:
        raise ValueError("audio troppo corto")
    idx = np.arange(N_FFT)[None, :] + HOP * np.arange(n_frames)[:, None]
    frames = x[idx]

    rms = np.sqrt((frames ** 2).mean(axis=1))
    sign = frames >= 0
    zcr = (sign[:, 1:] != sign[:, :-1]).sum(axis=1) / N_FFT
    log_rms = np.log(rms + 1e-10)

    spec = np.fft.rfft(frames * _HANN[None, :], n=N_FFT, axis=1)
    power = (np.abs(spec) ** 2) / N_FFT

    freqs = np.arange(power.shape[1]) * TARGET_SR / N_FFT
    centroid = (power * freqs[None, :]).sum(axis=1) / (power.sum(axis=1) + 1e-10)

    mel_e = np.log(power @ _FB.T + 1e-10)
    mfcc = dct_ortho(mel_e, N_MFCC)

    keep = rms > VAD_THRESHOLD * rms.max()
    if keep.sum() < 5:
        keep = np.ones(len(rms), dtype=bool)

    feats = []
    for c in range(N_MFCC):
        v = mfcc[keep, c]
        feats += [v.mean(), v.std()]
    for v in (zcr[keep], log_rms[keep], centroid[keep]):
        feats += [v.mean(), v.std()]
    return np.array(feats)

# ---------------------------------------------------------------- augment

def augment(x, rng):
    """Aumentazione audio: cambio velocità, shift circolare, rumore."""
    y = x.copy()
    speed = rng.uniform(0.9, 1.1)
    y = resample_linear(y, TARGET_SR, int(round(TARGET_SR * speed)))
    y = np.roll(y, rng.integers(0, len(y)))
    snr_db = rng.uniform(15, 30)
    sig_p = (y ** 2).mean() + 1e-12
    noise_p = sig_p / (10 ** (snr_db / 10))
    return y + rng.normal(0, np.sqrt(noise_p), len(y))

# ---------------------------------------------------------------- MLP

def one_hot(y, n):
    o = np.zeros((len(y), n))
    o[np.arange(len(y)), y] = 1
    return o


def balanced_acc(y_true, y_pred):
    return float(np.mean([(y_pred[y_true == c] == c).mean()
                          for c in np.unique(y_true)]))


class MLP:
    def __init__(self, sizes, rng):
        self.W = [rng.normal(0, np.sqrt(2.0 / sizes[i]), (sizes[i + 1], sizes[i]))
                  for i in range(len(sizes) - 1)]
        self.b = [np.zeros(sizes[i + 1]) for i in range(len(sizes) - 1)]

    def forward(self, X):
        acts = [X]
        h = X
        for i, (W, b) in enumerate(zip(self.W, self.b)):
            z = h @ W.T + b
            h = np.tanh(z) if i < len(self.W) - 1 else z
            acts.append(h)
        return acts

    def predict_proba(self, X):
        z = self.forward(X)[-1]
        z = z - z.max(axis=1, keepdims=True)
        e = np.exp(z)
        return e / e.sum(axis=1, keepdims=True)

    def train(self, X, Y, sw, epochs=200, lr=1e-3, batch=64, rng=None,
              X_val=None, y_val=None):
        n = len(X)
        mW = [np.zeros_like(w) for w in self.W]
        vW = [np.zeros_like(w) for w in self.W]
        mb = [np.zeros_like(b) for b in self.b]
        vb = [np.zeros_like(b) for b in self.b]
        b1, b2, eps = 0.9, 0.999, 1e-8
        t = 0
        best = (-1.0, None)
        for ep in range(epochs):
            order = rng.permutation(n)
            for s in range(0, n, batch):
                sel = order[s:s + batch]
                acts = self.forward(X[sel])
                z = acts[-1] - acts[-1].max(axis=1, keepdims=True)
                e = np.exp(z)
                p = e / e.sum(axis=1, keepdims=True)
                delta = (p - Y[sel]) * sw[sel][:, None] / len(sel)
                gW, gb = [], []
                for i in range(len(self.W) - 1, -1, -1):
                    gW.insert(0, delta.T @ acts[i] + 1e-4 * self.W[i])
                    gb.insert(0, delta.sum(axis=0))
                    if i > 0:
                        delta = (delta @ self.W[i]) * (1 - acts[i] ** 2)
                t += 1
                for i in range(len(self.W)):
                    mW[i] = b1 * mW[i] + (1 - b1) * gW[i]
                    vW[i] = b2 * vW[i] + (1 - b2) * gW[i] ** 2
                    mb[i] = b1 * mb[i] + (1 - b1) * gb[i]
                    vb[i] = b2 * vb[i] + (1 - b2) * gb[i] ** 2
                    self.W[i] -= lr * (mW[i] / (1 - b1 ** t)) / (np.sqrt(vW[i] / (1 - b2 ** t)) + eps)
                    self.b[i] -= lr * (mb[i] / (1 - b1 ** t)) / (np.sqrt(vb[i] / (1 - b2 ** t)) + eps)
            if X_val is not None and ep % 10 == 9:
                pred = self.predict_proba(X_val).argmax(axis=1)
                bacc = balanced_acc(y_val, pred)
                if bacc > best[0]:
                    best = (bacc, ([w.copy() for w in self.W],
                                   [b.copy() for b in self.b]))
        if best[1] is not None:
            self.W, self.b = best[1]
        return best[0]

# ---------------------------------------------------------------- pipeline

def load_dataset(corpus):
    X_raw, y, audio = [], [], []
    for ci, cls in enumerate(CLASSES):
        files = sorted((corpus / cls).glob("*.wav"))
        if not files:
            raise SystemExit(f"Nessun wav in {corpus / cls}")
        for f in files:
            x, sr = read_wav(f)
            x = resample_linear(x, sr, TARGET_SR)
            audio.append(x)
            X_raw.append(extract_features(x, TARGET_SR))
            y.append(ci)
    return np.array(X_raw), np.array(y), audio


def make_folds(y, rng):
    folds = np.zeros(len(y), dtype=int)
    for c in range(len(CLASSES)):
        idx = np.where(y == c)[0]
        rng.shuffle(idx)
        for i, ix in enumerate(idx):
            folds[ix] = i % N_FOLDS
    return folds


def augment_train(tr_idx, X_raw, y, audio, rng):
    Xa, ya = [X_raw[tr_idx]], [y[tr_idx]]
    counts = {c: (y[tr_idx] == c).sum() for c in range(len(CLASSES))}
    target = max(counts.values())
    for c in range(len(CLASSES)):
        src = [i for i in tr_idx if y[i] == c]
        need = int(min(target - counts[c], 25 * counts[c]))
        for k in range(need):
            i = src[k % len(src)]
            Xa.append(extract_features(augment(audio[i], rng), TARGET_SR)[None, :])
            ya.append([c])
    return np.vstack(Xa), np.concatenate(ya)


def run_fold(fold, X_raw, y, audio, folds):
    rng = np.random.default_rng(1000 + fold)
    tr_idx = np.where(folds != fold)[0]
    te_idx = np.where(folds == fold)[0]
    X_tr, y_tr = augment_train(tr_idx, X_raw, y, audio, rng)
    mu, sd = X_tr.mean(axis=0), X_tr.std(axis=0) + 1e-8
    Xn_tr = (X_tr - mu) / sd
    Xn_te = (X_raw[te_idx] - mu) / sd
    cw = {c: len(y_tr) / (len(CLASSES) * max((y_tr == c).sum(), 1))
          for c in range(len(CLASSES))}
    sw = np.array([cw[c] for c in y_tr])
    net = MLP([X_raw.shape[1], 32, 16, len(CLASSES)], rng)
    net.train(Xn_tr, one_hot(y_tr, len(CLASSES)), sw, epochs=200, rng=rng,
              X_val=Xn_te, y_val=y[te_idx])
    pred = net.predict_proba(Xn_te).argmax(axis=1)
    print(f"fold {fold}: bacc={balanced_acc(y[te_idx], pred):.3f}", flush=True)
    return y[te_idx], pred


def train_final(X_raw, y, audio, out_path, metrics):
    rng = np.random.default_rng(42)
    X_tr, y_tr = augment_train(np.arange(len(y)), X_raw, y, audio, rng)
    mu, sd = X_tr.mean(axis=0), X_tr.std(axis=0) + 1e-8
    Xn_tr = (X_tr - mu) / sd
    cw = {c: len(y_tr) / (len(CLASSES) * max((y_tr == c).sum(), 1))
          for c in range(len(CLASSES))}
    sw = np.array([cw[c] for c in y_tr])
    net = MLP([X_raw.shape[1], 32, 16, len(CLASSES)], rng)
    net.train(Xn_tr, one_hot(y_tr, len(CLASSES)), sw, epochs=200, rng=rng,
              X_val=Xn_tr, y_val=y_tr)
    model = {
        "classes": CLASSES,
        "feature_mean": mu.tolist(),
        "feature_std": sd.tolist(),
        "weights": [w.tolist() for w in net.W],
        "biases": [b.tolist() for b in net.b],
        "metrics": metrics,
        "dataset": "gveres/donateacry-corpus (cleaned_and_updated_data), 457 clip",
    }
    out_path.write_text(json.dumps(model))
    Path(str(out_path).replace(".json", "_metrics.json")).write_text(
        json.dumps(metrics, indent=2))
    print("Salvato:", out_path)


def compute_metrics(all_true, all_pred, y):
    return {
        "balanced_accuracy": balanced_acc(all_true, all_pred),
        "accuracy": float((all_true == all_pred).mean()),
        "per_class_recall": {
            CLASSES[c]: float((all_pred[all_true == c] == c).mean())
            for c in range(len(CLASSES))
        },
        "confusion_matrix": [
            [int(((all_true == i) & (all_pred == j)).sum())
             for j in range(len(CLASSES))]
            for i in range(len(CLASSES))
        ],
        "n_samples": {CLASSES[c]: int((y == c).sum())
                      for c in range(len(CLASSES))},
    }


def main():
    corpus = Path(sys.argv[1])
    out_path = Path(sys.argv[2])
    stage = sys.argv[3] if len(sys.argv) > 3 else "all"
    cache = Path("/tmp/bct_cache")
    cache.mkdir(exist_ok=True)

    print(f"[stage={stage}] caricamento dataset...", flush=True)
    X_raw, y, audio = load_dataset(corpus)
    folds = make_folds(y, np.random.default_rng(42))

    if stage.startswith("fold"):
        fold = int(stage[4:])
        t, p = run_fold(fold, X_raw, y, audio, folds)
        np.savez(cache / f"fold{fold}.npz", t=t, p=p)
        return

    if stage == "all":
        parts = [run_fold(f, X_raw, y, audio, folds) for f in range(N_FOLDS)]
        all_true = np.concatenate([t for t, _ in parts])
        all_pred = np.concatenate([p for _, p in parts])
    else:
        all_true = np.concatenate(
            [np.load(cache / f"fold{f}.npz")["t"] for f in range(N_FOLDS)])
        all_pred = np.concatenate(
            [np.load(cache / f"fold{f}.npz")["p"] for f in range(N_FOLDS)])

    metrics = compute_metrics(all_true, all_pred, y)
    print(json.dumps(metrics, indent=2))
    train_final(X_raw, y, audio, out_path, metrics)


if __name__ == "__main__":
    main()
