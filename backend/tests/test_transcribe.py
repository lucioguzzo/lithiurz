"""Trascrizione nel cloud: endpoint, quote e lettura della risposta."""

import pytest
from fastapi.testclient import TestClient

from app import main
from app.config import Settings
from app.providers.gemini import EMPTY_MARKER, extract_text
from tests.test_audio import wav


def build_client(tmp_path, **overrides) -> TestClient:
    settings = Settings(
        provider="echo",
        transcription_provider="echo",
        database_path=str(tmp_path / "usage.db"),
        **overrides,
    )
    main.app.state.settings_override = settings
    return TestClient(main.app)


@pytest.fixture()
def client(tmp_path):
    with build_client(tmp_path) as c:
        yield c


def post_audio(client, payload: bytes, device: str = "dispositivo-prova-1"):
    return client.post(
        "/v1/transcribe",
        headers={"x-device-id": device},
        files={"file": ("blocco.wav", payload, "audio/wav")},
        data={"language": "it"},
    )


def test_un_blocco_audio_torna_come_testo(client):
    response = post_audio(client, wav(30))
    assert response.status_code == 200
    body = response.json()
    assert body["text"]
    assert body["seconds"] == pytest.approx(30, abs=0.1)
    assert body["provider"] == "echo"


def test_la_durata_viene_letta_dal_file_non_dichiarata_dal_client(client):
    # Il client non manda nessuna durata: se la contasse lui, basterebbe
    # dichiarare zero secondi per aggirare le quote.
    assert post_audio(client, wav(12)).json()["seconds"] == pytest.approx(12, abs=0.1)


def test_un_file_che_non_e_audio_viene_rifiutato(client):
    assert post_audio(client, b"non sono un wav").status_code == 415


def test_un_blocco_vuoto_viene_rifiutato(client):
    assert post_audio(client, b"").status_code == 400


def test_senza_identificativo_del_dispositivo_si_viene_respinti(client):
    response = client.post(
        "/v1/transcribe",
        files={"file": ("blocco.wav", wav(5), "audio/wav")},
    )
    assert response.status_code == 400


def test_un_blocco_oltre_il_limite_di_dimensione_viene_respinto(tmp_path):
    with build_client(tmp_path, max_audio_bytes=1000) as client:
        assert post_audio(client, wav(30)).status_code == 413


def test_il_limite_giornaliero_di_audio_ferma_la_trascrizione(tmp_path):
    with build_client(
        tmp_path, daily_audio_seconds_per_device=45, rate_limit_requests=100
    ) as client:
        assert post_audio(client, wav(30)).status_code == 200
        secondo = post_audio(client, wav(30))
        assert secondo.status_code == 429
        assert "audio" in secondo.json()["detail"]


def test_la_spesa_viene_contata_e_lo_stato_la_riporta(tmp_path):
    with build_client(tmp_path, rate_limit_requests=100) as client:
        post_audio(client, wav(60))
        stato = client.get(
            "/v1/status", headers={"x-device-id": "dispositivo-prova-1"}
        ).json()
        assert stato["device_audio_seconds_today"] == pytest.approx(60, abs=0.1)
        assert stato["spent_today_usd"] > 0
        assert stato["transcription_provider"] == "echo"


def test_il_tetto_di_spesa_sospende_anche_la_trascrizione(tmp_path):
    with build_client(
        tmp_path,
        daily_budget_usd=0.0000001,
        rate_limit_requests=100,
    ) as client:
        assert post_audio(client, wav(30)).status_code == 200
        assert post_audio(client, wav(30)).status_code == 503


class TestRispostaGemini:
    """L'API espone il testo in forme diverse: vanno accettate tutte."""

    def test_campo_diretto(self):
        assert extract_text({"output_text": " Ciao a tutti "}) == "Ciao a tutti"

    def test_passo_model_output(self):
        data = {
            "id": "int_1",
            "steps": [
                {"type": "thought", "content": [{"text": "ragionamento"}]},
                {"type": "model_output", "content": [{"text": "Il testo vero"}]},
            ],
        }
        assert extract_text(data) == "Il testo vero"

    def test_forma_precedente_con_candidates(self):
        data = {
            "candidates": [
                {"content": {"parts": [{"text": "Testo alla vecchia maniera"}]}}
            ]
        }
        assert extract_text(data) == "Testo alla vecchia maniera"

    def test_risposta_senza_testo_non_fa_saltare_nulla(self):
        assert extract_text({"id": "int_2", "steps": []}) == ""

    def test_il_marcatore_di_silenzio_resta_riconoscibile(self):
        # Il provider lo converte in stringa vuota: non deve finire
        # nella trascrizione dell'utente.
        assert extract_text({"output_text": EMPTY_MARKER}) == EMPTY_MARKER


class TestChiamataGemini:
    """La richiesta inviata a Google, verificata senza chiave né rete."""

    @staticmethod
    def _transcriber(handler):
        import httpx

        from app.providers.gemini import GeminiTranscriber

        t = GeminiTranscriber(api_key="prova", model="gemini-3.8-flash")
        t._client = httpx.AsyncClient(
            base_url="https://generativelanguage.googleapis.com/v1beta",
            transport=httpx.MockTransport(handler),
            headers={"x-goog-api-key": "prova"},
        )
        return t

    async def test_invia_audio_e_istruzioni_e_legge_il_testo(self):
        import base64
        import json

        visto = {}

        def handler(request):
            import httpx

            visto["url"] = str(request.url)
            visto["key"] = request.headers.get("x-goog-api-key")
            visto["body"] = json.loads(request.content)
            return httpx.Response(
                200,
                json={
                    "id": "int_1",
                    "steps": [
                        {"type": "model_output", "content": [{"text": "Ciao a tutti"}]}
                    ],
                    "usage": {"input_tokens": 1000, "output_tokens": 10},
                },
            )

        t = self._transcriber(handler)
        result = await t.transcribe(b"\x01\x02\x03", "audio/wav", "it")
        await t.aclose()

        assert result.text == "Ciao a tutti"
        assert (result.input_tokens, result.output_tokens) == (1000, 10)
        assert visto["url"].endswith("/interactions")
        assert visto["key"] == "prova"

        parti = visto["body"]["input"]
        assert visto["body"]["model"] == "gemini-3.8-flash"
        assert parti[0]["type"] == "text"
        assert "italiano" in parti[0]["text"]
        assert parti[1]["mime_type"] == "audio/wav"
        assert base64.b64decode(parti[1]["data"]) == b"\x01\x02\x03"

    async def test_il_silenzio_torna_come_testo_vuoto(self):
        import httpx

        t = self._transcriber(
            lambda request: httpx.Response(200, json={"output_text": EMPTY_MARKER})
        )
        result = await t.transcribe(b"\x00", "audio/wav", "it")
        await t.aclose()
        assert result.text == ""

    async def test_un_errore_del_fornitore_diventa_ProviderError(self):
        import httpx

        from app.providers.base import ProviderError

        t = self._transcriber(lambda request: httpx.Response(500, text="guasto"))
        with pytest.raises(ProviderError):
            await t.transcribe(b"\x00", "audio/wav", "it")
        await t.aclose()

    async def test_sovraccarico_diventa_servizio_non_disponibile(self):
        import httpx

        from app.providers.base import ProviderError

        t = self._transcriber(lambda request: httpx.Response(429, text="slow down"))
        with pytest.raises(ProviderError) as info:
            await t.transcribe(b"\x00", "audio/wav", "it")
        await t.aclose()
        assert info.value.status_code == 503
