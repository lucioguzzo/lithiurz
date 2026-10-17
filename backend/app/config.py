"""Configurazione del servizio, interamente da variabili d'ambiente.

Nessuna chiave viene mai spedita all'app: restano qui.
"""

from __future__ import annotations

from functools import lru_cache
from typing import Literal

from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", env_prefix="BRIEF_")

    # --- Provider IA -----------------------------------------------------
    provider: Literal["anthropic", "openai", "echo"] = "echo"
    anthropic_api_key: str = ""
    anthropic_model: str = "claude-haiku-5-5"
    anthropic_base_url: str = "https://api.anthropic.com"
    openai_api_key: str = ""
    openai_model: str = "gpt-4o-mini"
    openai_base_url: str = "https://api.openai.com/v1"
    request_timeout_seconds: float = 90.0

    # --- Trascrizione nel cloud -----------------------------------------
    # `echo` non chiama nessuno: serve allo sviluppo e ai test.
    transcription_provider: Literal["gemini", "echo"] = "echo"
    gemini_api_key: str = ""
    gemini_model: str = "gemini-3.8-flash"
    gemini_base_url: str = "https://generativelanguage.googleapis.com/v1beta"
    # Un blocco da 90 secondi viaggia, si carica e si decodifica: largo.
    transcription_timeout_seconds: float = 180.0

    # Dimensione massima del singolo blocco audio accettato. Gemini ammette
    # 20 MB per richiesta *comprensivi* della codifica base64, che gonfia di
    # un terzo: 12 MB di audio grezzo restano dentro con margine.
    max_audio_bytes: int = 12 * 1024 * 1024

    # Quanto audio può trascrivere un dispositivo in un giorno.
    daily_audio_seconds_per_device: int = 3_600

    # Gemini conta l'audio 32 token al secondo: un'ora sono 115.200 token.
    audio_tokens_per_second: int = 32
    # Prezzo dei token audio in ingresso, per milione. Va allineato al
    # listino del modello scelto.
    price_audio_input_per_mtok: float = 0.75

    # --- Controllo dei costi --------------------------------------------
    # Prezzi indicativi per milione di token: servono solo a stimare la spesa
    # e a far scattare l'interruttore automatico. Vanno allineati al listino
    # del provider scelto.
    price_input_per_mtok: float = 1.0
    price_output_per_mtok: float = 5.0

    # Tetto di spesa giornaliero (USD). Superato, il cloud si disattiva da
    # solo fino al giorno successivo (UTC).
    daily_budget_usd: float = 5.0

    # Quote per dispositivo.
    daily_requests_per_device: int = 20
    daily_chars_per_device: int = 400_000
    rate_limit_requests: int = 6
    rate_limit_window_seconds: int = 60

    # Limite di dimensione della singola richiesta.
    max_input_chars: int = 220_000

    # --- Mappa/riduci ----------------------------------------------------
    # Oltre questa soglia il testo viene riassunto a blocchi e poi sintetizzato.
    map_reduce_threshold_chars: int = 12_000
    chunk_chars: int = 9_000
    chunk_overlap_chars: int = 400

    # --- Accesso ---------------------------------------------------------
    # Token pubblico dell'app (non è un segreto: serve solo a scremare il
    # traffico). Vuoto = nessun controllo.
    app_token: str = ""
    # Chiave per gli endpoint amministrativi (/admin/*). Vuoto = disattivati.
    admin_token: str = ""

    # --- Persistenza -----------------------------------------------------
    database_path: str = "usage.db"

    # Interruttore manuale: True mette il servizio in sola lettura.
    disabled: bool = False


@lru_cache
def get_settings() -> Settings:
    return Settings()
