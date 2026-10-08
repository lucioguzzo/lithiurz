// Renderizza il videoclip con Chromium headless (Playwright) e ffmpeg.
//   node tools/render.cjs still <out_dir> <t1,t2,...>        fotogrammi singoli PNG
//   node tools/render.cjs chunk <out.mp4> <frame_da> <frame_a> [fps]   un pezzo di video (senza audio)
const { chromium } = require('playwright');
const { spawn } = require('child_process');
const path = require('path');
const fs = require('fs');

const ROOT = path.resolve(__dirname, '..');

async function openPage() {
  const browser = await chromium.launch({ args: ['--allow-file-access-from-files', '--font-render-hinting=none', '--disable-gpu'] });
  const page = await browser.newPage({ viewport: { width: 1920, height: 1080 }, deviceScaleFactor: 1 });
  page.on('console', m => { if (m.type() === 'error' || m.type() === 'warning') console.error('[pagina]', m.text()); });
  page.on('pageerror', e => { console.error('[errore pagina]', e.message); process.exitCode = 2; });
  await page.goto('file://' + path.join(ROOT, 'index.html'));
  const n = await page.evaluate(() => window.prepare());
  console.error(`scene caricate: ${n}`);
  return { browser, page };
}

async function main() {
  const [mode, out, a, b, fpsArg] = process.argv.slice(2);
  const { browser, page } = await openPage();
  if (mode === 'still') {
    fs.mkdirSync(out, { recursive: true });
    for (const t of a.split(',').map(Number)) {
      await page.evaluate(t => window.renderAt(t), t);
      await page.screenshot({ path: path.join(out, `f_${t.toFixed(2).padStart(7, '0')}.png`) });
    }
  } else if (mode === 'chunk') {
    const fps = Number(fpsArg || 30), f0 = Number(a), f1 = Number(b);
    const ff = spawn('ffmpeg', ['-hide_banner', '-loglevel', 'error', '-y', '-f', 'image2pipe', '-framerate', String(fps), '-c:v', 'mjpeg', '-i', '-',
      '-c:v', 'libx264', '-preset', 'medium', '-crf', '17', '-pix_fmt', 'yuv420p', '-r', String(fps), out], { stdio: ['pipe', 'inherit', 'inherit'] });
    const t0 = Date.now();
    for (let f = f0; f < f1; f++) {
      await page.evaluate(t => window.renderAt(t), f / fps);
      const buf = await page.screenshot({ type: 'jpeg', quality: 93 });
      if (!ff.stdin.write(buf)) await new Promise(r => ff.stdin.once('drain', r));
      if ((f - f0) % 150 === 0) console.error(`${path.basename(out)}: ${f - f0}/${f1 - f0} (${((Date.now() - t0) / 1000).toFixed(0)}s)`);
    }
    ff.stdin.end();
    await new Promise(r => ff.on('close', r));
  }
  await browser.close();
}
main().catch(e => { console.error(e); process.exit(1); });
