#!/usr/bin/env python3
"""Misura gli errori di un motore di trascrizione sull'italiano parlato.

Serve a rispondere con un numero, e non a sensazione, alla domanda che
torna ogni volta che esce un modello nuovo: *è meglio di quello che
stiamo usando?*

Il materiale è FLEURS italiano (Google), voce umana reale con il testo di
riferimento pubblicato, scaricato in streaming da Hugging Face: nessun
dataset da tenere sul disco. Il confronto è fra testi normalizzati —
minuscole, senza punteggiatura — perché un riassunto non si accorge di
una virgola, ma si accorge di una parola sbagliata.

    pip install datasets soundfile jiwer numpy
    pip install cactus-needle          # per Whistle
    pip install pywhispercpp           # per whisper.cpp

    python3 wer_italiano.py --whistle /percorso/whistle.cact
    python3 wer_italiano.py --whisper /percorso/ggml-small-q8_0.bin

Attenzione a come si leggono i risultati: FLEURS è parlato letto, in
condizioni pulite. Un messaggio vocale di WhatsApp — compresso, con
rumore di fondo, con la bocca lontana dal microfono — è più difficile, e
tutti i modelli peggiorano. Serve a confrontare i motori fra loro, non a
promettere una percentuale all'utente.
"""
from __future__ import annotations

import argparse
import io
import re
import time
import unicodedata
from pathlib import Path

CAMPIONI = 12
FREQUENZA = 16_000


def normalizza(testo: str) -> str:
    """Minuscole, via la punteggiatura, spazi compattati."""
    testo = unicodedata.normalize("NFC", testo.lower())
    testo = re.sub(r"[^\w\sàèéìòùç']", " ", testo)
    return re.sub(r"\s+", " ", testo).strip()


def prepara(cartella: Path, quanti: int) -> list[tuple[Path, float, str]]:
    """Scarica i campioni se non ci sono già e restituisce i riferimenti."""
    import numpy as np
    import soundfile as sf

    indice = cartella / "riferimenti.tsv"
    if indice.exists():
        righe = []
        for riga in indice.read_text().splitlines():
            path, durata, testo = riga.split("\t", 2)
            righe.append((Path(path), float(durata), testo))
        if len(righe) >= quanti:
            return righe[:quanti]

    from datasets import Audio, load_dataset

    cartella.mkdir(parents=True, exist_ok=True)
    ds = load_dataset("google/fleurs", "it_it", split="test", streaming=True)
    ds = ds.cast_column("audio", Audio(decode=False))

    righe = []
    for i, esempio in enumerate(ds):
        if i >= quanti:
            break
        dati, sr = sf.read(io.BytesIO(esempio["audio"]["bytes"]), dtype="float32")
        if sr != FREQUENZA:
            raise SystemExit(f"campione a {sr} Hz: atteso {FREQUENZA}")
        # Scrivere silenzio per errore è già capitato: meglio accorgersene
        # qui che misurare un WER del 100% e crederci.
        if float(np.sqrt((dati ** 2).mean())) < 0.001:
            raise SystemExit(f"campione {i} silenzioso")
        path = cartella / f"fleurs_{i:02d}.wav"
        sf.write(path, dati, sr, subtype="PCM_16")
        righe.append((path, len(dati) / sr, esempio["raw_transcription"]))

    indice.write_text(
        "".join(f"{p}\t{d:.1f}\t{t}\n" for p, d, t in righe)
    )
    return righe


def trascrittore_whistle(pesi: str):
    import needle

    def trascrivi(path: Path) -> str:
        return needle.transcribe(str(path), weights=pesi, language="it")["text"]

    return trascrivi


def trascrittore_whisper(modello: str):
    from pywhispercpp.model import Model

    motore = Model(modello, language="it", print_progress=False,
                   print_realtime=False)

    def trascrivi(path: Path) -> str:
        return " ".join(s.text for s in motore.transcribe(str(path))).strip()

    return trascrivi


def main() -> None:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--whistle", help="percorso di whistle.cact")
    ap.add_argument("--whisper", help="percorso di un modello ggml")
    ap.add_argument("--campioni", type=int, default=CAMPIONI)
    ap.add_argument("--dati", default="campioni_fleurs",
                    help="dove tenere i campioni scaricati")
    ap.add_argument("--mostra", action="store_true",
                    help="stampa atteso e ottenuto per ogni campione")
    args = ap.parse_args()

    if bool(args.whistle) == bool(args.whisper):
        raise SystemExit("indicare --whistle OPPURE --whisper")

    import jiwer

    righe = prepara(Path(args.dati), args.campioni)
    nome = "Whistle" if args.whistle else Path(args.whisper).name
    trascrivi = (trascrittore_whistle(args.whistle) if args.whistle
                 else trascrittore_whisper(args.whisper))

    riferimenti, ottenuti = [], []
    durata_totale = tempo_totale = 0.0
    for path, durata, riferimento in righe:
        inizio = time.perf_counter()
        testo = trascrivi(path)
        trascorso = time.perf_counter() - inizio
        durata_totale += durata
        tempo_totale += trascorso
        riferimenti.append(normalizza(riferimento))
        ottenuti.append(normalizza(testo))
        if args.mostra:
            print(f"[{path.name}] {durata:.1f}s in {trascorso:.2f}s")
            print(f"  atteso:   {riferimento}")
            print(f"  ottenuto: {testo or '(vuoto)'}")

    wer = jiwer.wer(riferimenti, ottenuti) * 100
    print(f"\n{nome}: WER {wer:.1f}% su {len(righe)} campioni "
          f"({durata_totale:.0f}s di audio)")
    print(f"velocità: {durata_totale / tempo_totale:.1f}× il tempo reale "
          f"su questa macchina (non è un telefono)")


if __name__ == "__main__":
    main()
