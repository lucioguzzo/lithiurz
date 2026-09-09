#!/usr/bin/env python3
"""Carica sifu.glb nel motore che usa davvero l'app e ne verifica l'esito.

    python3 tools/verify_in_engine.py

Gli altri controlli guardano il modello dall'interno: le pose con la mia
matematica, il file con il mio parser. Questo lo guarda da fuori, con lo
stesso `model-viewer` che gira nella WebView dell'app, e risponde alle due
domande che contano davvero:

  1. il modello carica? (se l'evento non arriva, in app resta a caricare)
  2. l'inquadratura contiene la figura, o la taglia?

Sono due difetti che nessun test interno puo' vedere, e li ho scoperti solo
guardando l'app su un telefono. Il campo visivo di model-viewer e' di 30
gradi verticali, non 45: i raggi calcolati a mente erano tutti troppo corti.

Richiede playwright e Chromium; salta silenziosamente se mancano.
"""

import http.server
import json
import os
import shutil
import socketserver
import sys
import tempfile
import threading

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
MODEL = os.path.join(ROOT, "assets", "models", "sifu.glb")
LESSONS = os.path.join(ROOT, "assets", "data", "lessons.json")

PAGE = """<!doctype html><html><head><meta charset="utf-8">
<style>html,body{margin:0;height:100%;background:#14171f}
model-viewer{width:100vw;height:100vh;--poster-color:transparent}</style>
<script type="module" src="model_viewer.min.js"></script></head>
<body><model-viewer id="mv" src="sifu.glb" camera-controls
  interaction-prompt="none" shadow-intensity="0"></model-viewer></body></html>
"""

# quanto margine minimo, in pixel, fra la figura e il bordo del riquadro
MIN_MARGIN = 8
# lezioni la cui inquadratura taglia di proposito: il soggetto sono le mani
CLOSE_UPS = {"huen_sau"}


def find_viewer_js():
    for base in (os.path.expanduser("~/.pub-cache/hosted/pub.dev"),
                 os.path.expanduser("~/.pub-cache/hosted/pub.flutter-io.cn")):
        if not os.path.isdir(base):
            continue
        for name in sorted(os.listdir(base), reverse=True):
            if name.startswith("flutter_3d_controller-"):
                js = os.path.join(base, name, "assets", "model_viewer.min.js")
                if os.path.exists(js):
                    return js
    return None


def serve(directory):
    handler = lambda *a, **k: http.server.SimpleHTTPRequestHandler(
        *a, directory=directory, **k)
    httpd = socketserver.TCPServer(("127.0.0.1", 0), handler)
    threading.Thread(target=httpd.serve_forever, daemon=True).start()
    return httpd, httpd.server_address[1]


def chromium_path():
    base = os.environ.get("PLAYWRIGHT_BROWSERS_PATH", "/opt/pw-browsers")
    if os.path.isdir(base):
        for name in sorted(os.listdir(base), reverse=True):
            if name.startswith("chromium-"):
                exe = os.path.join(base, name, "chrome-linux", "chrome")
                if os.path.exists(exe):
                    return exe
    return None


def main():
    try:
        from playwright.sync_api import sync_playwright
    except ImportError:
        print("playwright non disponibile: verifica nel motore saltata")
        return 0

    js = find_viewer_js()
    if not js:
        print("model_viewer.min.js non trovato (manca flutter pub get?): saltata")
        return 0

    work = tempfile.mkdtemp(prefix="sifu-engine-")
    try:
        shutil.copy(js, work)
        shutil.copy(MODEL, work)
        with open(os.path.join(work, "index.html"), "w") as f:
            f.write(PAGE)
        httpd, port = serve(work)

        lessons = json.load(open(LESSONS, encoding="utf-8"))
        failures = []

        with sync_playwright() as pw:
            exe = chromium_path()
            browser = pw.chromium.launch(
                executable_path=exe,
                args=["--use-gl=angle", "--use-angle=swiftshader",
                      "--enable-unsafe-swiftshader", "--no-sandbox"])
            # stesso rapporto d'aspetto del riquadro nell'app
            page = browser.new_page(viewport={"width": 460, "height": 535})
            page.goto(f"http://127.0.0.1:{port}/index.html")

            loaded = page.evaluate("""async () => {
                const mv = document.getElementById('mv');
                if (mv.loaded) return 'ok';
                return await new Promise(r => {
                    mv.addEventListener('load', () => r('ok'), {once: true});
                    mv.addEventListener('error', e => r('errore: ' + e.detail),
                                        {once: true});
                    setTimeout(() => r('scaduto'), 60000);
                });
            }""")
            print(f"caricamento nel motore: {loaded}")
            if loaded != "ok":
                print("Il modello non carica in model-viewer.")
                return 1

            names = set(page.evaluate(
                "document.getElementById('mv').availableAnimations"))
            print(f"animazioni riconosciute: {len(names)}")
            for l in lessons:
                if l["id"] not in names:
                    failures.append(f"{l['id']}: animazione assente nel motore")

            for l in lessons:
                if l["id"] in CLOSE_UPS:
                    continue
                cam = l["camera"]
                page.evaluate(f"""() => {{
                    const mv = document.getElementById('mv');
                    mv.animationName = '{l["id"]}';
                    mv.pause();
                    mv.currentTime = {min(l['duration'] * 0.45, 3.0)};
                    mv.cameraTarget = '0m {cam["targetY"]}m {cam["targetZ"]}m';
                    mv.cameraOrbit =
                        '{cam["theta"]}deg {cam["phi"]}deg {cam["radius"]}m';
                    mv.jumpCameraToGoal();
                }}""")
                page.wait_for_timeout(220)
                margin = page.evaluate("""() => {
                    const mv = document.getElementById('mv');
                    const c = mv.shadowRoot.querySelector('canvas');
                    const w = c.width, h = c.height;
                    const g = document.createElement('canvas');
                    g.width = w; g.height = h;
                    const x = g.getContext('2d');
                    x.drawImage(c, 0, 0);
                    const d = x.getImageData(0, 0, w, h).data;
                    let top = h, bot = -1, left = w, right = -1;
                    for (let y = 0; y < h; y += 2)
                      for (let px = 0; px < w; px += 2) {
                        if (d[(y * w + px) * 4 + 3] > 24) {
                          if (y < top) top = y;
                          if (y > bot) bot = y;
                          if (px < left) left = px;
                          if (px > right) right = px;
                        }
                      }
                    if (bot < 0) return null;
                    return {margin: Math.min(top, h - 1 - bot,
                                             left, w - 1 - right),
                            fill: (bot - top) / h,
                            scale: w / mv.clientWidth};
                }""")
                if margin is None:
                    failures.append(f"{l['id']}: nulla di visibile")
                    continue
                px = margin["margin"] / max(1.0, margin["scale"])
                if px < MIN_MARGIN:
                    failures.append(
                        f"{l['id']}: la figura tocca il bordo "
                        f"(margine {px:.0f}px, raggio {cam['radius']} m)")

            browser.close()
        httpd.shutdown()

        if failures:
            print(f"\n{len(failures)} problemi:")
            for f in failures:
                print(f"  - {f}")
            return 1
        print(f"inquadrature verificate: {len(lessons) - len(CLOSE_UPS)} "
              f"({len(CLOSE_UPS)} primi piani esclusi di proposito)")
        print("Tutto a posto nel motore reale.")
        return 0
    finally:
        shutil.rmtree(work, ignore_errors=True)


if __name__ == "__main__":
    sys.exit(main())
