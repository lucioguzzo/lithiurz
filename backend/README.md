# Backend dei riassunti di BRIEF

Servizio FastAPI che sta fra l'app e il fornitore di modelli linguistici.
Esiste per tre motivi: **non mettere chiavi API dentro l'app**, **tenere i
costi sotto controllo** e **poter cambiare fornitore** senza aggiornare
l'app.

Riceve **solo testo** per i riassunti. Riceve anche **audio**, ma solo per
la trascrizione nel cloud, che nell'app è spenta di default e si attiva
con un consenso esplicito: in quel caso il blocco audio viene inoltrato al
fornitore e non viene mai scritto su disco né nei log.

## Endpoint

| Metodo | Percorso | Descrizione |
|---|---|---|
| `GET` | `/health` | Stato di vita del processo |
| `GET` | `/v1/status` | Stato del servizio e quota residua del dispositivo |
| `POST` | `/v1/summarize` | Riassume un testo |
| `POST` | `/v1/transcribe` | Trascrive un blocco audio (WAV) |
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

### `POST /v1/transcribe`

`multipart/form-data` con il campo `file` (un blocco WAV PCM 16 bit mono a
16 kHz, quello che l'app prepara con ffmpeg) e il campo `language`.
Intestazioni come sopra.

```json
{
  "text": "…",
  "provider": "gemini",
  "model": "gemini-3.8-flash",
  "seconds": 90.0,
  "estimated_cost_usd": 0.0022
}
```

La durata viene **letta dall'intestazione del WAV**, non dichiarata dal
client: è su quella che si applicano le quote, e un client potrebbe
dichiarare zero secondi. Errori: `415` (non è un WAV leggibile), `413`
(blocco troppo grande), `429` (quota giornaliera di audio o frequenza),
`503` (servizio sospeso).

Il fornitore è un modello generalista, non un riconoscitore vocale: il
prompt gli impone di restituire **solo** il parlato, senza preamboli né
traduzioni, e di rispondere con un marcatore convenuto quando nell'audio
non c'è voce — che il provider converte in testo vuoto.

### Gemini e l'Europa

I [termini dell'API Gemini](https://ai.google.dev/gemini-api/terms)
prevedono che un'app rivolta a utenti di SEE, Svizzera o Regno Unito usi
**solo servizi a pagamento**: per BRIEF serve quindi la chiave di un
progetto Google Cloud con fatturazione attiva. Il codice è identico nei
due casi. Sul piano gratuito, inoltre, Google può usare i contenuti per
migliorare i propri prodotti, cosa incompatibile con l'informativa
dell'app.

Costo indicativo: l'audio conta 32 token al secondo, quindi un'ora sono
115.200 token in ingresso — fra i 4 e i 13 centesimi di dollaro a seconda
del modello scelto.

## Controllo dei costi

1. tetto alla dimensione della richiesta;
2. limite di frequenza per dispositivo (finestra scorrevole);
3. quota giornaliera per dispositivo (richieste, caratteri e secondi di audio);
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

Con `BRIEF_PROVIDER=echo` (predefinito) il servizio funziona senza chiavi
API: i riassunti sono fittizi ma la pipeline è completa, utile per provare
l'app.

## Messa in esercizio

Guida completa, dal server vuoto all'app collegata:
[`docs/DEPLOY.md`](../docs/DEPLOY.md). In sintesi:

- Terminare sempre il TLS davanti al servizio (reverse proxy o piattaforma):
  l'app accetta **solo HTTPS**.
- Montare un volume per `/dati`, altrimenti le quote si azzerano a ogni
  riavvio.
- Prezzi (`PRICE_*_PER_MTOK`) allineati al listino reale del fornitore,
  altrimenti il tetto di spesa stima male.
- Il servizio è a processo singolo con SQLite: per più repliche serve un
  archivio condiviso (Postgres o Redis) al posto di `storage.py`.
