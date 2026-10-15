"""Fornitore Anthropic (Messages API)."""

from __future__ import annotations

import httpx

from .base import Completion, Provider, ProviderError


class AnthropicProvider(Provider):
    name = "anthropic"

    def __init__(
        self,
        api_key: str,
        model: str,
        base_url: str = "https://api.anthropic.com",
        timeout: float = 90.0,
    ) -> None:
        if not api_key:
            raise ProviderError("Chiave API Anthropic non configurata.", 500)
        self.model = model
        self._client = httpx.AsyncClient(
            base_url=base_url.rstrip("/"),
            timeout=timeout,
            headers={
                "x-api-key": api_key,
                "anthropic-version": "2023-06-01",
                "content-type": "application/json",
            },
        )

    async def complete(
        self, system: str, prompt: str, max_output_tokens: int = 1500
    ) -> Completion:
        try:
            response = await self._client.post(
                "/v1/messages",
                json={
                    "model": self.model,
                    "max_tokens": max_output_tokens,
                    "system": system,
                    "messages": [{"role": "user", "content": prompt}],
                },
            )
        except httpx.HTTPError as exc:  # rete irraggiungibile, timeout…
            raise ProviderError(f"Fornitore non raggiungibile: {exc}") from exc

        if response.status_code == 429:
            raise ProviderError("Fornitore sovraccarico, riprovare.", 503)
        if response.status_code >= 400:
            # Il corpo può contenere il testo inviato: non va nei log.
            raise ProviderError(
                f"Fornitore ha risposto {response.status_code}.",
                502,
            )

        data = response.json()
        parts = [
            block.get("text", "")
            for block in data.get("content", [])
            if block.get("type") == "text"
        ]
        usage = data.get("usage", {})
        return Completion(
            text="".join(parts),
            input_tokens=int(usage.get("input_tokens", 0)),
            output_tokens=int(usage.get("output_tokens", 0)),
        )

    async def aclose(self) -> None:
        await self._client.aclose()
