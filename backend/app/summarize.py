"""Sintesi gerarchica: mappa i blocchi, poi riduce in un unico riassunto."""

from __future__ import annotations

import json
import re
from dataclasses import dataclass, field

from .chunking import chunk_text, needs_map_reduce
from .config import Settings
from .prompts import SYSTEM, map_prompt, reduce_prompt, single_prompt
from .providers.base import Completion, Provider

_JSON_BLOCK = re.compile(r"\{.*\}", re.DOTALL)


@dataclass
class SummaryResult:
    summary: str
    key_points: list[str] = field(default_factory=list)
    actions: list[dict[str, str | None]] = field(default_factory=list)
    chunks: int = 1
    input_tokens: int = 0
    output_tokens: int = 0


def parse_model_json(raw: str) -> dict:
    """Estrae l'oggetto JSON dalla risposta del modello.

    I modelli a volte avvolgono il JSON in testo o in un blocco markdown:
    si prende il primo oggetto bilanciato, e se proprio non c'è si tratta
    l'intera risposta come riassunto in chiaro.
    """
    text = (raw or "").strip()
    if not text:
        return {"summary": "", "key_points": [], "actions": []}

    candidates: list[str] = []
    fenced = re.search(r"```(?:json)?\s*(.*?)```", text, re.DOTALL)
    if fenced:
        candidates.append(fenced.group(1).strip())
    block = _JSON_BLOCK.search(text)
    if block:
        candidates.append(block.group(0))
    candidates.append(text)

    for candidate in candidates:
        try:
            parsed = json.loads(candidate)
        except (json.JSONDecodeError, ValueError):
            continue
        if isinstance(parsed, dict):
            return parsed

    return {"summary": text, "key_points": [], "actions": []}


def normalize(parsed: dict) -> SummaryResult:
    """Porta la risposta del modello nella forma attesa dall'app."""
    summary = str(parsed.get("summary") or "").strip()

    key_points: list[str] = []
    for item in parsed.get("key_points") or []:
        value = str(item).strip()
        if value and value not in key_points:
            key_points.append(value)

    actions: list[dict[str, str | None]] = []
    for item in parsed.get("actions") or []:
        if isinstance(item, dict):
            text = str(item.get("text") or "").strip()
            owner = item.get("owner")
            due = item.get("due")
        else:
            text, owner, due = str(item).strip(), None, None
        if not text:
            continue
        actions.append(
            {
                "text": text,
                "owner": str(owner).strip() if owner not in (None, "", "null") else None,
                "due": str(due).strip() if due not in (None, "", "null") else None,
            }
        )

    return SummaryResult(summary=summary, key_points=key_points, actions=actions)


def max_output_tokens_for(style: str) -> int:
    return {"breve": 600, "standard": 1200, "dettagliato": 2200}.get(style, 1200)


async def summarize_text(
    text: str,
    *,
    style: str,
    language: str,
    provider: Provider,
    settings: Settings,
) -> SummaryResult:
    """Riassume ``text``, spezzandolo se supera la soglia configurata."""
    in_tokens = 0
    out_tokens = 0

    if not needs_map_reduce(text, settings.map_reduce_threshold_chars):
        completion: Completion = await provider.complete(
            SYSTEM,
            single_prompt(text, style, language),
            max_output_tokens=max_output_tokens_for(style),
        )
        result = normalize(parse_model_json(completion.text))
        result.chunks = 1
        result.input_tokens = completion.input_tokens
        result.output_tokens = completion.output_tokens
        return result

    blocks = chunk_text(
        text,
        max_chars=settings.chunk_chars,
        overlap_chars=settings.chunk_overlap_chars,
    )

    partials: list[str] = []
    for index, block in enumerate(blocks):
        completion = await provider.complete(
            SYSTEM,
            map_prompt(block, index, len(blocks), language),
            max_output_tokens=900,
        )
        in_tokens += completion.input_tokens
        out_tokens += completion.output_tokens
        partial = normalize(parse_model_json(completion.text))
        # Il riduttore lavora meglio su testo piano che su JSON annidato.
        pieces = [partial.summary]
        pieces += [f"- {p}" for p in partial.key_points]
        pieces += [f"AZIONE: {a['text']}" for a in partial.actions]
        partials.append("\n".join(p for p in pieces if p).strip())

    final = await provider.complete(
        SYSTEM,
        reduce_prompt(partials, style, language),
        max_output_tokens=max_output_tokens_for(style),
    )
    in_tokens += final.input_tokens
    out_tokens += final.output_tokens

    result = normalize(parse_model_json(final.text))
    result.chunks = len(blocks)
    result.input_tokens = in_tokens
    result.output_tokens = out_tokens
    return result
