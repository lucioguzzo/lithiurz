"""Fornitore compatibile OpenAI (Chat Completions).

Funziona anche con gateway compatibili cambiando ``base_url``.
"""

from __future__ import annotations

import httpx

from .base import Completion, Provider, ProviderError


class OpenAIProvider(Provider):
    name = "openai"

    def __init__(
        self,
        api_key: str,
        model: str,
        base_url: str = "https://api.openai.com/v1",
        timeout: float = 90.0,
    ) -> None:
        if not api_key:
            raise ProviderError("Chiave API OpenAI non configurata.", 500)
        self.model = model
        self._client = httpx.AsyncClient(
            base_url=base_url.rstrip("/"),
            timeout=timeout,
            headers={
                "authorization": f"Bearer {api_key}",
                "content-type": "application/json",
            },
        )

    async def complete(
        self, system: str, prompt: str, max_output_tokens: int = 1500
    ) -> Completion:
        try:
            response = await self._client.post(
                "/chat/completions",
                json={
                    "model": self.model,
                    "max_tokens": max_output_tokens,
                    "messages": [
                        {"role": "system", "content": system},
                        {"role": "user", "content": prompt},
                    ],
                    "response_format": {"type": "json_object"},
                },
            )
        except httpx.HTTPError as exc:
            raise ProviderError(f"Fornitore non raggiungibile: {exc}") from exc

        if response.status_code == 429:
            raise ProviderError("Fornitore sovraccarico, riprovare.", 503)
        if response.status_code >= 400:
            raise ProviderError(
                f"Fornitore ha risposto {response.status_code}.", 502
            )

        data = response.json()
        choices = data.get("choices") or [{}]
        text = (choices[0].get("message") or {}).get("content") or ""
        usage = data.get("usage", {})
        return Completion(
            text=text,
            input_tokens=int(usage.get("prompt_tokens", 0)),
            output_tokens=int(usage.get("completion_tokens", 0)),
        )

    async def aclose(self) -> None:
        await self._client.aclose()
