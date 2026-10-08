---
name: genera-immagini
description: Genera immagini da prompt testuali con servizi gratuiti (Pollinations.ai senza chiave, Pollinations con chiave per modelli migliori come Gemini/Flux, Cloudflare Workers AI). Usala ogni volta che un progetto ha bisogno di immagini generate (copertine, scene di un video, illustrazioni, mockup) invece di servizi a pagamento o a crediti.
---

# Genera immagini (gratis)

Script: `scripts/genimg.py` (solo libreria standard Python; Pillow opzionale per i ritagli).

## Scelta del servizio

Lo script sceglie da solo (`--provider auto`), in quest'ordine:

1. **Pollinations con chiave**, se c'è la variabile `POLLINATIONS_KEY` (chiave `sk_...` da enter.pollinations.ai).
   Modello predefinito `tongyi-mai/z-image-turbo`. Per Gemini: `--model google/gemini-2.5-flash-image`.
   Lista modelli: `curl -s https://gen.pollinations.ai/image/models`.
2. **Cloudflare Workers AI**, se ci sono `CF_ACCOUNT_ID` e `CF_API_TOKEN`.
   Modello predefinito `@cf/black-forest-labs/flux-1-schnell` (quadrato, lo script ritaglia all'aspetto richiesto);
   per dimensioni libere `--model @cf/stabilityai/stable-diffusion-xl-base-1.0`. Piano gratuito: 10.000 neuroni al giorno.
3. **Pollinations anonimo** (nessuna chiave): funziona sempre, ma serve un solo modello base (`sana`),
   riduce le immagini a circa 0,6 megapixel e mette una filigrana in basso a destra, che lo script ritaglia.

Le chiavi vanno nelle impostazioni dell'ambiente (variabili d'ambiente o segreti), mai nel codice o in chat.
Non creare account per conto dell'utente: è l'utente a registrarsi e a fornire la chiave.

## Uso

Un'immagine:

```bash
python3 .claude/skills/genera-immagini/scripts/genimg.py "vintage espresso cup, product photo, white background" \
  -o out/caffe.jpg -W 1024 -H 768 --seed 42
```

Più immagini da un file JSON (consigliato per i progetti):

```json
[
  {"id": "vesuvio", "prompt": "Mount Vesuvius at sunset, postcard photo", "width": 1024, "height": 576, "seed": 3},
  {"id": "chitarra", "prompt": "old acoustic guitar with worn strings, dim room", "width": 768, "height": 768}
]
```

```bash
python3 .claude/skills/genera-immagini/scripts/genimg.py --batch prompts.json --outdir out/ --skip-existing
```

`--skip-existing` permette di rilanciare il batch rigenerando solo le immagini mancanti o cancellate.
Per rifare un'immagine venuta male: cancella il file e cambia `seed` (o il prompt), poi rilancia.

## Consigli per i prompt

- Scrivi i prompt in inglese, descrittivi: soggetto, ambientazione, luce, stile ("35mm film photo", "product photo on white background", "cinematic, golden hour").
- Il modello anonimo è debole su mani, volti in primo piano e testo scritto: preferisci oggetti, paesaggi, dettagli, figure lontane o di spalle; aggiungi il testo dopo, in grafica.
- Non chiedere ritratti di persone reali o loghi di marchi esistenti.
- Controlla sempre il risultato guardando l'immagine (strumento Read) prima di usarla.
- Tieni un `seed` fisso per ogni immagine, così il batch è riproducibile.
