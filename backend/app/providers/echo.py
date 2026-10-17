"""Fornitore fittizio, deterministico: sviluppo in locale e test.

Non effettua chiamate di rete e produce un JSON valido ricavato dal testo
ricevuto, così l'intera pipeline è verificabile senza chiavi API.
"""

from __future__ import annotations

import json
import re

from .base import Completion, Provider, Transcriber, Transcription

_QUOTED = re.compile(r'"""\s*(.*?)\s*"""', re.DOTALL)


class EchoProvider(Provider):
    name = "echo"
    model = "echo-locale"

    async def complete(
        self, system: str, prompt: str, max_output_tokens: int = 1500
    ) -> Completion:
        match = _QUOTED.search(prompt)
        body = match.group(1) if match else prompt
        sentences = [s.strip() for s in re.split(r"(?<=[.!?])\s+", body) if s.strip()]

        payload = {
            "summary": " ".join(sentences[:3]) or body[:200],
            "key_points": [s[:160] for s in sentences[:4]],
            "actions": [
                {"text": s[:160], "owner": None, "due": None}
                for s in sentences
                if re.search(r"\b(bisogna|dobbiamo|dev[oi]|ricord\w*|entro|chiama\w*)\b", s, re.I)
            ][:5],
        }
        text = json.dumps(payload, ensure_ascii=False)
        return Completion(
            text=text,
            input_tokens=max(1, len(prompt) // 4),
            output_tokens=max(1, len(text) // 4),
        )


class EchoTranscriber(Transcriber):
    """Finto trascrittore: non chiama nessuno.

    Restituisce una frase che descrive l'audio ricevuto, così la catena
    (quote, costi, ricucitura dei blocchi, interfaccia) è verificabile
    senza chiavi e senza spendere.
    """

    name = "echo"
    model = "echo-locale"

    async def transcribe(
        self, audio: bytes, mime_type: str, language: str
    ) -> Transcription:
        text = (
            f"Trascrizione di prova di {len(audio)} byte di audio "
            f"({mime_type}, lingua {language})."
        )
        return Transcription(
            text=text,
            input_tokens=max(1, len(audio) // 1000),
            output_tokens=max(1, len(text) // 4),
        )
