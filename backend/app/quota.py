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

        if s.disabled:
            raise QuotaError(503, "Servizio temporaneamente disattivato.")

        if self.store.flag(KILL_SWITCH) == "1":
            raise QuotaError(
                503,
                "Servizio cloud sospeso: budget giornaliero esaurito.",
            )

        spent = self.store.spend_today()
        if spent >= s.daily_budget_usd:
            # L'interruttore resta inserito fino a riattivazione manuale o
            # al cambio di giornata (la spesa riparte da zero).
            self.store.set_flag(KILL_SWITCH, "1")
            raise QuotaError(
                503,
                "Servizio cloud sospeso: budget giornaliero esaurito.",
            )

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

    # -- consuntivo ------------------------------------------------------

    def record(
        self, device_id: str, chars: int, in_tokens: int, out_tokens: int
    ) -> float:
        cost = estimate_cost(self.settings, in_tokens, out_tokens)
        self.store.record_request(device_id, chars, cost, in_tokens, out_tokens)
        if self.store.spend_today() >= self.settings.daily_budget_usd:
            self.store.set_flag(KILL_SWITCH, "1")
        return cost

    def reset_kill_switch(self) -> None:
        self.store.set_flag(KILL_SWITCH, "0")

    def today(self) -> str:
        return utc_day()
