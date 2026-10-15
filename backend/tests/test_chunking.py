from app.chunking import chunk_text, needs_map_reduce, split_sentences


def test_split_sentences_normalizza_gli_spazi():
    text = "Prima frase.   Seconda frase!\n\nTerza?"
    assert split_sentences(text) == ["Prima frase.", "Seconda frase!", "Terza?"]


def test_split_sentences_su_testo_vuoto():
    assert split_sentences("   \n  ") == []


def test_chunk_text_restituisce_un_solo_blocco_se_breve():
    assert chunk_text("Ciao mondo.", max_chars=100) == ["Ciao mondo."]


def test_chunk_text_rispetta_il_limite():
    text = " ".join(f"Frase numero {i} del discorso." for i in range(200))
    blocks = chunk_text(text, max_chars=300, overlap_chars=0)
    assert len(blocks) > 1
    assert all(len(b) <= 300 for b in blocks)


def test_chunk_text_non_perde_contenuto():
    text = " ".join(f"Parola{i}." for i in range(500))
    blocks = chunk_text(text, max_chars=200, overlap_chars=0)
    ricostruito = " ".join(blocks)
    for i in (0, 123, 499):
        assert f"Parola{i}." in ricostruito


def test_chunk_text_spezza_una_frase_enorme():
    text = "x" * 1000
    blocks = chunk_text(text, max_chars=250)
    assert len(blocks) == 4
    assert sum(len(b) for b in blocks) == 1000


def test_chunk_text_sovrapposizione_ripete_la_coda():
    text = " ".join(f"Frase {i} con un po' di testo." for i in range(40))
    blocks = chunk_text(text, max_chars=200, overlap_chars=50)
    assert len(blocks) > 1
    # La coda del primo blocco ricompare all'inizio del secondo.
    coda = blocks[0][-20:]
    assert coda in blocks[1]


def test_chunk_text_rifiuta_limiti_non_validi():
    try:
        chunk_text("abc", max_chars=0)
    except ValueError:
        return
    raise AssertionError("atteso ValueError")


def test_needs_map_reduce():
    assert needs_map_reduce("a" * 101, 100) is True
    assert needs_map_reduce("a" * 100, 100) is False
