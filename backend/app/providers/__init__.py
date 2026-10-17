from __future__ import annotations

from ..config import Settings
from .anthropic import AnthropicProvider
from .base import Completion, Provider, ProviderError, Transcriber, Transcription
from .echo import EchoProvider, EchoTranscriber
from .gemini import GeminiTranscriber
from .openai import OpenAIProvider

__all__ = [
    "AnthropicProvider",
    "Completion",
    "EchoProvider",
    "EchoTranscriber",
    "GeminiTranscriber",
    "OpenAIProvider",
    "Provider",
    "ProviderError",
    "Transcriber",
    "Transcription",
    "build_provider",
    "build_transcriber",
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


def build_transcriber(settings: Settings) -> Transcriber:
    """Istanzia il trascrittore indicato dalla configurazione."""
    if settings.transcription_provider == "gemini":
        return GeminiTranscriber(
            api_key=settings.gemini_api_key,
            model=settings.gemini_model,
            base_url=settings.gemini_base_url,
            timeout=settings.transcription_timeout_seconds,
        )
    return EchoTranscriber()
