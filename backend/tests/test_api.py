"""Test dell'API end-to-end con il fornitore fittizio (nessuna rete)."""

import pytest
from fastapi.testclient import TestClient

from app import main
from app.config import Settings


def build_client(tmp_path, **overrides) -> TestClient:
    settings = Settings(
        provider="echo",
        database_path=str(tmp_path / "usage.db"),
        **overrides,
    )
    # Il lifespan legge `settings_override` quando presente.
    main.app.state.settings_override = settings
    return TestClient(main.app)


@pytest.fixture()
def client(tmp_path):
    with build_client(tmp_path) as c:
        yield c


def test_health(client):
    assert client.get("/health").json() == {"status": "ok"}


def test_riassunto_di_un_testo_breve(client):
    response = client.post(
        "/v1/summarize",
        headers={"x-device-id": "dispositivo-prova-1"},
        json={
            "text": "Ciao, ti confermo la riunione di domani alle dieci. "
            "Dobbiamo portare il preventivo aggiornato.",
            "style": "standard",
            "language": "it",
        },
    )
    assert response.status_code == 200
    body = response.json()
    assert body["summary"]
    assert body["provider"] == "echo"
    assert body["chunks"] == 1
    assert body["estimated_cost_usd"] >= 0
    assert any("portare" in a["text"] for a in body["actions"])


def test_richiesta_senza_identificativo_rifiutata(client):
    response = client.post("/v1/summarize", json={"text": "ciao"})
    assert response.status_code == 400


def test_testo_vuoto_rifiutato_dalla_validazione(client):
    response = client.post(
        "/v1/summarize",
        headers={"x-device-id": "dispositivo-prova-1"},
        json={"text": ""},
    )
    assert response.status_code == 422


def test_stato_del_servizio(client):
    response = client.get("/v1/status", headers={"x-device-id": "dispositivo-prova-1"})
    assert response.status_code == 200
    body = response.json()
    assert body["service_enabled"] is True
    assert body["device_requests_today"] == 0
    assert body["provider"] == "echo"


def test_la_quota_giornaliera_viene_applicata(tmp_path):
    with build_client(
        tmp_path, daily_requests_per_device=1, rate_limit_requests=10
    ) as client:
        headers = {"x-device-id": "dispositivo-prova-2"}
        payload = {"text": "Un messaggio qualsiasi da riassumere."}
        assert client.post("/v1/summarize", headers=headers, json=payload).status_code == 200
        second = client.post("/v1/summarize", headers=headers, json=payload)
        assert second.status_code == 429
        assert "limite giornaliero" in second.json()["detail"]


def test_limite_di_frequenza_applicato(tmp_path):
    with build_client(
        tmp_path, rate_limit_requests=1, rate_limit_window_seconds=600
    ) as client:
        headers = {"x-device-id": "dispositivo-prova-3"}
        payload = {"text": "Messaggio di prova."}
        assert client.post("/v1/summarize", headers=headers, json=payload).status_code == 200
        assert client.post("/v1/summarize", headers=headers, json=payload).status_code == 429


def test_token_applicazione_obbligatorio_se_configurato(tmp_path):
    with build_client(tmp_path, app_token="segreto-pubblico") as client:
        headers = {"x-device-id": "dispositivo-prova-4"}
        payload = {"text": "Messaggio."}
        assert client.post("/v1/summarize", headers=headers, json=payload).status_code == 403
        ok = client.post(
            "/v1/summarize",
            headers={**headers, "x-app-token": "segreto-pubblico"},
            json=payload,
        )
        assert ok.status_code == 200


def test_servizio_disattivato_risponde_503(tmp_path):
    with build_client(tmp_path, disabled=True) as client:
        response = client.post(
            "/v1/summarize",
            headers={"x-device-id": "dispositivo-prova-5"},
            json={"text": "Messaggio."},
        )
        assert response.status_code == 503


def test_interruttore_amministrativo(tmp_path):
    with build_client(tmp_path, admin_token="chiave-admin") as client:
        assert client.post("/admin/kill-switch?enabled=false").status_code == 403
        off = client.post(
            "/admin/kill-switch?enabled=false",
            headers={"x-admin-token": "chiave-admin"},
        )
        assert off.json() == {"service_enabled": False}
        blocked = client.post(
            "/v1/summarize",
            headers={"x-device-id": "dispositivo-prova-6"},
            json={"text": "Messaggio."},
        )
        assert blocked.status_code == 503
        on = client.post(
            "/admin/kill-switch?enabled=true",
            headers={"x-admin-token": "chiave-admin"},
        )
        assert on.json() == {"service_enabled": True}


def test_testo_lungo_passa_da_mappa_e_riduci(tmp_path):
    with build_client(
        tmp_path, map_reduce_threshold_chars=600, chunk_chars=500
    ) as client:
        testo = " ".join(
            f"Nel punto {i} abbiamo discusso del budget." for i in range(120)
        )
        response = client.post(
            "/v1/summarize",
            headers={"x-device-id": "dispositivo-prova-7"},
            json={"text": testo, "style": "dettagliato"},
        )
        assert response.status_code == 200
        assert response.json()["chunks"] > 1
