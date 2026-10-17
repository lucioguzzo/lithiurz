# Mettere online il backend

Il backend sta in `backend/`, è un'immagine Docker e non ha bisogno di
niente di speciale: un processo Python, un file SQLite di contatori e il
TLS davanti. Quello che segue è il percorso completo, dal server vuoto
all'app che trascrive nel cloud.

Serve perché l'app possa usare **il riassunto cloud** e **la trascrizione
cloud**. Senza backend l'app funziona lo stesso: Whisper sul telefono e
riassunto locale.

## Quale piattaforma

| | Costo | Il database dei contatori | Manutenzione |
|---|---|---|---|
| **VPS piccolo** (Hetzner, Scaleway, OVH…) | ~4 €/mese | SQLite su disco: funziona come progettato | aggiornamenti del sistema, a carico tuo |
| **Google Cloud Run** | gratis entro il piano gratuito: 2 mln di richieste, 180.000 vCPU-secondi e 360.000 GiB-secondi al mese ([listino](https://cloud.google.com/run/pricing)) | **problema**: vedi sotto | nessuna |
| **Fly.io** | ~2-3 $/mese (i nuovi account non hanno più un piano gratuito) | volume persistente: funziona | poca |

**Il problema di Cloud Run.** Il filesystem del contenitore è effimero e
le istanze nascono e muoiono da sole. I contatori di quota e spesa
finirebbero azzerati a ogni riavvio, e un tetto di spesa che si azzera non
è un tetto. Montare un bucket non risolve: la documentazione di Google
avverte che Cloud Storage FUSE **non ha il controllo di concorrenza sulle
scritture** (vince l'ultima), ed è esattamente ciò su cui SQLite conta.
Due vie d'uscita, entrambe praticabili:

1. `--max-instances=1` e accettare che i contatori ripartano da zero a
   ogni riavvio del contenitore. Accettabile solo se metti anche un
   avviso di budget su Google Cloud, che è il vero paracadute.
2. Sostituire lo store SQLite con Firestore (~150 righe più i test).
   Chiedimelo e lo scrivo: il resto del servizio non cambia di una riga,
   `UsageStore` è già isolato.

**Consiglio**: per una sola app con pochi utenti, il VPS. Costa quanto un
caffè, il codice gira esattamente come è stato scritto e provato, e non
devi fidarti di nessun comportamento implicito della piattaforma.

---

## Percorso A — VPS (consigliato)

### 1. Server e nome di dominio

Prendi la macchina più piccola che trovi: 1 vCPU e 2 GB di RAM sono
abbondanti, qui non gira nessun modello — il lavoro pesante lo fa Google.

Fai puntare un nome al suo indirizzo IP, con un record `A`:

```
api.tuodominio.it.   A   203.0.113.10
```

Non hai un dominio? Va bene un sottodominio gratuito (DuckDNS e simili):
serve solo che risolva pubblicamente, perché è così che il certificato
viene emesso. **Un indirizzo IP nudo non basta**: l'app accetta solo
HTTPS, e per HTTPS serve un nome.

### 2. Preparare la macchina

```bash
ssh root@203.0.113.10

# Utente non privilegiato
adduser brief && usermod -aG sudo brief

# Firewall: solo SSH e web
ufw allow 22,80,443/tcp && ufw enable

# Docker
curl -fsSL https://get.docker.com | sh
usermod -aG docker brief
```

### 3. Configurare il servizio

```bash
su - brief
git clone https://github.com/lucioguzzo/lithiurz.git
cd lithiurz/backend
cp .env.example .env
nano .env
```

Le voci che contano davvero:

```bash
# Trascrizione nel cloud
BRIEF_TRANSCRIPTION_PROVIDER=gemini
BRIEF_GEMINI_API_KEY=…          # progetto con FATTURAZIONE ATTIVA (vedi sotto)
BRIEF_GEMINI_MODEL=gemini-3.8-flash

# Riassunto cloud (facoltativo: senza, l'app usa quello locale)
BRIEF_PROVIDER=echo             # oppure anthropic / openai, con la sua chiave

# Soldi: questo è il freno a mano
BRIEF_DAILY_BUDGET_USD=2.0
BRIEF_PRICE_AUDIO_INPUT_PER_MTOK=0.75   # allinealo al modello scelto

# Quote per dispositivo
BRIEF_DAILY_AUDIO_SECONDS_PER_DEVICE=3600
BRIEF_DAILY_REQUESTS_PER_DEVICE=20

# Accessi
BRIEF_APP_TOKEN=                # facoltativo, screma il traffico casuale
BRIEF_ADMIN_TOKEN=$(openssl rand -hex 24)   # questo è un segreto vero
```

### 4. Accendere

```bash
BRIEF_DOMINIO=api.tuodominio.it \
  docker compose -f deploy/docker-compose.yml up -d --build
```

Due contenitori: il servizio, che non espone nessuna porta all'esterno, e
Caddy, che prende il certificato da solo al primo avvio e inoltra le
richieste. Dopo una ventina di secondi:

```bash
curl https://api.tuodominio.it/health
# {"status":"ok"}

curl https://api.tuodominio.it/v1/status -H "x-device-id: prova-da-riga-di-comando"
# deve dire transcription_provider: gemini
```

Se `/health` risponde ma il certificato non c'è, guarda `docker compose
-f deploy/docker-compose.yml logs proxy`: nove volte su dieci è il DNS
che non punta ancora alla macchina, o la porta 80 chiusa.

### 5. Collegare l'app

Nell'app: **Impostazioni → Server dei riassunti**, incolla
`https://api.tuodominio.it`. Da quel momento l'interruttore
**Trascrizione nel cloud** diventa attivabile; accendendolo l'app chiede
il consenso e poi usa il server.

### 6. Vivere con il servizio

```bash
# Aggiornare dopo nuove modifiche
git pull && BRIEF_DOMINIO=api.tuodominio.it \
  docker compose -f deploy/docker-compose.yml up -d --build

# Guardare cosa succede (solo numeri, mai testo)
docker compose -f deploy/docker-compose.yml logs -f brief

# Spegnere il cloud all'istante, senza fermare il servizio
curl -X POST "https://api.tuodominio.it/admin/kill-switch?enabled=false" \
  -H "x-admin-token: …"

# Copia dei contatori (sono solo numeri: nessun testo, nessun audio)
docker run --rm -v deploy_dati:/d -v "$PWD":/b alpine cp /d/usage.db /b/
```

---

## Percorso B — Cloud Run

Conviene se non vuoi amministrare niente, e se accetti il compromesso sui
contatori descritto sopra. Il vantaggio secondario non è piccolo: la
chiave Gemini e il servizio stanno nello stesso progetto Google, quindi
fatturazione e avvisi di spesa sono un posto solo.

```bash
gcloud config set project IL-TUO-PROGETTO

# La chiave non va in una variabile in chiaro
echo -n "LA-TUA-CHIAVE-GEMINI" | \
  gcloud secrets create brief-gemini-key --data-file=-

gcloud run deploy brief-backend \
  --source backend \
  --region europe-west1 \
  --allow-unauthenticated \
  --max-instances 1 \
  --memory 512Mi \
  --timeout 300 \
  --set-env-vars BRIEF_TRANSCRIPTION_PROVIDER=gemini,BRIEF_GEMINI_MODEL=gemini-3.8-flash,BRIEF_DAILY_BUDGET_USD=2.0,BRIEF_DATABASE_PATH=/tmp/usage.db \
  --set-secrets BRIEF_GEMINI_API_KEY=brief-gemini-key:latest
```

Note che non sono dettagli:

- `--allow-unauthenticated` serve perché l'app non ha credenziali Google.
  A schermare il traffico ci pensa `BRIEF_APP_TOKEN`, da impostare.
- `--max-instances 1` tiene i contatori in un posto solo. Senza, due
  istanze contano per conto proprio e le quote diventano il doppio.
- `BRIEF_DATABASE_PATH=/tmp/usage.db` perché il resto del filesystem è in
  sola lettura. `/tmp` sta in memoria e **sparisce a ogni riavvio**: è il
  compromesso di cui sopra.
- `--timeout 300` perché una trascrizione lunga non deve essere troncata
  dalla piattaforma.
- Il `Dockerfile` legge `$PORT`, che Cloud Run impone: funziona senza
  modifiche.

L'URL che `gcloud` stampa alla fine è quello da incollare nelle
impostazioni dell'app.

---

## Prima di dire che è fatto

1. **La prima chiamata vera a Gemini.** Da qui non è mai partita: non
   c'era una chiave. Trascrivi un audio breve e controlla che il testo
   arrivi, che sia solo il parlato (niente preamboli del modello) e che
   `estimated_cost_usd` nello stato sia plausibile.
2. **Avviso di spesa su Google Cloud**, a una cifra che ti fa dormire. Il
   tetto del backend è una stima basata sul listino che gli hai scritto
   tu: l'avviso di Google è la misura vera.
3. **Chiave di un progetto con fatturazione attiva.** I termini dell'API
   Gemini ammettono solo servizi a pagamento per gli utenti di SEE,
   Svizzera e Regno Unito, e sul piano gratuito Google può usare i
   contenuti per migliorare i propri prodotti — incompatibile con
   l'informativa dell'app.
4. **Guarda i log una volta** e verifica con i tuoi occhi che non
   contengano né trascrizioni né audio: devono esserci solo durata,
   dimensione e costo.
5. **Scheda "Sicurezza dei dati" di Play** da aggiornare prima di
   pubblicare: ora l'app può condividere registrazioni audio, se l'utente
   lo attiva.

## Cosa non è stato provato da qui

L'immagine Docker **non è mai stata costruita né eseguita** in questo
ambiente: il demone Docker non c'è. Il `Dockerfile` e il `docker-compose`
sono scritti e il compose è stato validato (`docker compose config`), ma
il primo `docker build` vero lo farai tu sul server. Il servizio in sé,
invece, è stato avviato e interrogato per davvero con `uvicorn`, con una
richiesta multipart completa.
