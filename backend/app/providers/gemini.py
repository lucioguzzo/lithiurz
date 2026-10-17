"""Trascrizione con l'API Gemini (endpoint `interactions`).

L'audio viene inviato in linea, codificato base64, insieme all'istruzione
di trascrivere. Il modello non è un riconoscitore vocale dedicato: va
istruito a restituire *solo* il parlato, altrimenti commenta, riassume o
si scusa, e quel testo finirebbe nella trascrizione dell'utente.

Nota sui termini di Google: per un'app distribuita in Europa è ammesso
soltanto il piano a pagamento (progetto con fatturazione attiva). Sul
piano gratuito i contenuti possono essere usati per migliorare i prodotti
Google. Il codice è identico nei due casi: cambia la chiave.
"""

from __future__ import annotations

import base64
from typing import Any

import httpx

from .base import ProviderError, Transcriber, Transcription

# Il modello va tenuto stretto: niente preamboli, niente riassunti, niente
# traduzioni. Se non c'è parlato deve rispondere con la riga convenuta.
PROMPT = (
    "Trascrivi fedelmente il parlato di questo audio in {language}.\n"
    "Regole:\n"
    "- Riporta soltanto le parole pronunciate, nella lingua in cui sono "
    "pronunciate. Non tradurre.\n"
    "- Non aggiungere introduzioni, commenti, titoli, marcatori di tempo "
    "o descrizioni dei suoni.\n"
    "- Usa la punteggiatura e le maiuscole corrette.\n"
    "- Non inventare parole se l'audio è incomprensibile: salta il punto.\n"
    "- Se nell'audio non c'è parlato, rispondi esattamente con: "
    "{empty}"
)

EMPTY_MARKER = "[NESSUN PARLATO]"

LANGUAGE_NAMES = {
    "it": "italiano",
    "en": "inglese",
    "es": "spagnolo",
    "fr": "francese",
    "de": "tedesco",
}


def language_name(code: str) -> str:
    return LANGUAGE_NAMES.get(code, "la lingua dell'audio")


def extract_text(data: dict[str, Any]) -> str:
    """Estrae il testo dalla risposta, tollerando le forme dell'API.

    L'API `interactions` espone il testo come `output_text` negli SDK, ma
    la risposta grezza lo porta dentro il passo `model_output`; le versioni
    precedenti usavano `candidates[].content.parts[].text`. Si accettano
    tutte e tre: una risposta valida non va buttata per la forma.
    """
    direct = data.get("output_text")
    if isinstance(direct, str) and direct.strip():
        return direct.strip()

    pieces: list[str] = []
    for step in data.get("steps") or []:
        if not isinstance(step, dict):
            continue
        if step.get("type") not in (None, "model_output"):
            continue
        pieces.extend(_texts_in(step.get("content") or step.get("output")))
    if pieces:
        return "\n".join(pieces).strip()

    for candidate in data.get("candidates") or []:
        if isinstance(candidate, dict):
            pieces.extend(_texts_in((candidate.get("content") or {}).get("parts")))
    return "\n".join(pieces).strip()


def _texts_in(node: Any) -> list[str]:
    """Raccoglie i campi `text` annidati, in qualunque forma arrivino."""
    if isinstance(node, str):
        return [node] if node.strip() else []
    if isinstance(node, dict):
        if isinstance(node.get("text"), str):
            return [node["text"]] if node["text"].strip() else []
        found: list[str] = []
        for value in node.values():
            found.extend(_texts_in(value))
        return found
    if isinstance(node, list):
        found = []
        for item in node:
            found.extend(_texts_in(item))
        return found
    return []


def _usage(data: dict[str, Any]) -> tuple[int, int]:
    usage = data.get("usage") or data.get("usage_metadata") or data.get("usageMetadata")
    if not isinstance(usage, dict):
        return 0, 0
    read = lambda *keys: next(  # noqa: E731 - tabella di alias, non logica
        (int(usage[k]) for k in keys if isinstance(usage.get(k), (int, float))), 0
    )
    return (
        read("input_tokens", "prompt_token_count", "promptTokenCount"),
        read("output_tokens", "candidates_token_count", "candidatesTokenCount"),
    )


class GeminiTranscriber(Transcriber):
    name = "gemini"

    def __init__(
        self,
        api_key: str,
        model: str,
        base_url: str = "https://generativelanguage.googleapis.com/v1beta",
        timeout: float = 180.0,
    ) -> None:
        if not api_key:
            raise ProviderError("Chiave API Gemini non configurata.", 500)
        self.model = model
        self._client = httpx.AsyncClient(
            base_url=base_url.rstrip("/"),
            timeout=timeout,
            headers={
                "x-goog-api-key": api_key,
                "content-type": "application/json",
            },
        )

    async def transcribe(
        self, audio: bytes, mime_type: str, language: str
    ) -> Transcription:
        prompt = PROMPT.format(language=language_name(language), empty=EMPTY_MARKER)
        payload = {
            "model": self.model,
            "input": [
                {"type": "text", "text": prompt},
                {
                    "type": "audio",
                    "data": base64.b64encode(audio).decode("ascii"),
                    "mime_type": mime_type,
                },
            ],
        }

        try:
            response = await self._client.post("/interactions", json=payload)
        except httpx.HTTPError as exc:
            raise ProviderError(f"Fornitore non raggiungibile: {exc}") from exc

        if response.status_code == 429:
            raise ProviderError("Fornitore sovraccarico, riprovare.", 503)
        if response.status_code >= 400:
            raise ProviderError(
                f"Fornitore ha risposto {response.status_code}.", 502
            )

        data = response.json()
        text = extract_text(data)
        if text == EMPTY_MARKER:
            text = ""
        in_tokens, out_tokens = _usage(data)
        return Transcription(
            text=text, input_tokens=in_tokens, output_tokens=out_tokens
        )

    async def aclose(self) -> None:
        await self._client.aclose()
