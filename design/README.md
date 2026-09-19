# Design — SLIPSTREAM

Documentazione di pre-produzione per **SLIPSTREAM: Blood Circuit**, arena FPS competitivo per Android
(codename interno: *Project VELOCE*).

| Documento | Contenuto |
|---|---|
| [`SLIPSTREAM_PRE_PRODUCTION.md`](SLIPSTREAM_PRE_PRODUCTION.md) | Documento completo di pre-produzione in 12 sezioni: identità, gameplay, movimento, armi, mappe, risorse, modalità, ranking/esport, bot IA, tecnologia, monetizzazione, analisi critica e roadmap 48 mesi |

## Tesi del progetto

> Su touchscreen il tetto di abilità di mira è fisicamente limitato. Quindi il gioco non è costruito
> sulla mira, ma su **movimento, timing delle risorse e lettura della mappa** — tre assi di abilità
> che il touch non comprime.

## Indice rapido

1. Identità del gioco — §1
2. Gameplay fondamentale (core combat loop) — §2
3. Sistema di movimento e *Momentum Arc* — §3
4. Arsenale e bilanciamento (8 armi) — §4
5. Map design competitivo (5 arene) — §5
6. Power-up e map control — §6
7. Modalità di gioco, inclusa **Surge** (modalità originale) — §7
8. Ranking, matchmaking, esport — §8
9. Bot IA — architettura a 4 strati — §9
10. Tecnologia (UE5 mobile, netcode, anti-cheat) — §10
11. Monetizzazione — §11
12. Analisi critica del publisher e roadmap — §12

**Nota:** documento di design. Non è collegato all'applicazione Flutter contenuta in `app/`.
