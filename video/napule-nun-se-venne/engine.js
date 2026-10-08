// Motore di animazione deterministico: tutto lo stato visivo è funzione del tempo t (secondi).
// Il renderer chiama window.renderAt(t) per ogni fotogramma e ne fa uno screenshot.
'use strict';

const $ = (s, r = document) => r.querySelector(s);
const $$ = (s, r = document) => [...r.querySelectorAll(s)];
const clamp = (x, a = 0, b = 1) => Math.min(b, Math.max(a, x));
const lerp = (a, b, k) => a + (b - a) * k;
const E = {
  lin: k => k,
  in: k => k * k * k,
  out: k => 1 - Math.pow(1 - k, 3),
  inOut: k => (k < 0.5 ? 4 * k * k * k : 1 - Math.pow(-2 * k + 2, 3) / 2),
  back: k => { const c1 = 1.70158, c3 = c1 + 1; return 1 + c3 * Math.pow(k - 1, 3) + c1 * Math.pow(k - 1, 2); },
  expo: k => (k >= 1 ? 1 : 1 - Math.pow(2, -10 * k)),
};
// progresso 0..1 di t nell'intervallo [a,b]
const P = (t, a, b, e = E.lin) => e(clamp((t - a) / (b - a)));
// interpolazione tra chiavi [[t, v], ...]
function kf(t, keys, e = E.inOut) {
  if (t <= keys[0][0]) return keys[0][1];
  for (let i = 1; i < keys.length; i++) {
    if (t <= keys[i][0]) {
      const [t0, v0] = keys[i - 1], [t1, v1] = keys[i];
      return lerp(v0, v1, e((t - t0) / (t1 - t0)));
    }
  }
  return keys[keys.length - 1][1];
}
// apostrofi tipografici per il napoletano
const ap = s => s.replace(/'/g, '’');
const fmt = n => Math.round(n).toString().replace(/\B(?=(\d{3})+(?!\d))/g, '.');
// pseudo-casuale deterministico
const rnd = i => { const x = Math.sin(i * 127.1 + 311.7) * 43758.5453; return x - Math.floor(x); };
// beat più vicino a t (dall'analisi audio)
function snap(t) {
  let best = t, d = 1e9;
  for (const b of BEATS) { const dd = Math.abs(b - t); if (dd < d) { d = dd; best = b; } }
  return best;
}
function beatsBetween(a, b) { return BEATS.filter(x => x >= a && x < b); }
const L = i => LINES[i];

// ---------------------------------------------------------------- registri globali
const SCENES = [];
const IMPACTS = [];   // {t, amp}: scosse di camera
const FLASHES = [];   // {t, dur, peak}
const CURSOR = [];    // {a, b, keys: [[t,x,y]], clicks: [t]}
const SUBS = {};      // indice verso -> 'cine' | 'ui'
const CART = [[0, 0]];
const BELL = [[0, 3]];
const FADE = [[0, 1], [0.9, 0], [261.7, 0], [262.9, 1]];
let CUR = { x: -100, y: -100, on: false };

function addScene(o) {
  const el = document.createElement('div');
  el.className = 'scene ' + (o.kind || 'page');
  el.innerHTML = o.html || '';
  $('#scenes').appendChild(el);
  o.el = el; o._on = false; o.op = 1;
  if (o.init) o.init(el, o);
  SCENES.push(o);
  return o;
}
const impact = (t, amp = 10) => IMPACTS.push({ t, amp });
const flash = (t, dur = 0.45, peak = 0.9) => FLASHES.push({ t, dur, peak });
const cursorTrack = (a, b, keys, clicks = []) => CURSOR.push({ a, b, keys, clicks });
const step = (arr, t) => { let v = arr[0][1]; for (const [tt, vv] of arr) if (t >= tt) v = vv; return v; };

// dissolvenza in entrata/uscita di una scena; restituisce l'opacità
function fadeIO(s, t, fin = 0.3, fout = 0.3) {
  const o = Math.min(fin > 0 ? P(t, s.a, s.a + fin) : 1, fout > 0 ? 1 - P(t, s.b - fout, s.b) : 1);
  s.el.style.opacity = o; s.op = o;
  return o;
}
// "Ken Burns": zoom/pan lento di un'immagine
function kb(img, k, s0, s1, x0 = 0, x1 = 0, y0 = 0, y1 = 0) {
  img.style.transform = `translate(${lerp(x0, x1, k)}px, ${lerp(y0, y1, k)}px) scale(${lerp(s0, s1, k)})`;
}
// animazione del timbro: cala dall'alto e "schiaccia" all'istante ti
function stampAnim(el, t, ti, rot = -10, out = null) {
  const d = 0.11;
  if (t < ti - d) { el.style.opacity = 0; return; }
  let s, o;
  if (t < ti) { const k = (t - (ti - d)) / d; s = lerp(2.3, 1, E.in(k)); o = k; }
  else { s = 1 + 0.035 * Math.exp(-(t - ti) * 18); o = 1; }
  if (out !== null) o *= 1 - P(t, out, out + 0.3);
  el.style.opacity = o;
  el.style.transform = `rotate(${rot}deg) scale(${s})`;
}
function stampHTML(text, size, extra = '', cls = '') {
  return `<div class="stamp ${cls}" style="font-size:${size}px;${extra}">${text}</div>`;
}
// timbro registrato con la sua scossa
function stamp(ti, amp = 12) { impact(ti, amp); return ti; }

// ---------------------------------------------------------------- render
let SUB_IDX = -1;
function renderSubs(t) {
  let idx = -1;
  for (let i = 0; i < LINES.length; i++) {
    if (!SUBS[i]) continue;
    const l = LINES[i], next = LINES[i + 1];
    const end = Math.min(next ? next.a - 0.12 : 1e9, l.b + 1.0);
    if (t >= l.a - 0.15 && t < end) { idx = i; break; }
  }
  const box = $('#subs');
  if (idx !== SUB_IDX) {
    SUB_IDX = idx;
    if (idx < 0) { box.innerHTML = ''; return; }
    const words = ap(LINES[idx].t).split(' ');
    box.innerHTML = `<div class="${SUBS[idx]}">` + words.map((w, i) => `<span class="w">${w}${i < words.length - 1 ? ' ' : ''}</span>`).join('') + '</div>';
  }
  if (idx < 0) return;
  const l = LINES[idx], next = LINES[idx + 1];
  const end = Math.min(next ? next.a - 0.12 : 1e9, l.b + 1.0);
  const ws = $$('.w', box), n = ws.length;
  const outK = 1 - P(t, end - 0.25, end);
  if (SUBS[idx] === 'ui') {
    // didascalia "social": la riga intera compare insieme al box
    const k = P(t, l.a - 0.15, l.a + 0.1, E.out), d = box.firstChild;
    ws.forEach(w => { w.style.opacity = 1; w.style.transform = 'none'; });
    d.style.opacity = k * outK; d.style.transform = `scale(${lerp(0.9, 1, E.back(k))})`;
    return;
  }
  ws.forEach((w, i) => {
    const ti = l.a - 0.1 + (l.b - l.a) * 0.92 * (i / n);
    const k = P(t, ti, ti + 0.2, E.out);
    w.style.opacity = k * outK;
    w.style.transform = `translateY(${(1 - k) * 14}px)`;
  });
}

function renderCursor(t) {
  const c = $('#cursor'), rp = $('#ripple');
  const tr = CURSOR.find(k => t >= k.a && t < k.b);
  if (!tr) { c.style.display = 'none'; rp.style.opacity = 0; CUR.on = false; return; }
  const x = kf(t, tr.keys.map(k => [k[0], k[1]])), y = kf(t, tr.keys.map(k => [k[0], k[2]]));
  CUR = { x, y, on: true };
  let s = 1, last = null;
  for (const ct of tr.clicks) {
    if (t >= ct - 0.06 && t < ct + 0.2) s = Math.min(s, 1 - 0.2 * (t < ct ? (t - ct + 0.06) / 0.06 : 1 - (t - ct) / 0.2));
    if (t >= ct && t < ct + 0.5) last = ct;
  }
  const appear = Math.min(P(t, tr.a, tr.a + 0.2), 1 - P(t, tr.b - 0.2, tr.b));
  c.style.display = 'block';
  c.style.opacity = appear;
  c.style.transform = `translate(${x - 4}px, ${y - 4}px) scale(${s})`;
  if (last !== null) {
    const k = (t - last) / 0.5;
    rp.style.opacity = 0.9 * (1 - k);
    rp.style.left = x + 'px'; rp.style.top = y + 'px';
    rp.style.transform = `scale(${0.2 + 0.8 * E.out(k)})`;
  } else rp.style.opacity = 0;
}

function renderAt(t) {
  renderCursor(t);
  let top = null, vig = 0;
  for (const s of SCENES) {
    const on = t >= s.a && t < s.b;
    if (on !== s._on) { s.el.style.display = on ? 'block' : 'none'; s._on = on; }
    if (!on) continue;
    s.op = 1;
    if (s.update) s.update(t, t - s.a, s.el, s);
    if ((s.kind || 'page') === 'page') top = s;
    if (s.vig) vig = Math.max(vig, s.op);
  }
  // browser e header del sito
  const br = $('#browser');
  if (top) {
    br.style.display = 'block';
    $('#urlpath').textContent = top.url || '/asta';
    $('#tabtitle').textContent = top.tab || 'vendesi.napoli · Asta NAPOLI';
    const lt = t - top.a;
    const lb = $('#loadbar');
    lb.style.width = (P(lt, 0, 0.45, E.out) * 100) + '%';
    lb.style.opacity = 1 - P(lt, 0.45, 0.65);
    const st = top.search ? top.search(t) : null;
    $('#searchtext').textContent = st === null ? 'Cerca: pizza, Vesuvio, tradizione…' : st;
    $('#searchtext').style.color = st === null ? '#9ca3af' : '#111';
    $('#search').style.borderColor = st === null ? '#d1d5db' : 'var(--accent)';
    const cart = step(CART, t), bell = step(BELL, t);
    $('#cart').textContent = cart; $('#cart').style.display = cart > 0 ? 'flex' : 'none';
    $('#bell').textContent = bell;
  } else br.style.display = 'none';

  // camera: scosse dei timbri
  let dx = 0, dy = 0, rz = 0;
  for (const im of IMPACTS) {
    const d = t - im.t;
    if (d < 0 || d > 0.6) continue;
    const env = im.amp * Math.exp(-d * 9);
    dx += env * Math.sin(d * 63 + im.t * 7);
    dy += env * Math.cos(d * 71 + im.t * 3);
    rz += env * 0.02 * Math.sin(d * 50);
  }
  $('#camera').style.transform = `translate(${dx}px, ${dy}px) rotate(${rz}deg)`;

  let fl = 0;
  for (const f of FLASHES) { const d = t - f.t; if (d >= 0 && d < f.dur) fl = Math.max(fl, f.peak * Math.pow(1 - d / f.dur, 2)); }
  $('#flash').style.opacity = fl;
  $('#vignette').style.opacity = vig;
  const fr = Math.floor(t * 30);
  $('#grain').style.transform = `translate(${Math.floor(rnd(fr) * 100)}px, ${Math.floor(rnd(fr + 0.5) * 100)}px)`;
  $('#fade').style.opacity = kf(t, FADE, E.lin);
  renderSubs(t);
}
window.renderAt = renderAt;

// attende font e immagini prima del primo fotogramma
// risolve i bersagli del cursore espressi come elementi DOM ({scene, sel, at, dx, dy})
function resolveCursorTargets() {
  const cam = $('#camera'); const saved = cam.style.transform; cam.style.transform = 'none';
  for (const tr of CURSOR) {
    for (const k of tr.keys) {
      if (typeof k[1] !== 'object') continue;
      const { scene, sel, at, dx = 0, dy = 0 } = k[1];
      const prev = scene.el.style.display;
      scene.el.style.display = 'block';
      if (scene.update) scene.update(at ?? scene.a + 0.8, (at ?? scene.a + 0.8) - scene.a, scene.el, scene);
      const el = $(sel, scene.el);
      if (!el) throw new Error('cursore: selettore non trovato ' + sel);
      const r = el.getBoundingClientRect();
      k[1] = r.x + r.width / 2 + dx; k[2] = r.y + r.height / 2 + dy;
      scene.el.style.display = prev; scene._on = false;
    }
  }
  cam.style.transform = saved;
}

window.prepare = async () => {
  const fams = ['40px "Bebas"', '40px "Stamp"', '40px "Type"', '40px "Receipt"', '700 40px "Receipt"', '40px "Serif"', '700 40px "Serif"', 'italic 40px "Serif"', '800 40px "Inter"'];
  for (const f of fams) await document.fonts.load(f);
  for (const src of ['assets/fx/grain.png', 'assets/fx/grunge.png']) {
    const im = new Image(); im.src = src; await im.decode().catch(() => {});
  }
  await Promise.all($$('img').map(i => i.decode().catch(() => console.warn('img', i.src))));
  await document.fonts.ready;
  resolveCursorTargets();
  for (const sc of SCENES) { sc.el.style.display = 'none'; sc._on = false; }
  return SCENES.length;
};
