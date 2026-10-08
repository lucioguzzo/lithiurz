// «LOTTO UNICO» – videoclip di "Napule nun se venne".
// Napoli messa all'asta su un marketplace inventato (vendesi.napoli); i numeri dei lotti sono quelli della Smorfia.
'use strict';

const ARTIST = 'lucioguzzo';
const IMG = n => `assets/img/${n}.jpg`;
const PAGE_Y = 172; // altezza di browser + header: le scene "page" partono da qui

const LOT = {
  vesuvio: { n: 1, sm: "ll'Italia", img: 'lot_vesuvio', t: 'Tramonto sul Vesuvio – drone 4K', p: 18500 },
  panni: { n: 70, sm: "'o palazzo", img: 'lot_panni', t: 'Panni stesi autentici (vicolo incluso)', p: 2400 },
  vespa: { n: 20, sm: "'a festa", img: 'lot_vespa', t: "Vespa d'epoca + lungomare", p: 7900 },
  pizza: { n: 82, sm: "'a tavula 'mbandita", img: 'lot_pizza', t: 'Pizza con vista (vista non garantita)', p: 390 },
  globe: { n: 58, sm: "'o paccotto", img: 'lot_snowglobe', t: 'Vesuvio in palla di vetro', p: 129 },
  caffe: { n: 42, sm: "'o ccafè", img: 'lot_caffe', t: 'Caffè sospeso (da asporto)', p: 95 },
  balcone: { n: 43, sm: "'onna pereta fore 'o barcone", img: 'lot_balcone', t: 'Balcone vista vicolo – nonna inclusa', p: 5600 },
  chitarra: { n: 55, sm: "'a museca", img: 'lot_chitarra', t: "Chitarra cu 'e corde consumate", p: 1250 },
  voce: { n: 80, sm: "'a vocca", img: 'lot_voce', t: 'Voce rauca, usata di notte', p: 640 },
  vicolo: { n: 65, sm: "'o chianto", img: 'lot_vicolo', t: "Blues d''e vicoli", p: 3100 },
  moda: { n: 64, sm: "'a sciammeria", img: 'lot_moda', t: 'Moda di stagione', p: 210 },
  maschera: { n: 75, sm: 'Pullecenella', img: 'lot_maschera', t: 'Maschera (per non farsi riconoscere)', p: 75 },
};
const GRID8 = ['vesuvio', 'panni', 'vespa', 'pizza', 'globe', 'caffe', 'balcone', 'chitarra'];

// ---------------------------------------------------------------- mattoncini HTML
function cardHTML(L, x, y, w = 400, ih = 250, cls = '') {
  return `<div class="card ${cls}" style="left:${x}px;top:${y}px;width:${w}px">
    <div class="ci" style="height:${ih}px"><img src="${IMG(L.img)}"></div>
    <div class="cb"><div class="cl">LOTTO ${L.n} <span class="smorfia">${ap(L.sm)}</span></div>
    <div class="ct">${ap(L.t)}</div>
    <div class="cp"><b>€ <span class="cpv">${fmt(L.p)}</span></b><span>⏱ <span class="ctm">04:12</span></span></div></div></div>`;
}
// griglia compatta 4x2 usata in più scene
const gridPos = i => ({ x: 100 + (i % 4) * 440, y: 100 + Math.floor(i / 4) * 340 });
function grid8HTML(keys = GRID8) {
  return keys.map((k, i) => { const p = gridPos(i); return cardHTML(LOT[k], p.x, p.y, 400, 200); }).join('');
}
// timbro centrato su un punto (coordinate della scena)
function stampAt(text, size, cx, cy, cls = '', id = '') {
  return `<div style="position:absolute;left:${cx}px;top:${cy}px;width:0;height:0;display:flex;align-items:center;justify-content:center;z-index:30">
    <div class="stamp ${cls} ${id}" style="position:relative;flex:none;font-size:${size}px">${text}</div></div>`;
}
function toastHTML(text, x, y, cls = '') {
  return `<div class="toast ${cls}" style="left:${x}px;top:${y}px;opacity:0">${text}</div>`;
}
function popIn(el, t, ti, dur = 0.3) {
  const k = P(t, ti, ti + dur);
  el.style.opacity = k;
  el.style.transform = `scale(${lerp(0.7, 1, E.back(k))})`;
}
function slideToast(el, t, ti, to) {
  const k = Math.min(P(t, ti, ti + 0.35, E.out), 1 - P(t, to, to + 0.3));
  el.style.opacity = k;
  el.style.transform = `translateX(${(1 - k) * 60}px)`;
}

// ---------------------------------------------------------------- scheda d'asta generica
function listing(a, b, o) {
  return addScene({
    a, b, kind: 'page', url: o.url || `/asta/lotto-${o.lot.n}`,
    html: `
      <div class="crumbs">Aste › Napoli › ${o.cat || 'Tradizione'} › <b style="color:#111">Lotto ${o.lot.n}</b></div>
      <div class="lst-img"><img class="i1" src="${IMG(o.lot.img)}">${o.img2 ? `<img class="i2" src="${IMG(o.img2)}" style="opacity:0">` : ''}
        <div class="live"><span class="dot"></span>ASTA LIVE</div>${o.imgHTML || ''}</div>
      <div class="lst-info">
        <div class="lotrow"><span class="lotpill">LOTTO ${o.lot.n}</span><span class="smorfia">${ap(o.lot.sm)}</span></div>
        <h1 class="lst-title">${ap(o.title)}</h1>
        <div class="seller">Venditore: <b>Napoli S.r.l.</b> <span class="stars">★★★★☆</span> <span class="muted">(12.304 recensioni)</span></div>
        <div class="cond">Condizioni: <b>${ap(o.cond)}</b></div>
        <div class="pricebox">
          <div class="muted small">Offerta attuale</div>
          <div class="price">€ <span class="pv"></span></div>
          <div class="muted small"><span class="bids"></span> offerte · termina tra <b class="tm" style="color:#d7261e"></b></div>
          <div class="btns"><div class="btn primary">FAI UN'OFFERTA</div><div class="btn ghost">Compra subito € ${o.buy}</div></div>
        </div>
        <div class="watch"><span class="live" style="background:#fee2e2;color:#b91c1c;padding:5px 10px"><span class="dot"></span>LIVE</span> <b class="wv"></b> persone lo stanno guardando</div>
      </div>
      ${o.extraHTML || ''}`,
    update(t, lt, el, s) {
      fadeIO(s, t, 0.22, 0);
      el.style.transform = `translateX(${(1 - P(lt, 0, 0.3, E.out)) * 40}px)`;
      kb($('.i1', el), P(t, a, b), 1.04, 1.16, 0, -24, 0, -10);
      if (o.img2) { const i2 = $('.i2', el); i2.style.opacity = P(t, o.img2At, o.img2At + 0.5); kb(i2, P(t, o.img2At, b + 1), 1.05, 1.15, 0, 24, 0, 0); }
      const per = 0.62, n = Math.floor(lt / per), N = Math.max(1, Math.floor((b - a) / per));
      $('.pv', el).textContent = o.priceText ? o.priceText(t, n) : fmt(lerp(o.p0, o.p1, Math.min(1, n / N)));
      const pop = n > 0 ? 1 - clamp(((lt % per) / per) * 4) : 0;
      $('.price', el).style.transform = `scale(${1 + 0.06 * pop})`;
      $('.price', el).style.color = pop > 0.3 ? '#0a5cff' : '#14161a';
      $('.bids', el).textContent = (o.bids0 || 17) + n;
      const rem = Math.max(0, 299 - Math.floor(lt) - (o.tOff || 0));
      $('.tm', el).textContent = `00:0${Math.floor(rem / 60)}:${String(rem % 60).padStart(2, '0')}`;
      $('.wv', el).textContent = fmt((o.w0 || 1204) + Math.floor(rnd(Math.floor(lt * 3) + a) * 40) + n * 13);
      if (o.extra) o.extra(t, lt, el, s);
    },
  });
}

// immagine a tutto schermo con lento movimento di camera
function cine(a, b, img, o = {}) {
  return addScene({
    a, b, kind: 'full', vig: o.vig !== false,
    html: `<img class="bgimg" src="${IMG(img)}" style="${o.filter ? 'filter:' + o.filter : ''}">${o.extraHTML || ''}`,
    update(t, lt, el, s) {
      fadeIO(s, t, o.fin ?? 0.4, o.fout ?? 0.4);
      kb($('.bgimg', el), P(t, a, b), o.s0 ?? 1.05, o.s1 ?? 1.16, o.x0 ?? 0, o.x1 ?? -30, o.y0 ?? 0, o.y1 ?? -12);
      if (o.extra) o.extra(t, lt, el, s);
    },
  });
}

// pagina asta finale (stessa impaginazione in due momenti)
function auctionHTML() {
  return `
    <div class="crumbs">Aste › <b style="color:#111">Asta finale</b></div>
    <div class="lst-img"><img class="i1" src="${IMG('real_gulf')}"><div class="live"><span class="dot"></span>ASTA LIVE</div></div>
    <div class="lst-info">
      <div class="lotrow"><span class="lotpill" style="background:#d7261e">LOTTO UNICO</span><span class="smorfia">90 · ’a paura</span></div>
      <h1 class="lst-title" style="font-size:58px">Napoli<br>(tutta intera)</h1>
      <div class="pricebox" style="margin-top:6px">
        <div class="muted small">Offerta attuale</div>
        <div class="price" style="font-size:64px">€ <span class="pv"></span></div>
        <div class="muted small status"><span class="bids"></span> offerte · termina tra <b class="tm" style="color:#d7261e">00:00:09</b></div>
        <div class="btns"><div class="btn primary offri">OFFRI ANCORA</div><div class="btn ghost">Compra subito: —</div></div>
      </div>
      <div class="feed" style="margin-top:22px;font-size:17px"></div>
    </div>`;
}
const BIDDERS = ['mariuolo_79', 'golfo_invest_spa', 'bnb_holding_srl', 'mariuolo_79', 'vesuvio_capital', 'mariuolo_79'];
function feedHTML(k, price, struck = 0) {
  let h = '';
  for (let i = 0; i < 5; i++) {
    const who = BIDDERS[(k + i) % BIDDERS.length];
    const v = price / Math.pow(1.35, i);
    h += `<div style="display:flex;justify-content:space-between;padding:9px 4px;border-bottom:1px solid #e5e7eb;opacity:${1 - i * 0.15};${i < struck ? 'text-decoration:line-through;color:#b91c1c' : ''}">
      <span><b>${who}</b> ha offerto</span><b>€ ${fmt(v)}</b><span class="muted">${i === 0 ? 'ora' : i + 's fa'}</span></div>`;
  }
  return h;
}

// =====================================================================================
// 0:00 — INTRO: finta pubblicità che si rivela un banner del sito
// =====================================================================================
addScene({
  a: 0, b: 10.35, kind: 'full',
  html: `
    <img class="bgimg" src="${IMG('ad_vesuvio')}" style="object-position:50% 60%">
    <div style="position:absolute;inset:0;background:linear-gradient(100deg,rgba(10,8,20,.72) 0%,rgba(10,8,20,.25) 55%,rgba(0,0,0,0) 100%)"></div>
    <div class="ad-tag" style="position:absolute;left:110px;top:90px;background:rgba(255,255,255,.18);color:#fff;font-weight:800;letter-spacing:3px;font-size:16px;padding:8px 16px;border-radius:6px">SPONSORIZZATO</div>
    <div class="ad-1 serif" style="position:absolute;left:105px;top:200px;color:#fff;font-size:96px;font-weight:600">Vivi la vera</div>
    <div class="ad-2 serif" style="position:absolute;left:100px;top:300px;color:#ffd77a;font-size:190px;font-weight:800;letter-spacing:-3px">NAPOLI™</div>
    <div style="position:absolute;left:110px;top:560px;display:flex;gap:28px;color:#fff;font-weight:800;font-size:52px">
      <span class="ad-w">Autentica.</span><span class="ad-w">Veloce.</span><span class="ad-w">Tua.</span></div>
    <div class="ad-cta" style="position:absolute;left:110px;top:680px;display:flex;gap:22px;align-items:center">
      <div style="background:#fff;color:#111;font-weight:800;font-size:34px;padding:16px 28px;border-radius:14px">da 29,99 €</div>
      <div class="ad-btn" style="background:#0a5cff;color:#fff;font-weight:800;font-size:30px;padding:18px 34px;border-radius:14px">Prenota ora →</div></div>
    <div class="ad-fine" style="position:absolute;left:110px;top:830px;color:rgba(255,255,255,.75);font-size:20px;max-width:1100px;line-height:1.4">
      *Tradizione non inclusa. L’autenticità può variare. Offerta soggetta a disponibilità del Vesuvio.</div>`,
  update(t, lt, el, s) {
    kb($('.bgimg', el), P(t, 0, 9.5), 1.18, 1.03, 30, 0, 10, 0);
    const ins = [[$('.ad-tag', el), 0.7], [$('.ad-1', el), 1.3], [$('.ad-2', el), 1.95]];
    for (const [e, ti] of ins) { const k = P(t, ti, ti + 0.6, E.out); e.style.opacity = k; e.style.transform = `translateY(${(1 - k) * 30}px)`; }
    $$('.ad-w', el).forEach((e, i) => { const k = P(t, 3.15 + i * 0.6, 3.45 + i * 0.6, E.out); e.style.opacity = k; e.style.transform = `scale(${lerp(1.4, 1, k)})`; });
    const kc = P(t, 5.4, 5.9, E.out); $('.ad-cta', el).style.opacity = kc; $('.ad-cta', el).style.transform = `translateY(${(1 - kc) * 40}px)`;
    const bb = beatsBetween(5.9, 9.5).some(x => t >= x && t < x + 0.15);
    $('.ad-btn', el).style.transform = `scale(${bb ? 1.06 : 1})`;
    $('.ad-fine', el).style.opacity = P(t, 6.8, 7.4);
    // si rimpicciolisce fino a diventare il banner in alto nella home del sito
    const k = P(t, 8.9, 10.15, E.inOut);
    el.style.transform = `translate(0px, ${lerp(0, -188, k)}px) scale(${lerp(1, 0.896, k)})`;
    el.style.clipPath = `inset(${372 * k}px 0px ${372 * k}px 0px round ${20 * k}px)`;
    el.style.opacity = 1 - P(t, 9.85, 10.35);
  },
});

// =====================================================================================
// 0:09 — HOME del sito: l'asta "NAPOLI"
// =====================================================================================
const home = addScene({
  a: 9.0, b: 21.0, kind: 'page', url: '/asta/napoli',
  html: `<div class="pg" style="position:absolute;left:0;top:0;width:1920px;height:1400px">
    <div style="position:absolute;left:100px;top:30px;width:1720px;height:300px;border-radius:18px;overflow:hidden">
      <img src="${IMG('ad_vesuvio')}" style="width:100%;height:100%;object-fit:cover;object-position:50% 60%">
      <div style="position:absolute;inset:0;background:linear-gradient(90deg,rgba(10,8,20,.7),rgba(0,0,0,0) 70%)"></div>
      <div style="position:absolute;left:40px;top:34px;color:#fff;font-weight:800;letter-spacing:3px;font-size:13px;background:rgba(255,255,255,.2);padding:6px 12px;border-radius:5px">SPONSORIZZATO</div>
      <div class="serif" style="position:absolute;left:40px;top:84px;color:#fff;font-size:64px;font-weight:700">Vivi la vera <span style="color:#ffd77a">NAPOLI™</span></div>
      <div style="position:absolute;left:40px;top:196px;display:flex;gap:14px"><div style="background:#fff;font-weight:800;font-size:22px;padding:12px 20px;border-radius:10px">da 29,99 €</div><div style="background:#0a5cff;color:#fff;font-weight:800;font-size:20px;padding:13px 22px;border-radius:10px">Prenota ora →</div></div>
    </div>
    <div style="position:absolute;left:100px;top:358px;display:flex;align-items:center;gap:20px">
      <span class="live" style="background:#fee2e2;color:#b91c1c"><span class="dot"></span>IN CORSO</span>
      <span style="font-size:34px;font-weight:800;letter-spacing:-.5px">Asta: NAPOLI</span>
      <span class="muted" style="font-size:20px">90 lotti · termina tra <b class="hometm" style="color:#d7261e">00:12:34</b></span></div>
    ${GRID8.map((k, i) => cardHTML(LOT[k], 100 + (i % 4) * 440, 420 + Math.floor(i / 4) * 400)).join('')}
  </div>
  ${toastHTML('🔔 <b>mariuolo_79</b> ha offerto € 18.500 sul Lotto 1', 1250, 24, 'h-t1')}
  ${toastHTML('🔔 Nuova offerta sul Lotto 43 · ’onna pereta fore ’o barcone', 1180, 24, 'h-t2')}`,
  update(t, lt, el, s) {
    s.op = 1; el.style.opacity = 1;
    const sc = kf(t, [[12.8, 0], [17.4, 330]]);
    $('.pg', el).style.transform = `translateY(${-sc}px)`;
    const rem = 754 - Math.floor(lt);
    $('.hometm', el).textContent = `00:${String(Math.floor(rem / 60)).padStart(2, '0')}:${String(rem % 60).padStart(2, '0')}`;
    $$('.card', el).forEach((c, i) => {
      const x = 100 + (i % 4) * 440, y = PAGE_Y + 420 + Math.floor(i / 4) * 400 - sc;
      const hov = CUR.on && CUR.x > x && CUR.x < x + 400 && CUR.y > y && CUR.y < y + 370;
      const press = i === 7 && t > 19.55 && t < 19.75;
      c.style.transform = `translateY(${hov ? -8 : 0}px) scale(${press ? 0.97 : 1})`;
      c.style.boxShadow = hov ? '0 18px 40px rgba(0,0,0,.16)' : '';
      const bumps = Math.floor((lt + i * 0.37) / 1.9);
      $('.cpv', c).textContent = fmt(LOT[GRID8[i]].p * (1 + bumps * 0.04));
      $('.ctm', c).textContent = `0${3 - (i % 3)}:${String(59 - Math.floor(lt * 1.3 + i * 7) % 60).padStart(2, '0')}`;
    });
    slideToast($('.h-t1', el), t, 12.2, 14.4);
    slideToast($('.h-t2', el), t, 15.5, 17.7);
  },
});
BELL.push([12.2, 4], [15.5, 5]);
cursorTrack(10.8, 21.0, [[10.8, 1780, 1060], [12.4, 760, 700], [14.2, 1220, 640], [16.2, 1600, 560], [17.6, 720, 840], [19.0, 1620, 800], [21, 1620, 800]], [19.55]);

// =====================================================================================
// 0:20 — STROFA 1: ogni verso è un lotto in vendita
// =====================================================================================
listing(20.6, 26.5, { lot: LOT.chitarra, title: L(0).t, cond: 'Usata. Vissuta.', p0: 1250, p1: 1900, buy: '2.999', cat: 'Musica' });
listing(26.3, 31.9, { lot: LOT.voce, title: L(1).t, cond: 'Rauca. Notturna. Stanca.', p0: 640, p1: 980, buy: '1.500', cat: 'Musica', bids0: 9 });
listing(31.7, 37.2, { lot: LOT.vicolo, title: L(2).t, cond: 'Malinconica (blues incluso)', p0: 3100, p1: 4400, buy: '9.900', cat: 'Atmosfere', img2: 'lot_mare', img2At: 34.0, bids0: 31 });
listing(37.0, 42.6, { lot: LOT.moda, title: L(3).t, cond: 'Fuori moda (per fortuna)', p0: 210, p1: 260, buy: '499', cat: 'Moda', bids0: 4 });
listing(42.4, 47.8, { lot: { n: 5, sm: "'a mano", img: 'lot_piano' }, title: L(4).t, cond: 'Suonato a mano', p0: 4300, p1: 5200, buy: '7.900', cat: 'Musica', bids0: 22 });

// 0:47 — "È 'na parola sincera cchiù forte": la cerca nel sito non trova niente
const search = addScene({
  a: 47.6, b: 54.5, kind: 'page', url: '/cerca',
  search: t => (t < 47.85 ? null : ap("'na parola sincera").slice(0, Math.floor(P(t, 47.95, 49.6) * 19))),
  html: `
    <div class="sr-a" style="position:absolute;left:100px;top:60px;font-size:28px;color:#6b7280">Risultati per «<b style="color:#111">’na parola sincera</b>»</div>
    <div class="sr-b" style="position:absolute;left:96px;top:104px;font-size:110px;font-weight:900;letter-spacing:-4px">0 risultati</div>
    <div class="sr-c" style="position:absolute;left:100px;top:262px;font-size:24px;display:flex;gap:14px;align-items:center;color:#6b7280">Forse cercavi:
      <span style="border:1.5px solid #d1d5db;border-radius:20px;padding:8px 16px;color:#111">parola sponsorizzata</span>
      <span style="border:1.5px solid #d1d5db;border-radius:20px;padding:8px 16px;color:#111">parola d’ordine</span>
      <span style="border:1.5px solid #d1d5db;border-radius:20px;padding:8px 16px;color:#111">parola chiave (SEO)</span></div>
    <div class="sr-d serif" style="position:absolute;left:100px;top:380px;font-size:84px;font-style:italic">È ’na parola sincera…</div>
    <div class="sr-e big" style="position:absolute;left:92px;top:505px;font-size:310px;color:#d7261e;transform-origin:0 50%">CCHIÙ FORTE</div>`,
  update(t, lt, el, s) {
    fadeIO(s, t, 0.22, 0);
    for (const [c, ti] of [['.sr-a', 50.0], ['.sr-b', 50.15], ['.sr-c', 50.6], ['.sr-d', 51.0]]) {
      const k = P(t, ti, ti + 0.4, E.out); const e = $(c, el); e.style.opacity = k; e.style.transform = `translateY(${(1 - k) * 20}px)`;
    }
    const k = P(t, 52.05, 52.3, E.out); const e = $('.sr-e', el);
    e.style.opacity = k; e.style.transform = `scale(${lerp(0.55, 1, k) + 0.02 * P(t, 52.3, 54.5)})`;
  },
});
impact(52.3, 7);
// il campo di ricerca sta nell'header globale (fuori dalla scena): coordinate fisse
cursorTrack(47.6, 50.3, [[47.6, 1500, 760], [47.85, 1180, 134], [50.3, 1180, 200]], [47.9]);

// 0:54 — "D'e luci finte e d'e numeri": il lotto si riempie di notifiche e numeri finti
const FAKE = [['+1.000 follower', 1180, 40], ['❤ 25,3K mi piace', 1500, 150], ['🔥 IN TENDENZA #1', 620, 100], ['OFFERTA LAMPO −70%', 160, 620],
  ['⭐ 4,9 · recensioni verificate*', 1240, 700], ['👁 98.765 stanno guardando', 560, 430], ['+500 offerte in 1 minuto', 1500, 470],
  ['SPONSORIZZATO', 140, 160], ['Solo per oggi!', 860, 770], ['#autentico #napoli #vero', 1100, 300]];
listing(54.3, 60.0, {
  lot: { n: 46, sm: "'e denare", img: 'lot_luci' }, title: L(6).t, cond: 'Finte (ma luccicano)', buy: '9.999', cat: 'Visibilità', bids0: 999,
  priceText: t => fmt(1000 + rnd(Math.floor(t * 14)) * 998000),
  extraHTML: `<div class="neon" style="position:absolute;inset:0;pointer-events:none;mix-blend-mode:screen;background:radial-gradient(circle at 25% 30%,rgba(255,0,170,.55),rgba(0,0,0,0) 45%),radial-gradient(circle at 80% 70%,rgba(0,220,255,.45),rgba(0,0,0,0) 45%)"></div>
    ${FAKE.map(([txt, x, y], i) => toastHTML(txt, x, y, 'fk fk' + i)).join('')}`,
  extra(t, lt, el) {
    const beat = BEATS.find(x => t >= x && t < x + 0.25);
    $('.neon', el).style.opacity = P(t, 54.5, 55.2) * (beat ? 0.95 : 0.55);
    $$('.fk', el).forEach((e, i) => {
      popIn(e, t, 54.6 + i * 0.5, 0.25);
      e.style.background = i % 3 === 0 ? '#ff2d87' : i % 3 === 1 ? '#111' : '#0a5cff';
      e.style.fontSize = '24px'; e.style.fontWeight = '800';
    });
  },
});
BELL.push([54.6, 12], [56.6, 99]);

// 0:59 — "Credere a tutti ca chesta è libertà": la libertà in abbonamento
const lib = addScene({
  a: 59.8, b: 65.6, kind: 'page', url: '/premium/liberta',
  html: `${grid8HTML()}<div style="position:absolute;inset:0;background:rgba(10,10,14,.62)"></div>
    <div class="modal" style="position:absolute;left:530px;top:80px;width:860px;height:730px;background:#fff;border-radius:22px;box-shadow:0 30px 80px rgba(0,0,0,.4)">
      <div style="position:absolute;left:50px;top:40px;color:#b8901c;font-weight:800;letter-spacing:4px;font-size:16px">ABBONAMENTO PREMIUM</div>
      <div class="big" style="position:absolute;left:46px;top:66px;font-size:160px;color:#111">LIBERTÀ™</div>
      <div class="serif" style="position:absolute;left:50px;top:226px;font-size:40px;font-style:italic">${ap(L(7).t)}</div>
      <div style="position:absolute;left:50px;top:300px;font-size:22px;line-height:1.75;color:#374151">✓ Libertà di scegliere tra 3 opzioni già scelte<br>✓ Opinioni pronte, consegnate ogni mattina<br>✓ Disdici quando vuoi*</div>
      <div style="position:absolute;left:50px;top:440px;display:flex;gap:16px;align-items:center;font-size:24px;font-weight:600">
        <div class="chk" style="width:36px;height:36px;border:3px solid #111;border-radius:8px;display:flex;align-items:center;justify-content:center;font-size:26px;font-weight:900;color:#fff">✓</div>Accetto di credere a tutto</div>
      <div style="position:absolute;left:50px;top:510px;font-size:42px;font-weight:900">€ 9,99<span style="font-size:22px;color:#6b7280;font-weight:600"> / mese, per sempre</span></div>
      <div class="sub-btn btn primary" style="position:absolute;left:50px;top:590px;width:760px;height:66px;font-size:22px">ABBONATI ORA</div>
      <div style="position:absolute;left:50px;top:678px;font-size:15px;color:#9ca3af">*La disdetta va inviata per raccomandata A/R, Vesuvio permettendo.</div>
    </div>`,
  update(t, lt, el, s) {
    fadeIO(s, t, 0.25, 0);
    const k = P(t, 60.0, 60.4, E.back); const m = $('.modal', el);
    m.style.transform = `scale(${lerp(0.88, 1, k)})`; m.style.opacity = P(t, 60.0, 60.25);
    const on = t >= 62.05; const c = $('.chk', el); c.style.background = on ? '#0a5cff' : '#fff'; c.style.borderColor = on ? '#0a5cff' : '#111';
    $('.sub-btn', el).style.transform = `scale(${t > 64.9 && t < 65.1 ? 0.97 : 1})`;
  },
});
cursorTrack(59.9, 65.6, [[59.9, 1500, 1000], [61.85, { scene: lib, sel: '.chk', at: 61 }], [62.4, { scene: lib, sel: '.chk', at: 61, dx: 40, dy: 30 }], [64.5, { scene: lib, sel: '.sub-btn', at: 61 }], [65.6, { scene: lib, sel: '.sub-btn', at: 61 }]], [62.0, 64.95]);

// =====================================================================================
// 1:05 — RITORNELLO 1
// =====================================================================================
// "Napule nun se venne / pe' quatto click e na fotografia"
const c1clicks = beatsBetween(69.2, 72.0).slice(0, 4);
const c1stamp1 = stamp(snap(67.55), 16);
const c1flash = snap(72.15);
const c1stamp2 = stamp(snap(72.8), 12);
flash(c1flash, 0.5, 1);
c1clicks.forEach((c, i) => CART.push([c, i + 1]));
CART.push([104, 0]);
const ch1a = addScene({
  a: 65.2, b: 75.0, kind: 'page', url: '/asta/napoli/compra-tutto',
  html: `<div class="photo" style="position:absolute;left:0;top:0;width:1920px;height:908px;background:#f5f6f8;transform-origin:50% 50%">
      <div style="position:absolute;left:100px;top:34px;font-size:30px;font-weight:800">Asta: NAPOLI · <span class="muted" style="font-weight:600">8 lotti selezionati per te</span></div>
      ${grid8HTML()}
      <div class="buyall btn red" style="position:absolute;left:1240px;top:18px;width:580px;height:66px;font-size:24px;letter-spacing:1px">COMPRA TUTTO · € 36.364</div>
      <div class="clickc" style="position:absolute;left:1030px;top:28px;font-size:34px;font-weight:900;color:#0a5cff;opacity:0"></div>
      ${stampAt('NUN SE VENNE', 200, 960, 440, '', 'st1')}
      ${stampAt('NUN SE VENNE', 130, 960, 454, '', 'st2')}
    </div>`,
  update(t, lt, el, s) {
    fadeIO(s, t, 0.2, 0);
    stampAnim($('.st1', el), t, c1stamp1, -9, 68.9);
    const n = c1clicks.filter(c => t >= c).length;
    const cc = $('.clickc', el);
    cc.textContent = `click ${n}/4`; cc.style.opacity = n > 0 && t < c1flash ? 1 : 0;
    const lastc = c1clicks.filter(c => t >= c).pop();
    cc.style.transform = `scale(${lastc ? 1 + 0.3 * (1 - P(t, lastc, lastc + 0.2)) : 1})`;
    $('.buyall', el).style.transform = `scale(${c1clicks.some(c => t >= c - 0.03 && t < c + 0.12) || (t > 66.95 && t < 67.1) ? 0.96 : 1})`;
    // "na fotografia": la pagina diventa una foto ricordo
    const k = P(t, c1flash, c1flash + 0.35, E.out);
    const ph = $('.photo', el);
    ph.style.transform = `scale(${lerp(1, 0.78, k)}) rotate(${lerp(0, -3, k)}deg)`;
    ph.style.outline = k > 0 ? `${18 * k}px solid #fff` : 'none';
    ph.style.boxShadow = k > 0 ? `0 40px 90px rgba(0,0,0,${0.6 * k})` : 'none';
    el.style.background = t >= c1flash ? '#2a2a2e' : '#f5f6f8';
    stampAnim($('.st2', el), t, c1stamp2, -12);
  },
});
cursorTrack(65.3, 72.1, [[65.3, 960, 874], [66.7, { scene: ch1a, sel: '.buyall', at: 66 }], [68.9, { scene: ch1a, sel: '.buyall', at: 66 }], [69.15, { scene: ch1a, sel: '.buyall', at: 66, dx: -40 }], [71.8, { scene: ch1a, sel: '.buyall', at: 66, dx: 40 }], [72.1, 1100, 900]], [66.95, ...c1clicks]);

// "Napule nun se venne": timbri su ogni lotto, a tempo
const c1b = beatsBetween(75.0, 80).slice(0, 8);
c1b.forEach(b => impact(b, 6));
addScene({
  a: 74.85, b: 79.6, kind: 'page', url: '/asta/napoli',
  html: `<div style="position:absolute;left:100px;top:34px;font-size:30px;font-weight:800">Asta: NAPOLI · <span class="muted" style="font-weight:600">lotti disponibili: <span class="disp">8</span></span></div>
    ${grid8HTML()}
    <div class="bnb" style="position:absolute;left:${gridPos(6).x}px;top:${gridPos(6).y}px;width:400px;opacity:0;z-index:5">
      ${cardHTML({ n: 31, sm: "'o padrone 'e casa", img: 'lot_keybox', t: 'Ex balcone, ora B&B (4 posti letto)', p: 89 }, 0, 0, 400, 200)}</div>
    ${GRID8.map((k, i) => stampAt('NUN SE VENNE', 36, gridPos(i).x + 200, gridPos(i).y + 100, '', 'gs' + i)).join('')}`,
  update(t, lt, el, s) {
    fadeIO(s, t, 0.2, 0);
    $('.bnb', el).style.opacity = P(t, 75.6, 76.0);
    c1b.forEach((b, i) => stampAnim($('.gs' + i, el), t, b, -14 + rnd(i) * 22));
    $('.disp', el).textContent = 8 - c1b.filter(b => t >= b).length;
  },
});

// "Pe' nu ritornello senza fantasia"
const ritStamp = stamp(snap(82.7), 12);
listing(79.4, 83.5, {
  lot: { n: 23, sm: "'o scemo", img: 'lot_vinile' }, title: L(11).t, cond: 'Usato 1.000 volte', buy: '0,99', cat: 'Musica', bids0: 1000,
  priceText: () => '0,99',
  extraHTML: stampAt('NUN SE VENNE', 105, 570, 437, '', 'rs'),
  extra(t, lt, el) { stampAnim($('.rs', el), t, ritStamp, -11); },
});

// "E quanno 'a musica nasce d''o core": la chitarra non ha prezzo
function coreScene(a, b, strike) {
  return cine(a, b, 'lot_chitarra', {
    s0: 1.08, s1: 1.2, x1: 20, filter: 'saturate(1.15) brightness(1.05)',
    extraHTML: `<div class="glow" style="position:absolute;inset:0;background:radial-gradient(circle at 50% 60%,rgba(255,170,60,.35),rgba(0,0,0,0) 60%);mix-blend-mode:screen"></div>
      <div class="tag" style="position:absolute;left:110px;top:110px;background:#fff;border-radius:18px;padding:22px 30px;box-shadow:0 20px 50px rgba(0,0,0,.35);min-width:380px">
        <div style="display:flex;gap:12px;align-items:center"><span class="lotpill">LOTTO 55</span><span class="smorfia">’a museca</span></div>
        <div style="position:relative;display:inline-block;font-size:72px;font-weight:900;letter-spacing:-2px;margin-top:8px">€ 1.250<div class="strike" style="position:absolute;left:-6px;top:52%;height:8px;background:#d7261e;width:0"></div></div>
        <div class="nonv serif" style="font-size:44px;font-style:italic;color:#b91c1c;opacity:0">Non in vendita</div></div>`,
    extra(t, lt, el) {
      const k = P(t, a + 0.3, a + 0.7, E.out); const tg = $('.tag', el);
      tg.style.opacity = k; tg.style.transform = `translateY(${(1 - k) * -30}px)`;
      $('.strike', el).style.width = `${P(t, strike, strike + 0.3, E.out) * 105}%`;
      $('.nonv', el).style.opacity = P(t, strike + 0.35, strike + 0.75);
      const beat = BEATS.find(x => t >= x && t < x + 0.4);
      $('.glow', el).style.opacity = 0.6 + (beat ? 0.4 * (1 - (t - beat) / 0.4) : 0);
    },
  });
}
coreScene(83.3, 89.5, 86.2);

// "Nun tene bisogno 'e pubblicità": il cursore chiude una pubblicità dopo l'altra
const ADS = [
  { x: 120, y: 60, w: 380, img: 'ad_vesuvio', t: 'VIVI LA VERA NAPOLI™', d: 'da 29,99 € · prenota ora' },
  { x: 560, y: 150, w: 360, bg: '#d7261e', t: 'OFFERTA LAMPO −70%', d: 'Su tutta la tradizione' },
  { x: 990, y: 40, w: 360, img: 'hit_cover', t: "HIT DELL'ESTATE", d: 'Ascolta ora (sponsorizzato)' },
  { x: 1410, y: 130, w: 380, bg: '#0a5cff', t: 'Diventa virale in 24h', d: '+10.000 follower garantiti' },
  { x: 250, y: 430, w: 380, img: 'lot_pizza', t: 'Pizza Experience™', d: 'Prenota il tuo tavolo-selfie' },
  { x: 760, y: 470, w: 400, bg: '#111', t: 'Il tuo ritornello è già scritto', d: 'Basta un click' },
  { x: 1300, y: 450, w: 380, img: 'lot_autotune', t: 'AUTOTUNE PRO', d: 'Canta senza cantare' },
];
function adHTML(a, i, cls) {
  const body = a.img ? `<img src="${IMG(a.img)}"><div class="pb"><div class="pt">${ap(a.t)}</div><div class="pd">${a.d}</div></div>`
    : `<div class="pb" style="background:${a.bg};color:#fff;height:210px;display:flex;flex-direction:column;justify-content:center"><div class="pt" style="font-size:40px">${ap(a.t)}</div><div class="pd" style="color:rgba(255,255,255,.85)">${a.d}</div></div>`;
  return `<div class="popup ${cls} ${cls}${i}" style="left:${a.x}px;top:${a.y}px;width:${a.w}px;opacity:0"><div class="ph">PUBBLICITÀ<div class="x">×</div></div>${body}</div>`;
}
const adClose = ADS.map((_, i) => 90.55 + i * 0.42);
const ads1 = addScene({
  a: 89.3, b: 93.9, kind: 'page', url: '/asta/napoli',
  html: `${grid8HTML()}<div style="position:absolute;inset:0;background:rgba(0,0,0,.2)"></div>${ADS.map((a, i) => adHTML(a, i, 'ad')).join('')}
    ${toastHTML('🛡 Pubblicità chiuse: <b class="adn">0</b>/7', 1500, 820)}`,
  update(t, lt, el, s) {
    fadeIO(s, t, 0.15, 0);
    ADS.forEach((a, i) => {
      const e = $('.ad' + i, el);
      popIn(e, t, 89.35 + i * 0.15, 0.25);
      const c = adClose[i];
      if (t >= c) { const k = P(t, c + 0.02, c + 0.18); e.style.opacity = 1 - k; e.style.transform = `scale(${1 - 0.15 * k})`; }
    });
    const n = adClose.filter(c => t >= c).length;
    $('.adn', el).textContent = n;
    const tt = $('.toast', el); tt.style.opacity = P(t, 90.6, 90.9); tt.style.background = n === 7 ? '#15803d' : '#111';
  },
});
cursorTrack(89.4, 93.9, [[89.4, 960, 980], ...ADS.flatMap((a, i) => [[adClose[i] - 0.12, { scene: ads1, sel: `.ad${i} .x`, at: 90.53 }], [adClose[i] + 0.04, { scene: ads1, sel: `.ad${i} .x`, at: 90.53 }]]), [93.9, 1200, 900]], adClose);

// "Napule nun se venne" — titolo
const titStamp = stamp(snap(95.6), 22);
addScene({
  a: 93.6, b: 100.45, kind: 'full',
  html: `<div style="position:absolute;inset:0;background:radial-gradient(ellipse at center,#3a0a08 0%,#0b0505 70%)"></div>
    <div class="tg" style="position:absolute;inset:0">
      <div class="nap big" style="position:absolute;left:0;right:0;top:250px;text-align:center;font-size:330px;letter-spacing:30px">NAPULE</div>
      ${stampAt('NUN SE VENNE', 170, 960, 690, 'onDark', 'ts')}
    </div>`,
  update(t, lt, el, s) {
    fadeIO(s, t, 0.15, 0.4);
    const k = P(t, 93.75, 94.35, E.out); const n = $('.nap', el);
    n.style.opacity = k; n.style.transform = `scale(${lerp(1.25, 1, k)})`; n.style.letterSpacing = `${lerp(80, 30, k)}px`;
    stampAnim($('.ts', el), t, titStamp, -6);
    $('.tg', el).style.transform = `scale(${1 + 0.06 * P(t, 94, 100.4)})`;
  },
});
// "È verità"
function veritaScene(a, b, label) {
  return addScene({
    a, b, kind: 'full',
    html: `<div class="center"><div class="lab" style="font-family:Receipt;font-size:30px;color:#ff3b2f;letter-spacing:3px"></div>
      <div class="vv serif" style="color:#fff;font-size:160px;font-weight:600;margin-top:20px">È verità.</div></div>`,
    update(t, lt, el, s) {
      fadeIO(s, t, 0.1, 0.4);
      $('.lab', el).textContent = label.slice(0, Math.floor(P(t, a + 0.1, a + 0.7) * label.length));
      const k = P(t, a + 0.25, a + 1.1, E.out); const v = $('.vv', el);
      v.style.opacity = k; v.style.letterSpacing = `${lerp(14, 0, k)}px`;
    },
  });
}
veritaScene(100.3, 104.45, 'ERRORE 90 · ’A PAURA');

// =====================================================================================
// 1:44 — STROFA 2: la fabbrica delle hit
// =====================================================================================
function hitsHTML() {
  let h = '';
  for (let r = 0; r < 7; r++) for (let c = 0; c < 10; c++) {
    const k = r * 10 + c + 1;
    h += `<div class="hit" data-c="${c}" style="position:absolute;left:${c * 274}px;top:${r * 330}px;width:250px">
      <img src="${IMG('hit_cover')}" style="width:250px;height:250px;border-radius:10px;object-fit:cover">
      <div style="font-weight:800;font-size:17px;margin-top:8px">Hit napoletana #${k}</div>
      <div class="muted" style="font-size:14px">3:01 · ammore, core, stasera</div></div>`;
  }
  return h;
}
function hitsHeader(sub) {
  return `<div class="crumbs">Musica › <b style="color:#111">Hit del momento</b></div>
    <div style="position:absolute;left:100px;top:62px;display:flex;align-items:center;gap:16px;z-index:3">
      <span class="lotpill">LOTTO 9</span><span class="smorfia">’a figliata</span>
      <span style="font-size:32px;font-weight:800;margin-left:10px">${sub}</span></div>`;
}
addScene({
  a: 104.3, b: 109.65, kind: 'page', url: '/musica/hit', tab: 'vendesi.napoli · Musica',
  html: `${hitsHeader('Hit napoletane — tutte nuove, tutte uguali')}
    <div class="hg" style="position:absolute;left:100px;top:130px;width:2716px;transform-origin:0 0">${hitsHTML()}</div>`,
  update(t, lt, el, s) {
    fadeIO(s, t, 0.2, 0);
    $('.hg', el).style.transform = `scale(${kf(t, [[104.6, 1], [109.4, 0.633]])})`;
  },
});
listing(109.5, 111.95, {
  lot: { n: 60, sm: 'se lamenta', img: 'lot_autotune' }, title: 'Autotune «Vesuvio» Pro', cond: 'Intonazione garantita al 100%', p0: 99, p1: 149, buy: '199', cat: 'Musica', bids0: 340,
  imgHTML: `<svg class="pitch" viewBox="0 0 980 735" style="position:absolute;inset:0">
      <polyline points="${Array.from({ length: 50 }, (_, i) => `${40 + i * 18},${380 + Math.sin(i * 0.9) * 60 + (rnd(i) - 0.5) * 70}`).join(' ')}" fill="none" stroke="rgba(255,255,255,.7)" stroke-width="4"/>
      <polyline class="q" points="${Array.from({ length: 50 }, (_, i) => { const y = 380 + Math.round((Math.sin(i * 0.9) * 60) / 40) * 40; return `${40 + i * 18},${y} ${58 + i * 18},${y}`; }).join(' ')}" fill="none" stroke="#ff2d87" stroke-width="8" stroke-dasharray="2400" stroke-dashoffset="2400"/>
      <text x="40" y="660" fill="#fff" font-family="Inter" font-weight="800" font-size="34">CORREZIONE: 100%</text></svg>`,
  extra(t, lt, el) { $('.q', el).style.strokeDashoffset = 2400 * (1 - P(t, 109.8, 111.6)); },
});
beatsBetween(111.7, 114.2).forEach(b => impact(b, 3));
cine(111.7, 114.25, 'factory', {
  filter: 'saturate(.5) brightness(.75) hue-rotate(10deg)', s0: 1.15, s1: 1.3, fin: 0.15, fout: 0.2,
  extraHTML: `<div style="position:absolute;inset:0;background:rgba(0,40,90,.35)"></div>
    <div style="position:absolute;left:110px;top:100px;display:flex;gap:16px;align-items:center"><span class="lotpill" style="background:#fff;color:#111">LOTTO 38</span><span class="smorfia" style="color:#ffe2a8">’e mazzate</span></div>
    <div class="center"><div class="big" style="font-size:120px">HIT PRODOTTE</div><div class="big fcn" style="font-size:260px;color:#7fd3ff"></div>
      <div style="color:#fff;font-size:34px;font-weight:700;margin-top:10px">1.000 hit/ora · 0 emozioni</div></div>`,
  extra(t, lt, el) { $('.fcn', el).textContent = fmt(12000 + lt * 1873); },
});

// "Parole senza tiempo e senza storia": il generatore di ritornelli
const CHIPS = [['ammore', 100, 150], ['core', 270, 110], ['stasera', 400, 150], ['nun me lassà', 570, 210], ['luna', 800, 110], ['mare', 930, 110]];
const chipT = [114.55, 114.95, 115.35, 115.75];
const genClick = 116.25;
const GEN_TEXT = 'Ammore, core, stasera, nun me lassà…\nAmmore, core, stasera, nun me lassà…\nAmmore, core, stasera…\nAmmore…';
const gen = addScene({
  a: 114.0, b: 119.4, kind: 'page', url: '/musica/generatore',
  html: `<div class="serif" style="position:absolute;left:100px;top:40px;font-size:66px;font-weight:700">${ap(L(18).t)}</div>
    <div style="position:absolute;left:100px;top:142px;font-size:22px;color:#6b7280">Generatore di ritornelli™ · Scegli le parole, al resto pensiamo noi</div>
    ${CHIPS.map(([w, x, ww], i) => `<div class="chip chip${i}" style="position:absolute;left:${x}px;top:206px;width:${ww}px;height:56px;border:2px solid #d1d5db;border-radius:28px;display:flex;align-items:center;justify-content:center;font-size:21px;font-weight:700">${ap(w)}</div>`).join('')}
    <div class="genbtn btn primary" style="position:absolute;left:1140px;top:203px;width:260px;height:62px;font-size:22px">GENERA ✨</div>
    <div style="position:absolute;left:100px;top:300px;width:1720px;height:440px;background:#fff;border:1px solid #e5e7eb;border-radius:18px;padding:34px 40px">
      <div class="gout serif" style="font-size:46px;font-style:italic;line-height:1.45;white-space:pre-wrap"></div></div>
    <div style="position:absolute;left:100px;top:770px;display:flex;align-items:center;gap:18px;font-size:22px;font-weight:700">Originalità:
      <div style="width:400px;height:16px;border-radius:8px;background:#e5e7eb"></div><span style="color:#d7261e">0%</span></div>`,
  update(t, lt, el, s) {
    fadeIO(s, t, 0.2, 0);
    chipT.forEach((ct, i) => { const c = $('.chip' + i, el); const on = t >= ct; c.style.background = on ? '#0a5cff' : '#fff'; c.style.color = on ? '#fff' : '#111'; c.style.borderColor = on ? '#0a5cff' : '#d1d5db'; });
    $('.genbtn', el).style.transform = `scale(${t > genClick && t < genClick + 0.15 ? 0.95 : 1})`;
    const n = Math.floor(P(t, 116.45, 119.2) * GEN_TEXT.length);
    $('.gout', el).textContent = GEN_TEXT.slice(0, n) + (t > 116.4 && Math.floor(t * 3) % 2 ? '▌' : '');
  },
});
cursorTrack(114.05, 117.0, [[114.05, 1300, 760], ...chipT.flatMap((ct, i) => [[ct - 0.12, { scene: gen, sel: '.chip' + i }], [ct + 0.05, { scene: gen, sel: '.chip' + i }]]), [genClick - 0.12, { scene: gen, sel: '.genbtn' }], [117, { scene: gen, sel: '.genbtn', dx: 60, dy: 80 }]], [...chipT, genClick]);

// "Ca passano veloci e perdono memoria": il feed scorre sempre più veloce e si cancella
const FEED = ['lot_vesuvio', 'hit_cover', 'lot_pizza', 'lot_luci', 'lot_vespa', 'lot_autotune', 'lot_panni', 'hit_cover', 'lot_snowglobe', 'lot_caffe', 'lot_vinile', 'hit_cover'];
const FEED_TXT = ['Tramonto sul Vesuvio', 'Hit napoletana #12', 'Pizza con vista', 'Luci finte', 'Vespa + lungomare', 'Autotune Pro', 'Panni stesi', 'Hit napoletana #47', 'Vesuvio in palla di vetro', 'Caffè sospeso', 'Ritornello usato', 'Hit napoletana #3'];
const FEED_PITCH = 430, FEED_N = FEED.length * 2;
function feedPos(t) { let p = 0; for (let x = 119.2; x < t; x += 1 / 120) { const sp = lerp(500, 7200, E.in(P(x, 119.3, 122.5))) * (1 - P(x, 123.2, 124.0, E.out)); p += sp / 120; } return p; }
addScene({
  a: 119.2, b: 124.1, kind: 'page', url: '/per-te',
  html: `<div class="fd" style="position:absolute;left:410px;top:0;width:1100px">
    ${Array.from({ length: FEED_N * 2 }, (_, i) => { const j = i % FEED.length; return `<div style="position:absolute;left:0;top:${i * FEED_PITCH}px;width:1100px;height:400px;background:#fff;border-radius:18px;overflow:hidden;border:1px solid #e5e7eb">
      <img class="fi" src="${IMG(FEED[j])}" style="position:absolute;left:0;top:0;width:600px;height:400px;object-fit:cover">
      <div class="ftx" style="position:absolute;left:640px;top:50px;width:420px"><span class="lotpill">IN TENDENZA</span><div style="font-size:38px;font-weight:800;margin-top:22px;line-height:1.1">${FEED_TXT[j]}</div><div class="muted" style="font-size:22px;margin-top:14px">€ ${fmt(100 + rnd(i) * 9000)} · ${fmt(1000 + rnd(i + 9) * 90000)} views</div></div></div>`; }).join('')}</div>
    ${toastHTML('🗑 Cronologia cancellata', 790, 380, 'fdt')}`,
  update(t, lt, el, s) {
    fadeIO(s, t, 0.2, 0);
    const p = feedPos(t) % (FEED_N * FEED_PITCH);
    const fd = $('.fd', el);
    fd.style.transform = `translateY(${-p}px)`;
    const sp = lerp(500, 7200, E.in(P(t, 119.3, 122.5))) * (1 - P(t, 123.2, 124.0, E.out));
    const forget = P(t, 122.2, 123.5);
    fd.style.filter = `blur(${(sp / 2400).toFixed(2)}px) grayscale(${forget})`;
    fd.style.opacity = 1 - 0.75 * forget;
    const tt = $('.fdt', el); tt.style.opacity = P(t, 123.0, 123.25); tt.style.fontSize = '34px'; tt.style.padding = '26px 36px';
  },
});

// =====================================================================================
// 2:04 — "Ma ccà ce stanno ancora 'e canzoni vere": lo schermo si rompe, entra la città vera
// =====================================================================================
flash(124.05, 0.3, 0.6);
cine(124.0, 128.45, 'real_guitar', {
  fin: 0.01, s0: 1.1, s1: 1.2, x1: -40,
  extraHTML: Array.from({ length: 9 }, (_, i) => `<div class="gl" style="position:absolute;left:0;top:${i * 120}px;width:1920px;height:120px;background:url(${IMG('real_guitar')}) 0 ${-i * 120}px/1920px 1080px;opacity:0"></div>`).join(''),
  extra(t, lt, el) {
    const on = t < 124.5;
    $$('.gl', el).forEach((g, i) => {
      g.style.opacity = on ? 1 : 0;
      const fr = Math.floor(t * 30);
      g.style.transform = `translateX(${(rnd(fr * 13 + i) - 0.5) * 160 * (1 - P(t, 124.0, 124.5))}px)`;
      g.style.filter = i % 3 === 0 ? 'hue-rotate(160deg) saturate(3)' : i % 3 === 1 ? 'grayscale(1) contrast(2)' : 'none';
    });
  },
});
cine(128.1, 133.35, 'real_radio', { s0: 1.05, s1: 1.15, x1: 30 });
cine(133.0, 136.7, 'real_sea', { s0: 1.06, s1: 1.18, x1: -40 });
cine(136.3, 140.15, 'real_sheets', { s0: 1.05, s1: 1.15, y1: 10 });
const city = cine(139.8, 145.95, 'real_city', {
  s0: 1.04, s1: 1.2, x1: 0, y1: -20,
  extraHTML: `<div class="back" style="position:absolute;left:610px;top:760px;width:700px;height:120px;background:#fff;border-radius:18px;box-shadow:0 20px 60px rgba(0,0,0,.5);display:flex;align-items:center;padding:0 24px;gap:16px;opacity:0">
    <div style="flex:1"><div style="font-weight:900;font-size:26px">Sei ancora lì?</div><div style="color:#6b7280;font-size:18px">Hai 1 asta in corso: <b style="color:#111">NAPOLI</b></div></div>
    <div class="backbtn btn primary" style="width:240px;height:58px;font-size:19px">Torna all’asta →</div></div>`,
  extra(t, lt, el) { const k = P(t, 144.3, 144.6, E.back); const b = $('.back', el); b.style.opacity = P(t, 144.3, 144.45); b.style.transform = `translateY(${(1 - k) * 40}px)`; },
});
cursorTrack(144.4, 145.95, [[144.4, 1500, 1050], [145.2, { scene: city, sel: '.backbtn', at: 145 }], [145.95, { scene: city, sel: '.backbtn', at: 145 }]], [145.35]);

// =====================================================================================
// 2:25 — RITORNELLO 2: l'asta finale
// =====================================================================================
const c2stamp1 = stamp(snap(147.9), 14);
const c2clicks = beatsBetween(149.9, 152.6).slice(0, 4);
const c2flash = snap(153.0);
const c2stamp2 = stamp(snap(153.75), 12);
flash(c2flash, 0.5, 1);
const finale = addScene({
  a: 145.7, b: 155.75, kind: 'page', url: '/asta/napoli/lotto-unico', tab: 'vendesi.napoli · ASTA FINALE',
  html: `<div class="photo" style="position:absolute;left:0;top:0;width:1920px;height:908px;background:#f5f6f8;transform-origin:50% 50%">${auctionHTML()}
    ${stampAt('NUN SE VENNE', 112, 570, 437, '', 'f1')}
    <div class="plus" style="position:absolute;left:1150px;top:250px;font-size:44px;font-weight:900;color:#0a5cff;opacity:0">+ € 50.000.000</div>
    ${stampAt('NUN SE VENNE', 140, 960, 454, '', 'f2')}</div>`,
  update(t, lt, el, s) {
    fadeIO(s, t, 0.25, 0);
    kb($('.i1', el), P(t, 145.7, 155.7), 1.04, 1.18, 0, -30, 0, -10);
    const price = Math.min(999999999, 1e6 * Math.pow(10, 2.9999 * P(t, 146.0, 153.0, E.in)));
    $('.pv', el).textContent = fmt(price);
    const k = Math.floor(lt / 0.5);
    $('.bids', el).textContent = 211 + k * 7;
    $('.feed', el).innerHTML = feedHTML(k, price);
    stampAnim($('.f1', el), t, c2stamp1, -10, c2flash - 0.4);
    const lastc = c2clicks.filter(c => t >= c).pop();
    const pl = $('.plus', el);
    pl.style.opacity = lastc ? 1 - P(t, lastc, lastc + 0.5) : 0;
    pl.style.transform = `translateY(${lastc ? -60 * P(t, lastc, lastc + 0.5) : 0}px)`;
    $('.offri', el).style.transform = `scale(${c2clicks.some(c => t >= c - 0.03 && t < c + 0.12) ? 0.95 : 1})`;
    const kk = P(t, c2flash, c2flash + 0.35, E.out); const ph = $('.photo', el);
    ph.style.transform = `scale(${lerp(1, 0.78, kk)}) rotate(${lerp(0, 2.5, kk)}deg)`;
    ph.style.outline = kk > 0 ? `${18 * kk}px solid #fff` : 'none';
    ph.style.boxShadow = kk > 0 ? `0 40px 90px rgba(0,0,0,${0.6 * kk})` : 'none';
    el.style.background = t >= c2flash ? '#2a2a2e' : '#f5f6f8';
    stampAnim($('.f2', el), t, c2stamp2, 8);
  },
});
cursorTrack(146.2, 153.0, [[146.2, 1700, 1000], [149.6, { scene: finale, sel: '.offri', at: 147 }], [152.7, { scene: finale, sel: '.offri', at: 147, dx: 20 }], [153.0, 1500, 900]], c2clicks);

// storico offerte: ogni riga timbrata a mezzo beat
const hb0 = snap(156.2), HALF = 0.3135;
const rowT = Array.from({ length: 8 }, (_, i) => hb0 + i * HALF);
rowT.forEach(x => impact(x, 4));
const ROWS = ['mariuolo_79', 'golfo_invest_spa', 'bnb_holding_srl', 'vesuvio_capital', 'mariuolo_79', 'golfo_invest_spa', 'bnb_holding_srl', 'mariuolo_79'];
addScene({
  a: 155.6, b: 159.85, kind: 'page', url: '/asta/napoli/lotto-unico/offerte',
  html: `<div style="position:absolute;left:100px;top:34px;font-size:34px;font-weight:800">Storico offerte · <span class="muted" style="font-weight:600">LOTTO UNICO: Napoli (tutta intera)</span></div>
    ${ROWS.map((w, i) => `<div style="position:absolute;left:100px;top:${120 + i * 92}px;width:1720px;height:80px;background:#fff;border:1px solid #e5e7eb;border-radius:14px;display:flex;align-items:center;padding:0 28px;font-size:26px;gap:20px">
      <div class="avatar" style="width:44px;height:44px">${w[0]}</div><b style="width:420px">${w}</b><span style="width:560px">€ ${fmt(999999999 / Math.pow(1.4, i))}</span><span class="muted">${i * 2 + 1}s fa</span>
      <div class="rs${i}" style="position:absolute;left:20px;top:38px;height:5px;background:#d7261e;width:0"></div></div>`).join('')}
    ${ROWS.map((_, i) => stampAt('NUN SE VENNE', 28, 1600, 160 + i * 92, '', 'rst' + i)).join('')}`,
  update(t, lt, el, s) {
    fadeIO(s, t, 0.2, 0);
    rowT.forEach((x, i) => { $('.rs' + i, el).style.width = `${P(t, x - 0.08, x + 0.05) * 1680}px`; stampAnim($('.rst' + i, el), t, x, -5 + rnd(i) * 10); });
  },
});

// "Pe' nu ritornello senza fantasia": tutte le hit uguali, un solo timbro
const hitStamp = stamp(snap(162.9), 18);
addScene({
  a: 159.5, b: 164.05, kind: 'page', url: '/musica/hit', tab: 'vendesi.napoli · Musica',
  html: `${hitsHeader('Hit napoletane — tutte nuove, tutte uguali')}
    <div class="hg" style="position:absolute;left:100px;top:130px;width:2716px;transform-origin:0 0;transform:scale(.633)">${hitsHTML()}</div>
    ${stampAt('NUN SE VENNE', 150, 960, 470, '', 'hs')}`,
  update(t, lt, el, s) {
    fadeIO(s, t, 0.2, 0);
    $$('.hit', el).forEach(h => { const c = +h.dataset.c; h.style.filter = `grayscale(${P(t, 160.0 + c * 0.22, 160.4 + c * 0.22)})`; });
    stampAnim($('.hs', el), t, hitStamp, -9);
  },
});
coreScene(163.8, 169.95, 166.6);

// "Nun tene bisogno 'e pubblicità": le pubblicità si moltiplicano e poi crollano
const ADS2 = Array.from({ length: 12 }, (_, i) => ({ ...ADS[i % ADS.length], x: 40 + rnd(i * 3 + 1) * 1460, y: 10 + rnd(i * 5 + 2) * 460 }));
addScene({
  a: 169.7, b: 175.65, kind: 'page', url: '/asta/napoli',
  html: `${grid8HTML()}<div style="position:absolute;inset:0;background:rgba(0,0,0,.2)"></div>${ADS2.map((a, i) => adHTML(a, i, 'bd')).join('')}
    ${toastHTML('🛡 Pubblicità bloccate: 12', 1440, 820, 'bdt')}`,
  update(t, lt, el, s) {
    fadeIO(s, t, 0.2, 0);
    ADS2.forEach((a, i) => {
      const e = $('.bd' + i, el);
      const t0 = 169.8 + i * 0.22;
      if (t < 173.0) { popIn(e, t, t0, 0.22); return; }
      const d = Math.max(0, t - (173.0 + i * 0.03));
      e.style.opacity = 1;
      e.style.transform = `translateY(${0.5 * 3200 * d * d}px) rotate(${d * (rnd(i) - 0.5) * 160}deg)`;
    });
    slideToast($('.bdt', el), t, 173.4, 99);
  },
});
impact(173.0, 10);

// "Napule nun se venne": l'asta si annulla, il prezzo torna a zero
const c2stamp3 = stamp(snap(178.5), 16);
addScene({
  a: 175.4, b: 181.45, kind: 'page', url: '/asta/napoli/lotto-unico', tab: 'vendesi.napoli · ASTA ANNULLATA',
  html: `${auctionHTML()}${stampAt('NUN SE VENNE', 112, 570, 437, '', 'cs')}
    ${toastHTML('⛔ Account <b>mariuolo_79</b> sospeso', 1300, 830, 'cst')}`,
  update(t, lt, el, s) {
    fadeIO(s, t, 0.25, 0);
    kb($('.i1', el), P(t, 175.4, 181.4), 1.18, 1.06, -30, 0, -10, 0);
    const price = 999999999 * (1 - P(t, 175.6, 178.2, E.inOut));
    $('.pv', el).textContent = fmt(price);
    $('.status', el).innerHTML = t > 178.3 ? '<b style="color:#d7261e;font-size:22px">ASTA ANNULLATA · nessun lotto venduto</b>' : `<span class="bids">0</span> offerte valide · verifica in corso…`;
    $('.feed', el).innerHTML = feedHTML(3, Math.max(price, 1), t > 176 ? Math.min(5, Math.floor((t - 176) / 0.4)) : 0);
    $('.offri', el).style.opacity = 1 - P(t, 178.3, 178.6) * 0.7;
    stampAnim($('.cs', el), t, c2stamp3, -10);
    slideToast($('.cst', el), t, 179.1, 99);
  },
});
CART.push([175.4, 0]);
veritaScene(181.3, 184.6, 'ASTA CHIUSA · LOTTI VENDUTI: 0');

// =====================================================================================
// 3:04 — PONTE: da Carosone a Pino
// =====================================================================================
cine(184.4, 189.5, 'real_records', {
  filter: 'sepia(.55) contrast(1.05)', s0: 1.08, s1: 1.2,
  extraHTML: `<div style="position:absolute;inset:0;background:linear-gradient(0deg,rgba(0,0,0,.6),rgba(0,0,0,.1))"></div>
    <div class="center"><div class="r1 big" style="font-size:230px">DA CAROSONE</div><div class="r2 big" style="font-size:230px;color:#ffd77a">A PINO</div>
      <div class="r3 serif" style="color:#fff;font-size:56px;font-style:italic;margin-top:10px">pe’ ’e strade d’’o Sud</div></div>`,
  extra(t, lt, el) {
    for (const [c, ti] of [['.r1', 184.6], ['.r2', 185.95], ['.r3', 187.1]]) { const k = P(t, ti, ti + 0.5, E.out); const e = $(c, el); e.style.opacity = k; e.style.transform = `translateY(${(1 - k) * 50}px)`; }
  },
});
cine(189.2, 194.55, 'real_road', { s0: 1.04, s1: 1.16, x1: 0, y1: -20 });
cine(194.2, 200.05, 'real_wall', { s0: 1.05, s1: 1.15, x1: 30 });
// la maschera cade e sotto c'è il pianoforte
cine(202.0, 210.05, 'lot_piano', { fin: 0.01, s0: 1.08, s1: 1.2, x1: -30, filter: 'saturate(1.1)' });
addScene({
  a: 199.8, b: 204.3, kind: 'full', vig: true,
  html: `<img class="bgimg" src="${IMG('lot_maschera')}">
    <div style="position:absolute;left:110px;top:100px;background:#fff;border-radius:14px;padding:16px 22px;display:flex;gap:12px;align-items:center"><span class="lotpill">LOTTO 75</span><span class="smorfia">Pullecenella</span></div>`,
  update(t, lt, el, s) {
    fadeIO(s, t, 0.4, 0);
    kb($('.bgimg', el), P(t, 199.8, 202.4), 1.05, 1.12);
    const k = P(t, 202.4, 203.4, E.in);
    el.style.transform = `translateY(${k * 1300}px) rotate(${k * 14}deg)`;
    el.style.transformOrigin = '20% 0%';
  },
});
// "'A fatica, l'ammore e pure 'a lotta": trittico
const tri = [snap(209.95), snap(211.35), snap(212.65)];
addScene({
  a: 209.8, b: 215.25, kind: 'full', vig: true,
  html: [['real_fishermen', '’a fatica'], ['real_chairs', 'l’ammore'], ['real_wall', 'e pure ’a lotta']].map(([im, cap], i) => `
    <div class="tp tp${i}" style="position:absolute;left:${i * 640}px;top:0;width:640px;height:1080px;overflow:hidden;border-left:${i ? 4 : 0}px solid #000">
      <img src="${IMG(im)}" style="position:absolute;left:-320px;top:0;width:1280px;height:1080px;object-fit:cover">
      <div class="serif" style="position:absolute;left:0;right:0;bottom:90px;text-align:center;color:#fff;font-size:58px;font-style:italic;text-shadow:0 2px 20px rgba(0,0,0,.8)">${cap}</div></div>`).join(''),
  update(t, lt, el, s) {
    fadeIO(s, t, 0.01, 0.4);
    tri.forEach((ti, i) => { const k = P(t, ti - 0.05, ti + 0.4, E.out); const p = $('.tp' + i, el); p.style.transform = `translateY(${(1 - k) * 1080}px)`; $('img', p).style.transform = `scale(${1.05 + 0.08 * P(t, ti, 215.2)})`; });
  },
});
// "E mentre 'o viento se porta 'e stagioni": il vento si porta via cartellini e offerte
const DEBRIS = ['€ 1.250', 'LOTTO 42', 'OFFERTA', '−70%', '★★★★☆', 'SPONSORIZZATO', 'COMPRA ORA', '€ 29,99', 'LOTTO 1', '#trend', 'ASTA LIVE', '+1.000 follower', '€ 0,99', 'LOTTO 43'];
cine(215.0, 221.65, 'real_sheets', {
  s0: 1.12, s1: 1.04, x0: -30, x1: 30,
  extraHTML: DEBRIS.map((d, i) => `<div class="db db${i}" style="position:absolute;left:0;top:0;background:${i % 3 ? '#fff' : '#d7261e'};color:${i % 3 ? '#111' : '#fff'};font-weight:900;font-size:${26 + rnd(i) * 22}px;padding:10px 18px;border-radius:10px;box-shadow:0 10px 30px rgba(0,0,0,.25);white-space:nowrap">${d}</div>`).join(''),
  extra(t, lt, el) {
    DEBRIS.forEach((d, i) => {
      const t0 = 215.3 + i * 0.32, k = (t - t0) / 2.3;
      const e = $('.db' + i, el);
      if (k < 0 || k > 1) { e.style.opacity = 0; return; }
      e.style.opacity = 1;
      const x = lerp(-300, 2100, k), y = 120 + rnd(i * 7) * 760 + Math.sin(k * 6 + i) * 70 - k * 120;
      e.style.transform = `translate(${x}px, ${y}px) rotate(${k * (rnd(i + 3) - 0.3) * 540}deg)`;
    });
  },
});
cine(221.3, 227.7, 'real_guitar', {
  s0: 1.02, s1: 1.14, x0: 20, x1: -20,
  extraHTML: `<div class="rays" style="position:absolute;inset:0;background:radial-gradient(circle at 70% 25%,rgba(255,200,120,.55),rgba(0,0,0,0) 55%);mix-blend-mode:screen"></div>`,
  extra(t, lt, el) { $('.rays', el).style.opacity = 0.5 + 0.5 * Math.sin(lt * 1.3); },
});

// =====================================================================================
// 3:47 — FINALE: asta chiusa, nessun lotto venduto
// =====================================================================================
const FIN12 = ['vesuvio', 'panni', 'vespa', 'pizza', 'globe', 'caffe', 'balcone', 'chitarra', 'voce', 'vicolo', 'moda', 'maschera'];
const fs1 = stamp(snap(228.8), 16), fs2 = stamp(snap(233.6), 16), fs3 = stamp(snap(236.8), 16);
addScene({
  a: 227.4, b: 239.7, kind: 'page', url: '/asta/napoli/esito', tab: 'vendesi.napoli · Asta chiusa',
  html: `<div style="position:absolute;left:100px;top:40px;font-size:40px;font-weight:800">Asta NAPOLI · <span style="color:#d7261e">CHIUSA</span></div>
    <div class="big" style="position:absolute;right:100px;top:16px;font-size:120px;color:#111;letter-spacing:2px">LOTTI VENDUTI: <span style="color:#d7261e">0</span> SU 90</div>
    ${FIN12.map((k, i) => { const x = 100 + (i % 6) * 290, y = 190 + Math.floor(i / 6) * 330; const Lo = LOT[k]; return `<div class="card" style="left:${x}px;top:${y}px;width:260px"><div class="ci" style="height:180px"><img src="${IMG(Lo.img)}"></div>
      <div class="cb" style="padding:10px 14px"><div class="cl">LOTTO ${Lo.n}</div><div class="smorfia" style="font-size:17px">${ap(Lo.sm)}</div></div></div>
      ${stampAt('NUN SE VENNE', 21, x + 130, y + 90, '', 'ms' + i)}`; }).join('')}
    <div class="veil" style="position:absolute;inset:0;background:#f5f6f8;opacity:0;z-index:20"></div>
    ${stampAt('NUN SE VENNE', 128, 950, 230, '', 'b1')}${stampAt('NUN SE PIEGA', 128, 985, 460, '', 'b2')}${stampAt('CANTA ANCORA', 128, 945, 690, '', 'b3')}`,
  update(t, lt, el, s) {
    fadeIO(s, t, 0.4, 0);
    FIN12.forEach((_, i) => stampAnim($('.ms' + i, el), t, 227.7 + i * 0.07, -12 + rnd(i + 40) * 24));
    $('.veil', el).style.opacity = 0.82 * P(t, fs1 - 0.5, fs1 - 0.1);
    stampAnim($('.b1', el), t, fs1, -6); stampAnim($('.b2', el), t, fs2, 4); stampAnim($('.b3', el), t, fs3, -3);
  },
});
cine(239.3, 245.1, 'real_gulf', { fin: 0.6, fout: 0.5, s0: 1.02, s1: 1.12, x1: 0, y1: -15 });

// 4:04 — carrello vuoto
addScene({
  a: 244.6, b: 250.9, kind: 'page', url: '/carrello', tab: 'vendesi.napoli · Carrello',
  html: `<div class="center" style="justify-content:flex-start;padding-top:110px">
    <svg class="cv1" width="210" height="210" viewBox="0 0 24 24"><path d="M3 4h3l2.5 11h10L21 7H7" fill="none" stroke="#14161a" stroke-width="1.4"/><circle cx="10" cy="19.5" r="1.3" fill="#14161a"/><circle cx="17" cy="19.5" r="1.3" fill="#14161a"/></svg>
    <div class="cv2 serif" style="font-size:84px;font-weight:700;margin-top:20px">Il tuo carrello è vuoto.</div>
    <div class="cv3 serif" style="font-size:46px;font-style:italic;color:#6b7280;margin-top:10px">Napule nun se venne.</div>
    <div class="cv4 btn ghost" style="width:460px;margin-top:50px;font-size:20px">Continua a non comprare</div></div>`,
  update(t, lt, el, s) {
    fadeIO(s, t, 0.5, 0);
    ['.cv1', '.cv2', '.cv3', '.cv4'].forEach((c, i) => { const k = P(t, 245.1 + i * 0.5, 245.6 + i * 0.5, E.out); const e = $(c, el); e.style.opacity = k; e.style.transform = `translateY(${(1 - k) * 24}px)`; });
  },
});

// 4:10 — lo scontrino con i titoli di coda
const RC = [
  ['c', '<b style="font-size:34px">vendesi.napoli</b>'], ['c', 'SCONTRINO NON FISCALE'], ['c', '08/10/2026 · cassa 90'], ['hr'],
  ['r', "LOTTO 55 'a museca", 'NUN SE VENNE'], ['r', "LOTTO 42 'o ccafè", 'NUN SE VENNE'], ['r', "LOTTO 43 'o barcone", 'NUN SE VENNE'],
  ['r', "LOTTO 1 ll'Italia", 'NUN SE VENNE'], ['r', "LOTTO 80 'a vocca", 'NUN SE VENNE'], ['r', 'LOTTO 75 Pullecenella', 'NUN SE VENNE'],
  ['r', 'LOTTO UNICO Napoli', 'NUN SE VENNE'], ['hr'],
  ['r', '<b>TOTALE</b>', '<b>0,00 €</b>'], ['r', '<b>RESTO</b>', '<b>TUTTO</b>'], ['hr'],
  ['c', '<b style="font-size:38px">NAPULE NUN SE VENNE</b>'], ['c', `musica e testo: ${ARTIST}`], ['sp'],
  ['c', 'Napule nun se piega.'], ['c', 'Napule canta ancora.'], ['sp'], ['c', 'Grazie e arrivederci.'], ['bar'],
];
const rcHTML = RC.map(r => {
  if (r[0] === 'hr') return '<div style="border-top:3px dashed #555;margin:16px 0"></div>';
  if (r[0] === 'sp') return '<div style="height:18px"></div>';
  if (r[0] === 'bar') return `<div style="display:flex;justify-content:center;gap:3px;margin-top:20px;height:90px">${Array.from({ length: 46 }, (_, i) => `<div style="width:${1 + Math.floor(rnd(i) * 4)}px;background:#111"></div>`).join('')}</div>`;
  if (r[0] === 'c') return `<div style="text-align:center;line-height:1.5">${r[1]}</div>`;
  return `<div style="display:flex;justify-content:space-between;line-height:1.6"><span>${ap(r[1])}</span><span>${r[2]}</span></div>`;
}).join('');
addScene({
  a: 250.6, b: 262.95, kind: 'full',
  html: `<div style="position:absolute;inset:0;background:radial-gradient(ellipse at 50% 30%,#2a2420,#0d0b0a 75%)"></div>
    <div class="paper" style="position:absolute;left:590px;top:1080px;width:740px;background:#fbfaf6;color:#151515;font-family:Receipt;font-size:26px;padding:50px 46px 60px;box-shadow:0 40px 100px rgba(0,0,0,.6)">${rcHTML}</div>`,
  update(t, lt, el, s) {
    fadeIO(s, t, 0.4, 0);
    const pp = $('.paper', el);
    const h = s.ph || (s.ph = pp.offsetHeight);
    pp.style.top = `${lerp(1080, 1080 - h - 70, P(t, 250.9, 259.0, E.inOut))}px`;
  },
});

// ---------------------------------------------------------------- sottotitoli
Object.assign(SUBS, {
  9: 'ui', 12: 'cine', 13: 'ui', 16: 'ui', 17: 'ui', 19: 'ui', 20: 'cine', 21: 'cine', 22: 'cine', 23: 'cine',
  25: 'ui', 27: 'ui', 28: 'cine', 29: 'ui', 33: 'cine', 34: 'cine', 35: 'cine', 36: 'cine', 38: 'cine', 39: 'cine', 43: 'cine',
});
