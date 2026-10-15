"""API dei riassunti di BRIEF.

Riceve solo testo. Protegge la chiave del fornitore IA, applica quote,
limiti di frequenza e un tetto di spesa con disattivazione automatica.
"""

from __future__ import annotations

import logging
from contextlib import asynccontextmanager
from typing import AsyncIterator

from fastapi import Depends, FastAPI, Header, HTTPException, Request
from fastapi.responses import JSONResponse

from .config import Settings, get_settings
from .providers import build_provider
from .providers.base import Provider, ProviderError
from .quota import QuotaError, QuotaGuard, estimate_tokens
from .schemas import StatusResponse, SummarizeRequest, SummarizeResponse
from .storage import UsageStore
from .summarize import summarize_text

# Attenzione: nessun log deve contenere trascrizioni o riassunti.
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger("brief")


@asynccontextmanager
async def lifespan(app: FastAPI) -> AsyncIterator[None]:
    # `settings_override` esiste per i test: in esercizio la configurazione
    # arriva sempre dall'ambiente.
    settings: Settings = getattr(app.state, "settings_override", None) or get_settings()
    store = UsageStore(settings.database_path)
    app.state.settings = settings
    app.state.store = store
    app.state.guard = QuotaGuard(settings, store)
    app.state.provider = build_provider(settings)
    logger.info(
        "Avvio: provider=%s modello=%s budget=%.2f USD/giorno",
        app.state.provider.name,
        app.state.provider.model,
        settings.daily_budget_usd,
    )
    try:
        yield
    finally:
        await app.state.provider.aclose()
        store.close()


app = FastAPI(
    title="BRIEF Summary API",
    version="0.1.0",
    lifespan=lifespan,
    docs_url="/docs",
)


def get_guard(request: Request) -> QuotaGuard:
    return request.app.state.guard


def get_provider(request: Request) -> Provider:
    return request.app.state.provider


def get_app_settings(request: Request) -> Settings:
    return request.app.state.settings


def check_app_token(settings: Settings, token: str | None) -> None:
    if settings.app_token and token != settings.app_token:
        raise HTTPException(status_code=403, detail="Applicazione non autorizzata.")


@app.exception_handler(QuotaError)
async def quota_error_handler(request: Request, exc: QuotaError) -> JSONResponse:
    return JSONResponse(status_code=exc.status_code, content={"detail": exc.message})


@app.exception_handler(ProviderError)
async def provider_error_handler(
    request: Request, exc: ProviderError
) -> JSONResponse:
    logger.warning("Errore fornitore: %s", exc.message)
    return JSONResponse(status_code=exc.status_code, content={"detail": exc.message})


@app.get("/health")
async def health() -> dict[str, str]:
    return {"status": "ok"}


@app.get("/v1/status", response_model=StatusResponse)
async def status(
    x_device_id: str = Header(default=""),
    x_app_token: str | None = Header(default=None),
    guard: QuotaGuard = Depends(get_guard),
    provider: Provider = Depends(get_provider),
    settings: Settings = Depends(get_app_settings),
) -> StatusResponse:
    check_app_token(settings, x_app_token)
    requests_today, _ = guard.store.device_usage(x_device_id or "sconosciuto")
    return StatusResponse(
        service_enabled=guard.service_enabled(),
        provider=provider.name,
        model=provider.model,
        daily_budget_usd=settings.daily_budget_usd,
        spent_today_usd=round(guard.store.spend_today(), 4),
        device_requests_today=requests_today,
        device_requests_limit=settings.daily_requests_per_device,
        rate_limit_per_window=settings.rate_limit_requests,
        rate_limit_window_seconds=settings.rate_limit_window_seconds,
    )


@app.post("/v1/summarize", response_model=SummarizeResponse)
async def summarize(
    payload: SummarizeRequest,
    x_device_id: str = Header(default=""),
    x_app_token: str | None = Header(default=None),
    guard: QuotaGuard = Depends(get_guard),
    provider: Provider = Depends(get_provider),
    settings: Settings = Depends(get_app_settings),
) -> SummarizeResponse:
    check_app_token(settings, x_app_token)
    guard.check(x_device_id, payload.text)

    result = await summarize_text(
        payload.text,
        style=payload.style,
        language=payload.language,
        provider=provider,
        settings=settings,
    )

    # Alcuni fornitori non riportano i token: in quel caso si stima, così il
    # tetto di spesa resta efficace.
    in_tokens = result.input_tokens or estimate_tokens(payload.text)
    out_tokens = result.output_tokens or estimate_tokens(result.summary)
    cost = guard.record(x_device_id, len(payload.text), in_tokens, out_tokens)

    logger.info(
        "Riassunto: device=%s… caratteri=%d blocchi=%d costo=%.5f USD",
        x_device_id[:6],
        len(payload.text),
        result.chunks,
        cost,
    )

    return SummarizeResponse(
        summary=result.summary,
        key_points=result.key_points,
        actions=result.actions,
        provider=provider.name,
        model=provider.model,
        chunks=result.chunks,
        estimated_cost_usd=round(cost, 6),
    )


@app.post("/admin/kill-switch")
async def kill_switch(
    enabled: bool,
    x_admin_token: str | None = Header(default=None),
    guard: QuotaGuard = Depends(get_guard),
    settings: Settings = Depends(get_app_settings),
) -> dict[str, bool]:
    """Disattiva o riattiva a mano il servizio cloud."""
    if not settings.admin_token or x_admin_token != settings.admin_token:
        raise HTTPException(status_code=403, detail="Accesso negato.")
    if enabled:
        guard.reset_kill_switch()
    else:
        guard.store.set_flag("cloud_disabled", "1")
    return {"service_enabled": guard.service_enabled()}
