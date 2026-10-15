from __future__ import annotations

from ..config import Settings
from .anthropic import AnthropicProvider
from .base import Completion, Provider, ProviderError
from .echo import EchoProvider
from .openai import OpenAIProvider

__all__ = [
    "AnthropicProvider",
    "Completion",
    "EchoProvider",
    "OpenAIProvider",
    "Provider",
    "ProviderError",
    "build_provider",
]


def build_provider(settings: Settings) -> Provider:
    """Istanzia il fornitore indicato dalla configurazione."""
    if settings.provider == "anthropic":
        return AnthropicProvider(
            api_key=settings.anthropic_api_key,
            model=settings.anthropic_model,
            base_url=settings.anthropic_base_url,
            timeout=settings.request_timeout_seconds,
        )
    if settings.provider == "openai":
        return OpenAIProvider(
            api_key=settings.openai_api_key,
            model=settings.openai_model,
            base_url=settings.openai_base_url,
            timeout=settings.request_timeout_seconds,
        )
    return EchoProvider()
