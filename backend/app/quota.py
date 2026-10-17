"""Quote, limitazione di frequenza e interruttore automatico di spesa."""

from __future__ import annotations

from dataclasses import dataclass

from .config import Settings
from .storage import UsageStore, utc_day


class QuotaError(Exception):
    """Richiesta rifiutata dalle politiche di utilizzo."""

    def __init__(self, status_code: int, message: str) -> None:
        super().__init__(message)
        self.status_code = status_code
        self.message = message


@dataclass(frozen=True)
class Decision:
    """Esito del controllo preventivo."""

    allowed: bool
    reason: str = ""


KILL_SWITCH = "cloud_disabled"


def estimate_tokens(text: str) -> int:
    """Stima grossolana ma prudente: ~3,5 caratteri per token in italiano."""
    return max(1, int(len(text) / 3.5))


def estimate_cost(
    settings: Settings, in_tokens: int, out_tokens: int
) -> float:
    return (
        in_tokens * settings.price_input_per_mtok
        + out_tokens * settings.price_output_per_mtok
    ) / 1_000_000


def estimate_audio_cost(
    settings: Settings, seconds: float, out_tokens: int
) -> float:
    """Costo di una trascrizione: l'audio è contato a token al secondo."""
    in_tokens = int(seconds * settings.audio_tokens_per_second)
    return (
        in_tokens * settings.price_audio_input_per_mtok
        + out_tokens * settings.price_output_per_mtok
    ) / 1_000_000


class QuotaGuard:
    """Decide se una richiesta può essere servita e registra il consumo."""

    def __init__(self, settings: Settings, store: UsageStore) -> None:
        self.settings = settings
        self.store = store

    # -- controlli -------------------------------------------------------

    def service_enabled(self) -> bool:
        if self.settings.disabled:
            return False
        if self.store.flag(KILL_SWITCH) == "1":
            return False
        return self.store.spend_today() < self.settings.daily_budget_usd

    def check(self, device_id: str, text: str, now: float | None = None) -> None:
        """Solleva [QuotaError] se la richiesta non può essere servita."""
        s = self.settings

        if not device_id or len(device_id) < 8:
            raise QuotaError(400, "Identificativo del dispositivo mancante o non valido.")

        if len(text) > s.max_input_chars:
            raise QuotaError(
                413,
                f"Testo troppo lungo ({len(text)} caratteri): il limite è "
                f"{s.max_input_chars}.",
            )

        # L'interruttore, una volta inserito, resta fino a riattivazione
        # manuale o al cambio di giornata (la spesa riparte da zero).
        self._check_service_and_budget()

        hits = self.store.recent_hits(device_id, s.rate_limit_window_seconds, now)
        if hits >= s.rate_limit_requests:
            raise QuotaError(429, "Troppe richieste ravvicinate. Riprova fra poco.")

        requests, chars = self.store.device_usage(device_id)
        if requests >= s.daily_requests_per_device:
            raise QuotaError(
                429,
                "Hai raggiunto il limite giornaliero di riassunti cloud.",
            )
        if chars + len(text) > s.daily_chars_per_device:
            raise QuotaError(
                429,
                "Hai raggiunto il limite giornaliero di testo elaborabile.",
            )

        self.store.record_hit(device_id, now)

    def check_audio(
        self, device_id: str, size_bytes: int, seconds: float, now: float | None = None
    ) -> None:
        """Come [check], ma per un blocco audio.

        I controlli comuni (servizio attivo, budget, frequenza, richieste
        giornaliere) sono gli stessi; cambiano i limiti di dimensione, che
        qui si misurano in byte e in secondi di audio.
        """
        s = self.settings

        if not device_id or len(device_id) < 8:
            raise QuotaError(400, "Identificativo del dispositivo mancante o non valido.")

        if size_bytes > s.max_audio_bytes:
            raise QuotaError(
                413,
                f"Blocco audio troppo grande ({size_bytes} byte): il limite "
                f"è {s.max_audio_bytes}.",
            )

        self._check_service_and_budget()

        hits = self.store.recent_hits(device_id, s.rate_limit_window_seconds, now)
        if hits >= s.rate_limit_requests:
            raise QuotaError(429, "Troppe richieste ravvicinate. Riprova fra poco.")

        requests, _ = self.store.device_usage(device_id)
        if requests >= s.daily_requests_per_device:
            raise QuotaError(
                429, "Hai raggiunto il limite giornaliero di richieste cloud."
            )

        used = self.store.device_audio_seconds(device_id)
        if used + seconds > s.daily_audio_seconds_per_device:
            raise QuotaError(
                429,
                "Hai raggiunto il limite giornaliero di audio trascrivibile "
                "nel cloud.",
            )

        self.store.record_hit(device_id, now)

    def _check_service_and_budget(self) -> None:
        s = self.settings
        if s.disabled:
            raise QuotaError(503, "Servizio temporaneamente disattivato.")
        if self.store.flag(KILL_SWITCH) == "1":
            raise QuotaError(
                503, "Servizio cloud sospeso: budget giornaliero esaurito."
            )
        if self.store.spend_today() >= s.daily_budget_usd:
            self.store.set_flag(KILL_SWITCH, "1")
            raise QuotaError(
                503, "Servizio cloud sospeso: budget giornaliero esaurito."
            )

    # -- consuntivo ------------------------------------------------------

    def record(
        self, device_id: str, chars: int, in_tokens: int, out_tokens: int
    ) -> float:
        cost = estimate_cost(self.settings, in_tokens, out_tokens)
        self.store.record_request(device_id, chars, cost, in_tokens, out_tokens)
        if self.store.spend_today() >= self.settings.daily_budget_usd:
            self.store.set_flag(KILL_SWITCH, "1")
        return cost

    def record_audio(
        self, device_id: str, seconds: float, out_tokens: int
    ) -> float:
        in_tokens = int(seconds * self.settings.audio_tokens_per_second)
        cost = estimate_audio_cost(self.settings, seconds, out_tokens)
        self.store.record_request(
            device_id,
            chars=0,
            cost_usd=cost,
            in_tokens=in_tokens,
            out_tokens=out_tokens,
            audio_seconds=seconds,
        )
        if self.store.spend_today() >= self.settings.daily_budget_usd:
            self.store.set_flag(KILL_SWITCH, "1")
        return cost

    def reset_kill_switch(self) -> None:
        self.store.set_flag(KILL_SWITCH, "0")

    def today(self) -> str:
        return utc_day()
