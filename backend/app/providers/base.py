"""Interfaccia comune ai fornitori di modelli linguistici."""

from __future__ import annotations

import abc
from dataclasses import dataclass


@dataclass(frozen=True)
class Completion:
    text: str
    input_tokens: int
    output_tokens: int


class ProviderError(Exception):
    """Errore nel dialogo con il fornitore IA."""

    def __init__(self, message: str, status_code: int = 502) -> None:
        super().__init__(message)
        self.message = message
        self.status_code = status_code


class Provider(abc.ABC):
    name: str = "base"
    model: str = ""

    @abc.abstractmethod
    async def complete(
        self, system: str, prompt: str, max_output_tokens: int = 1500
    ) -> Completion:
        """Esegue una richiesta e restituisce testo e token consumati."""

    async def aclose(self) -> None:  # pragma: no cover - default inerte
        return None
