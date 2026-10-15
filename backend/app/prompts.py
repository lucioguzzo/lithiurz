"""Prompt per la sintesi. Tenuti separati per poterli rivedere senza
toccare la logica."""

from __future__ import annotations

STYLE_GUIDE = {
    "breve": "Da 2 a 3 frasi. Solo il senso generale, niente dettagli.",
    "standard": "Da 5 a 8 frasi, in uno o due paragrafi, con i fatti salienti.",
    "dettagliato": (
        "Da 12 a 20 frasi, organizzate per argomento, mantenendo numeri, "
        "nomi, date e cifre citate."
    ),
}

SYSTEM = (
    "Sei un assistente che riassume trascrizioni di messaggi vocali e "
    "registrazioni in italiano. La trascrizione è automatica e può contenere "
    "errori, ripetizioni e parole mancanti: interpreta con buon senso, senza "
    "inventare fatti che non ci sono. Non aggiungere commenti sul tuo "
    "ragionamento. Rispondi esclusivamente con un oggetto JSON valido."
)

OUTPUT_CONTRACT = """
Rispondi con questo JSON, senza testo prima o dopo:
{
  "summary": "testo del riassunto",
  "key_points": ["punto 1", "punto 2"],
  "actions": [{"text": "cosa fare", "owner": "chi (o null)", "due": "quando (o null)"}]
}
Regole:
- Scrivi in %(language)s.
- "key_points": da 3 a 7 voci, ciascuna una frase autonoma.
- "actions": solo impegni, richieste o scadenze realmente presenti. Se non ce
  ne sono, usa una lista vuota.
- Non inserire informazioni assenti dal testo.
"""


def map_prompt(chunk: str, index: int, total: int, language: str) -> str:
    """Prompt per il riassunto di un singolo blocco di un audio lungo."""
    return (
        f"Questa è la parte {index + 1} di {total} della trascrizione di una "
        "registrazione. Riassumi SOLO questa parte, conservando fatti, nomi, "
        "numeri e impegni. Non tentare di concludere il discorso: altre parti "
        "seguono.\n\n"
        + OUTPUT_CONTRACT % {"language": language}
        + f"\nTrascrizione (parte {index + 1}/{total}):\n\"\"\"\n{chunk}\n\"\"\""
    )


def reduce_prompt(partials: list[str], style: str, language: str) -> str:
    """Prompt che fonde i riassunti parziali in quello definitivo."""
    joined = "\n\n".join(
        f"[Parte {i + 1}]\n{p}" for i, p in enumerate(partials)
    )
    return (
        "Di seguito i riassunti delle parti successive di un'unica "
        "registrazione, in ordine cronologico. Uniscili in un unico riassunto "
        "coerente, eliminando le ripetizioni e mantenendo l'ordine degli "
        "argomenti.\n"
        f"Lunghezza richiesta: {STYLE_GUIDE.get(style, STYLE_GUIDE['standard'])}\n\n"
        + OUTPUT_CONTRACT % {"language": language}
        + f"\nRiassunti parziali:\n\"\"\"\n{joined}\n\"\"\""
    )


def single_prompt(text: str, style: str, language: str) -> str:
    """Prompt per una trascrizione che sta in una sola richiesta."""
    return (
        "Riassumi la seguente trascrizione.\n"
        f"Lunghezza richiesta: {STYLE_GUIDE.get(style, STYLE_GUIDE['standard'])}\n\n"
        + OUTPUT_CONTRACT % {"language": language}
        + f"\nTrascrizione:\n\"\"\"\n{text}\n\"\"\""
    )
