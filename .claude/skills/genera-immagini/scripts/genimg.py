#!/usr/bin/env python3
"""Generazione immagini gratuita: Pollinations (senza chiave o con POLLINATIONS_KEY)
e Cloudflare Workers AI (CF_ACCOUNT_ID + CF_API_TOKEN). Solo libreria standard;
Pillow è opzionale e serve per ritagliare la filigrana e l'inquadratura.

Uso:
  genimg.py "prompt" -o out.jpg [-W 1024 -H 576] [--seed 7] [--provider auto]
  genimg.py --batch prompts.json --outdir imgs/ [--skip-existing] [--jobs 1]

prompts.json: [{"id": "vesuvio", "prompt": "...", "width": 1024, "height": 576,
                "seed": 3, "model": "..."}]   (width/height/seed/model opzionali)
"""
import argparse, base64, concurrent.futures as cf, io, json, os, random, sys, time
import urllib.error, urllib.parse, urllib.request

UA = "genimg/1.0 (+claude-code skill)"
POLL_ANON = "https://image.pollinations.ai/prompt/"
POLL_KEYED = "https://gen.pollinations.ai/image/"
CF_URL = "https://api.cloudflare.com/client/v4/accounts/{acct}/ai/run/{model}"
DEFAULT_MODEL = {
    "pollinations-anon": None,                      # il tier anonimo serve un solo modello
    "pollinations": "tongyi-mai/z-image-turbo",     # economico; per Gemini: google/gemini-2.5-flash-image
    "cloudflare": "@cf/black-forest-labs/flux-1-schnell",
}


def pick_provider(name):
    if name != "auto":
        if name == "pollinations":
            return "pollinations" if os.environ.get("POLLINATIONS_KEY") else "pollinations-anon"
        return name
    if os.environ.get("POLLINATIONS_KEY"):
        return "pollinations"
    if os.environ.get("CF_ACCOUNT_ID") and os.environ.get("CF_API_TOKEN"):
        return "cloudflare"
    return "pollinations-anon"


def http(req, timeout=180):
    with urllib.request.urlopen(req, timeout=timeout) as r:
        return r.read(), r.headers.get("Content-Type", "")


def gen_pollinations(job, provider):
    q = {"width": job["width"], "height": job["height"], "seed": job["seed"]}
    model = job.get("model") or DEFAULT_MODEL[provider]
    if model:
        q["model"] = model
    headers = {"User-Agent": UA}
    if provider == "pollinations":
        base = POLL_KEYED
        headers["Authorization"] = "Bearer " + os.environ["POLLINATIONS_KEY"]
    else:
        base, q["nologo"] = POLL_ANON, "true"
    url = base + urllib.parse.quote(job["prompt"], safe="") + "?" + urllib.parse.urlencode(q)
    data, ctype = http(urllib.request.Request(url, headers=headers))
    if not ctype.startswith("image/"):
        raise RuntimeError(f"risposta non immagine ({ctype}): {data[:200]!r}")
    return data


def gen_cloudflare(job, provider):
    model = job.get("model") or DEFAULT_MODEL["cloudflare"]
    body = {"prompt": job["prompt"], "seed": job["seed"]}
    if "flux" in model:
        body["steps"] = 8
    else:  # SDXL e simili accettano le dimensioni
        body.update(width=job["width"], height=job["height"])
    url = CF_URL.format(acct=os.environ["CF_ACCOUNT_ID"], model=model)
    req = urllib.request.Request(url, data=json.dumps(body).encode(), method="POST", headers={
        "Authorization": "Bearer " + os.environ["CF_API_TOKEN"],
        "Content-Type": "application/json", "User-Agent": UA})
    data, ctype = http(req)
    if ctype.startswith("image/"):
        return data
    res = json.loads(data)
    if not res.get("success", True) or "result" not in res:
        raise RuntimeError(f"Cloudflare: {res.get('errors')}")
    return base64.b64decode(res["result"]["image"])


def postprocess(data, job, provider, out):
    """Toglie la filigrana del tier anonimo (in basso a destra) e riporta l'immagine
    all'aspetto richiesto. Senza Pillow salva i byte così come sono."""
    try:
        from PIL import Image
    except ImportError:
        open(out, "wb").write(data)
        return
    im = Image.open(io.BytesIO(data)).convert("RGB")
    w, h = im.size
    if provider == "pollinations-anon":
        im = im.crop((0, 0, w, int(h * 0.93)))
        w, h = im.size
    want = job["width"] / job["height"]
    if abs(w / h - want) > 0.01:
        if w / h > want:
            nw = int(h * want); im = im.crop(((w - nw) // 2, 0, (w - nw) // 2 + nw, h))
        else:
            nh = int(w / want); im = im.crop((0, (h - nh) // 2, w, (h - nh) // 2 + nh))
    ext = os.path.splitext(out)[1].lower()
    im.save(out, "PNG" if ext == ".png" else "JPEG", quality=94)


def run_job(job, provider, retries=4):
    fn = gen_cloudflare if provider == "cloudflare" else gen_pollinations
    for attempt in range(retries + 1):
        try:
            data = fn(job, provider)
            postprocess(data, job, provider, job["out"])
            if provider == "pollinations-anon":
                time.sleep(2)  # il tier anonimo risponde 402 se le richieste sono troppo ravvicinate
            return job["out"]
        except (urllib.error.URLError, RuntimeError, OSError, ValueError) as e:
            detail = ""
            if isinstance(e, urllib.error.HTTPError):
                detail = e.read()[:300].decode("utf-8", "replace")
                if e.code in (400, 401, 403):  # errori che un nuovo tentativo non risolve
                    raise RuntimeError(f"{job['id']}: HTTP {e.code} {detail}") from e
            if attempt == retries:
                raise RuntimeError(f"{job['id']}: {e} {detail}") from e
            wait = 3 * 2 ** attempt + random.random()
            print(f"  [{job['id']}] tentativo {attempt + 1} fallito ({e}); riprovo tra {wait:.0f}s", file=sys.stderr)
            time.sleep(wait)


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("prompt", nargs="?")
    ap.add_argument("-o", "--out", default="image.jpg")
    ap.add_argument("--batch", help="file JSON con una lista di job")
    ap.add_argument("--outdir", default=".")
    ap.add_argument("-W", "--width", type=int, default=1024)
    ap.add_argument("-H", "--height", type=int, default=1024)
    ap.add_argument("--seed", type=int, default=None)
    ap.add_argument("--model")
    ap.add_argument("--provider", default="auto", choices=["auto", "pollinations", "cloudflare"])
    ap.add_argument("--jobs", type=int, default=None, help="richieste parallele (default 1 anonimo, 3 con chiave)")
    ap.add_argument("--skip-existing", action="store_true")
    a = ap.parse_args()

    provider = pick_provider(a.provider)
    if a.batch:
        raw = json.load(open(a.batch))
        os.makedirs(a.outdir, exist_ok=True)
    elif a.prompt:
        raw = [{"id": os.path.splitext(os.path.basename(a.out))[0], "prompt": a.prompt}]
    else:
        ap.error("serve un prompt oppure --batch")

    jobs = []
    for r in raw:
        j = {"width": a.width, "height": a.height, "model": a.model,
             "seed": a.seed if a.seed is not None else random.randint(1, 2**31 - 1), **r}
        j.setdefault("out", os.path.join(a.outdir, j["id"] + ".jpg") if a.batch else a.out)
        if a.skip_existing and os.path.exists(j["out"]):
            continue
        jobs.append(j)

    print(f"provider: {provider} · immagini da generare: {len(jobs)}", file=sys.stderr)
    workers = a.jobs or (1 if provider == "pollinations-anon" else 3)
    failed = 0
    with cf.ThreadPoolExecutor(workers) as ex:
        futs = {ex.submit(run_job, j, provider): j for j in jobs}
        for f in cf.as_completed(futs):
            try:
                print("ok", f.result())
            except Exception as e:
                failed += 1
                print("ERRORE", e, file=sys.stderr)
    sys.exit(1 if failed else 0)


if __name__ == "__main__":
    main()
