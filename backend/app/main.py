"""API di BRIEF: riassunti e, su richiesta esplicita, trascrizione.

Protegge le chiavi dei fornitori, applica quote, limiti di frequenza e un
tetto di spesa con disattivazione automatica.

Il riassunto riceve solo testo. La trascrizione riceve audio, ma solo
quando è l'utente ad attivarla nell'app: l'audio viene inoltrato al
fornitore e non viene mai scritto su disco né nei log.
"""

from __future__ import annotations

import logging
from contextlib import asynccontextmanager
from typing import AsyncIterator

from fastapi import (
    Depends,
    FastAPI,
    File,
    Form,
    Header,
    HTTPException,
    Request,
    UploadFile,
)
from fastapi.responses import JSONResponse

from .audio import AudioError, read_wav_info
from .config import Settings, get_settings
from .providers import build_provider, build_transcriber
from .providers.base import Provider, ProviderError, Transcriber
from .quota import QuotaError, QuotaGuard, estimate_tokens
from .schemas import (
    StatusResponse,
    SummarizeRequest,
    SummarizeResponse,
    TranscribeResponse,
)
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
    app.state.transcriber = build_transcriber(settings)
    logger.info(
        "Avvio: riassunti=%s/%s trascrizione=%s/%s budget=%.2f USD/giorno",
        app.state.provider.name,
        app.state.provider.model,
        app.state.transcriber.name,
        app.state.transcriber.model,
        settings.daily_budget_usd,
    )
    try:
        yield
    finally:
        await app.state.provider.aclose()
        await app.state.transcriber.aclose()
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


def get_transcriber(request: Request) -> Transcriber:
    return request.app.state.transcriber


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
    request: Request,
    x_device_id: str = Header(default=""),
    x_app_token: str | None = Header(default=None),
    guard: QuotaGuard = Depends(get_guard),
    provider: Provider = Depends(get_provider),
    settings: Settings = Depends(get_app_settings),
) -> StatusResponse:
    check_app_token(settings, x_app_token)
    device = x_device_id or "sconosciuto"
    requests_today, _ = guard.store.device_usage(device)
    transcriber: Transcriber = request.app.state.transcriber
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
        transcription_provider=transcriber.name,
        transcription_model=transcriber.model,
        device_audio_seconds_today=round(
            guard.store.device_audio_seconds(device), 1
        ),
        device_audio_seconds_limit=settings.daily_audio_seconds_per_device,
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


@app.post("/v1/transcribe", response_model=TranscribeResponse)
async def transcribe(
    file: UploadFile = File(...),
    language: str = Form(default="it"),
    x_device_id: str = Header(default=""),
    x_app_token: str | None = Header(default=None),
    guard: QuotaGuard = Depends(get_guard),
    transcriber: Transcriber = Depends(get_transcriber),
    settings: Settings = Depends(get_app_settings),
) -> TranscribeResponse:
    """Trascrive un blocco audio.

    L'app invia un blocco alla volta: così l'avanzamento resta visibile,
    una richiesta che fallisce non butta via l'intera registrazione e
    nessun file supera i limiti di dimensione del fornitore.
    """
    check_app_token(settings, x_app_token)

    # Si legge in memoria: niente file temporanei, niente audio su disco.
    payload = await file.read()
    if not payload:
        raise HTTPException(status_code=400, detail="Blocco audio vuoto.")

    try:
        seconds = read_wav_info(payload).seconds
    except AudioError as exc:
        # La durata serve per le quote: senza, non si serve la richiesta.
        raise HTTPException(status_code=415, detail=str(exc)) from exc

    guard.check_audio(x_device_id, len(payload), seconds)

    result = await transcriber.transcribe(payload, "audio/wav", language)
    out_tokens = result.output_tokens or estimate_tokens(result.text)
    cost = guard.record_audio(x_device_id, seconds, out_tokens)

    logger.info(
        "Trascrizione: device=%s… secondi=%.1f byte=%d costo=%.5f USD",
        x_device_id[:6],
        seconds,
        len(payload),
        cost,
    )

    return TranscribeResponse(
        text=result.text,
        provider=transcriber.name,
        model=transcriber.model,
        seconds=round(seconds, 2),
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
