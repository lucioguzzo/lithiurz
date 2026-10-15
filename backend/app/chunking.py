"""Suddivisione del testo per la sintesi gerarchica.

Funzioni pure, senza dipendenze: sono il cuore verificato dai test.
"""

from __future__ import annotations

import re

_SENTENCE_END = re.compile(r"(?<=[.!?…])\s+")


def split_sentences(text: str) -> list[str]:
    cleaned = re.sub(r"\s+", " ", text).strip()
    if not cleaned:
        return []
    return [s for s in _SENTENCE_END.split(cleaned) if s]


def chunk_text(text: str, max_chars: int, overlap_chars: int = 0) -> list[str]:
    """Divide il testo in blocchi di al più ``max_chars`` caratteri.

    Il taglio avviene a fine frase quando possibile; una frase più lunga del
    blocco viene spezzata a forza, così nessun contenuto viene perso in
    silenzio. ``overlap_chars`` ripete la coda del blocco precedente per non
    perdere il filo del discorso.
    """
    if max_chars <= 0:
        raise ValueError("max_chars deve essere positivo")
    cleaned = re.sub(r"\s+", " ", text).strip()
    if not cleaned:
        return []
    if len(cleaned) <= max_chars:
        return [cleaned]

    overlap = max(0, min(overlap_chars, max_chars // 2))
    chunks: list[str] = []
    current: list[str] = []
    current_len = 0

    def flush() -> None:
        nonlocal current, current_len
        if current:
            chunks.append(" ".join(current).strip())
            current = []
            current_len = 0

    for sentence in split_sentences(cleaned):
        # Frase mostruosa: la si spezza in pezzi di max_chars.
        while len(sentence) > max_chars:
            flush()
            chunks.append(sentence[:max_chars].strip())
            sentence = sentence[max_chars:].lstrip()

        if current_len + len(sentence) + 1 > max_chars:
            tail = " ".join(current)[-overlap:] if overlap and current else ""
            flush()
            if tail:
                current.append(tail)
                current_len = len(tail)

        current.append(sentence)
        current_len += len(sentence) + 1

    flush()
    return [c for c in chunks if c]


def needs_map_reduce(text: str, threshold_chars: int) -> bool:
    return len(text) > threshold_chars
