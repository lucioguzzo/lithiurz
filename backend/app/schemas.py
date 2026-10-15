"""Modelli di richiesta e risposta dell'API."""

from __future__ import annotations

from typing import Literal

from pydantic import BaseModel, Field

Style = Literal["breve", "standard", "dettagliato"]


class SummarizeRequest(BaseModel):
    # Solo testo: l'audio non arriva mai al server.
    text: str = Field(min_length=1)
    style: Style = "standard"
    language: str = "it"


class ActionItem(BaseModel):
    text: str
    owner: str | None = None
    due: str | None = None


class SummarizeResponse(BaseModel):
    summary: str
    key_points: list[str] = Field(default_factory=list)
    actions: list[ActionItem] = Field(default_factory=list)
    provider: str
    model: str
    chunks: int = 1
    estimated_cost_usd: float = 0.0


class StatusResponse(BaseModel):
    service_enabled: bool
    provider: str
    model: str
    daily_budget_usd: float
    spent_today_usd: float
    device_requests_today: int
    device_requests_limit: int
    rate_limit_per_window: int
    rate_limit_window_seconds: int
