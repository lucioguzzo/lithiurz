# Backend dei riassunti di VoceNota

Servizio FastAPI che sta fra l'app e il fornitore di modelli linguistici.
Esiste per tre motivi: **non mettere chiavi API dentro l'app**, **tenere i
costi sotto controllo** e **poter cambiare fornitore** senza aggiornare
l'app.

Riceve **solo testo**. L'audio resta sul telefono.

## Endpoint

| Metodo | Percorso | Descrizione |
|---|---|---|
| `GET` | `/health` | Stato di vita del processo |
| `GET` | `/v1/status` | Stato del servizio e quota residua del dispositivo |
| `POST` | `/v1/summarize` | Riassume un testo |
| `POST` | `/admin/kill-switch?enabled=…` | Spegne o riaccende il cloud (richiede `x-admin-token`) |

### `POST /v1/summarize`

Intestazioni: `x-device-id` (obbligatoria, identificativo anonimo),
`x-app-token` (se configurata).

```json
{ "text": "trascrizione…", "style": "standard", "language": "it" }
```

Risposta:

```json
{
  "summary": "…",
  "key_points": ["…"],
  "actions": [{"text": "…", "owner": null, "due": "venerdì"}],
  "provider": "anthropic",
  "model": "claude-haiku-5-5",
  "chunks": 3,
  "estimated_cost_usd": 0.0042
}
```

Codici di errore che l'app sa gestire: `429` (quota o frequenza), `503`
(servizio sospeso o budget esaurito), `403` (app non autorizzata), `413`
(testo troppo lungo). In tutti i casi l'app ripiega sul riassunto locale.

## Controllo dei costi

1. tetto alla dimensione della richiesta;
2. limite di frequenza per dispositivo (finestra scorrevole);
3. quota giornaliera per dispositivo (richieste e caratteri);
4. costo stimato sui token realmente consumati e sommato giorno per giorno;
5. **interruttore automatico** al superamento del budget giornaliero;
6. interruttore manuale via `/admin/kill-switch`.

I contatori stanno in SQLite e contengono **solo numeri**: nessuna
trascrizione, nessun riassunto, né nei dati né nei log.

## Avvio

```bash
pip install -r requirements-dev.txt
python -m pytest            # 39 test, senza rete
cp .env.example .env
uvicorn app.main:app --reload
```

Con `VOCENOTA_PROVIDER=echo` (predefinito) il servizio funziona senza chiavi
API: i riassunti sono fittizi ma la pipeline è completa, utile per provare
l'app.

## Messa in esercizio

- Terminare sempre il TLS davanti al servizio (reverse proxy o piattaforma):
  l'app accetta **solo HTTPS**.
- Montare un volume per `/dati`, altrimenti le quote si azzerano a ogni
  riavvio.
- Prezzi (`PRICE_*_PER_MTOK`) allineati al listino reale del fornitore,
  altrimenti il tetto di spesa stima male.
- Il servizio è a processo singolo con SQLite: per più repliche serve un
  archivio condiviso (Postgres o Redis) al posto di `storage.py`.
