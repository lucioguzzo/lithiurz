import time

import pytest

from app.config import Settings
from app.quota import QuotaError, QuotaGuard, estimate_cost, estimate_tokens
from app.storage import UsageStore


@pytest.fixture()
def guard(tmp_path):
    settings = Settings(
        daily_budget_usd=1.0,
        daily_requests_per_device=3,
        daily_chars_per_device=1000,
        rate_limit_requests=2,
        rate_limit_window_seconds=60,
        max_input_chars=500,
        price_input_per_mtok=1.0,
        price_output_per_mtok=5.0,
    )
    store = UsageStore(str(tmp_path / "usage.db"))
    yield QuotaGuard(settings, store)
    store.close()


DEVICE = "dispositivo-di-prova"


def test_accetta_la_prima_richiesta(guard):
    guard.check(DEVICE, "ciao")


def test_rifiuta_identificativo_mancante(guard):
    with pytest.raises(QuotaError) as exc:
        guard.check("", "ciao")
    assert exc.value.status_code == 400


def test_rifiuta_testo_troppo_lungo(guard):
    with pytest.raises(QuotaError) as exc:
        guard.check(DEVICE, "x" * 501)
    assert exc.value.status_code == 413


def test_limite_di_frequenza(guard):
    now = time.time()
    guard.check(DEVICE, "a", now=now)
    guard.check(DEVICE, "a", now=now + 1)
    with pytest.raises(QuotaError) as exc:
        guard.check(DEVICE, "a", now=now + 2)
    assert exc.value.status_code == 429
    # Fuori dalla finestra la richiesta torna a passare.
    guard.check(DEVICE, "a", now=now + 61)


def test_limite_giornaliero_di_richieste(guard):
    now = time.time()
    for i in range(3):
        guard.record(DEVICE, chars=10, in_tokens=1, out_tokens=1)
    with pytest.raises(QuotaError) as exc:
        guard.check(DEVICE, "a", now=now)
    assert exc.value.status_code == 429
    assert "limite giornaliero" in exc.value.message


def test_limite_giornaliero_di_caratteri(tmp_path):
    settings = Settings(
        daily_requests_per_device=100,
        daily_chars_per_device=100,
        rate_limit_requests=100,
        max_input_chars=1000,
    )
    store = UsageStore(str(tmp_path / "u.db"))
    guard = QuotaGuard(settings, store)
    guard.record(DEVICE, chars=90, in_tokens=1, out_tokens=1)
    with pytest.raises(QuotaError):
        guard.check(DEVICE, "x" * 50)
    store.close()


def test_interruttore_di_spesa_scatta_e_blocca(guard):
    # 1 USD di budget: 1.000.000 token in uscita a 5 USD/Mtok lo superano.
    guard.record(DEVICE, chars=10, in_tokens=0, out_tokens=1_000_000)
    assert guard.service_enabled() is False
    with pytest.raises(QuotaError) as exc:
        guard.check("altro-dispositivo", "a")
    assert exc.value.status_code == 503


def test_interruttore_si_puo_riarmare(guard):
    guard.record(DEVICE, chars=10, in_tokens=0, out_tokens=1_000_000)
    assert guard.service_enabled() is False
    guard.reset_kill_switch()
    # La spesa resta oltre il budget, quindi il servizio è comunque fermo.
    assert guard.service_enabled() is False


def test_stima_costo():
    settings = Settings(price_input_per_mtok=3.0, price_output_per_mtok=15.0)
    assert estimate_cost(settings, 1_000_000, 0) == pytest.approx(3.0)
    assert estimate_cost(settings, 0, 100_000) == pytest.approx(1.5)


def test_stima_token_cresce_col_testo():
    assert estimate_tokens("a" * 350) == 100
    assert estimate_tokens("") == 1
