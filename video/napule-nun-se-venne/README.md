# «Lotto unico» – videoclip di *Napule nun se venne*

Napoli messa all'asta su un marketplace inventato (`vendesi.napoli`). I numeri dei lotti sono quelli
della Smorfia; a ogni ritornello cala il timbro **NUN SE VENNE**.

Il video è una pagina HTML animata in modo deterministico (`renderAt(t)`), fotografata fotogramma per
fotogramma con Chromium (Playwright) e montata con ffmpeg sull'audio.

| File | Cosa contiene |
|---|---|
| `scenes.js` | tutte le scene, con i tempi dei versi (nome artista in `ARTIST`) |
| `engine.js` | motore: scene, cursore, timbri, scosse, sottotitoli |
| `analysis/` | tempi dei versi (trascrizione allineata al testo) e beat (95,7 BPM) |
| `prompts.json` | prompt delle immagini, generate con la skill `genera-immagini` |

## Rigenerare

```bash
# immagini (solo quelle mancanti)
python3 ../../.claude/skills/genera-immagini/scripts/genimg.py --batch prompts.json --outdir assets/img --skip-existing
# fotogrammi di prova
NODE_PATH=/opt/node22/lib/node_modules node tools/render.cjs still /tmp/stills 20,67.6,95.7
# video completo (circa 15 minuti con 4 processi)
tools/render_all.sh /percorso/Napule_Nun_Se_Venne.mp3 out/napule_nun_se_venne.mp4 4 30
```

L'mp3 non è nel repository: va passato a `render_all.sh`.
