"""Fornitore fittizio, deterministico: sviluppo in locale e test.

Non effettua chiamate di rete e produce un JSON valido ricavato dal testo
ricevuto, così l'intera pipeline è verificabile senza chiavi API.
"""

from __future__ import annotations

import json
import re

from .base import Completion, Provider

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
