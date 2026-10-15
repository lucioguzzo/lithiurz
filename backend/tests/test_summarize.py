import pytest

from app.config import Settings
from app.providers.base import Completion, Provider
from app.providers.echo import EchoProvider
from app.summarize import normalize, parse_model_json, summarize_text


def test_parse_json_semplice():
    assert parse_model_json('{"summary": "ciao"}')["summary"] == "ciao"


def test_parse_json_dentro_un_blocco_markdown():
    raw = 'Ecco il risultato:\n```json\n{"summary": "ok", "key_points": ["a"]}\n```'
    parsed = parse_model_json(raw)
    assert parsed["summary"] == "ok"
    assert parsed["key_points"] == ["a"]


def test_parse_json_con_testo_attorno():
    raw = 'Certo! {"summary": "breve"} Spero vada bene.'
    assert parse_model_json(raw)["summary"] == "breve"


def test_parse_risposta_non_json_diventa_riassunto():
    parsed = parse_model_json("Il riassunto in chiaro.")
    assert parsed["summary"] == "Il riassunto in chiaro."
    assert parsed["key_points"] == []


def test_normalize_pulisce_duplicati_e_valori_nulli():
    result = normalize(
        {
            "summary": "  testo  ",
            "key_points": ["a", "a", "", "b"],
            "actions": [
                {"text": "fare x", "owner": "null", "due": ""},
                {"text": "", "owner": "Luca"},
                "fare y",
            ],
        }
    )
    assert result.summary == "testo"
    assert result.key_points == ["a", "b"]
    assert [a["text"] for a in result.actions] == ["fare x", "fare y"]
    assert result.actions[0]["owner"] is None
    assert result.actions[0]["due"] is None


class CountingProvider(Provider):
    """Registra i prompt ricevuti per verificare la strategia mappa/riduci."""

    name = "counting"
    model = "test"

    def __init__(self) -> None:
        self.prompts: list[str] = []

    async def complete(self, system, prompt, max_output_tokens=1500):
        self.prompts.append(prompt)
        return Completion(
            text='{"summary": "parziale", "key_points": ["p"], "actions": []}',
            input_tokens=10,
            output_tokens=5,
        )


@pytest.mark.asyncio
async def test_testo_breve_una_sola_chiamata():
    provider = CountingProvider()
    settings = Settings(map_reduce_threshold_chars=1000)
    result = await summarize_text(
        "Testo breve.",
        style="standard",
        language="italiano",
        provider=provider,
        settings=settings,
    )
    assert len(provider.prompts) == 1
    assert result.chunks == 1
    assert result.summary == "parziale"


@pytest.mark.asyncio
async def test_testo_lungo_mappa_e_riduce():
    provider = CountingProvider()
    settings = Settings(
        map_reduce_threshold_chars=500, chunk_chars=400, chunk_overlap_chars=0
    )
    testo = " ".join(f"Frase numero {i} della riunione." for i in range(200))
    result = await summarize_text(
        testo,
        style="dettagliato",
        language="italiano",
        provider=provider,
        settings=settings,
    )
    # una chiamata per blocco + una di riduzione finale
    assert len(provider.prompts) == result.chunks + 1
    assert result.chunks > 1
    assert "Riassunti parziali" in provider.prompts[-1]
    assert result.input_tokens == 10 * (result.chunks + 1)


@pytest.mark.asyncio
async def test_echo_provider_produce_json_valido():
    settings = Settings()
    result = await summarize_text(
        "Dobbiamo chiamare il fornitore. La consegna slitta a lunedì.",
        style="breve",
        language="italiano",
        provider=EchoProvider(),
        settings=settings,
    )
    assert result.summary
    assert result.key_points
    assert any("chiamare" in a["text"] for a in result.actions)
