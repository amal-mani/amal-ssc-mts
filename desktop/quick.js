// Pop-up quiz for the PC: during work hours a small box with one question appears in the screen corner.
// The study app sends a pack of questions (all subjects + current affairs); answers go back to it.
const { app, BrowserWindow, ipcMain, screen } = require('electron');
const path = require('path');
const fs = require('fs');
const FILE = () => path.join(app.getPath('userData'), 'quick.json');
let S = { cfg: { on: false }, qs: [], idx: 0, res: [], nextAt: 0 };
let pop = null, popQ = null, tick = null;

function load() { try { S = Object.assign(S, JSON.parse(fs.readFileSync(FILE(), 'utf8'))); } catch (e) {} }
function store() { try { fs.writeFileSync(FILE(), JSON.stringify(S)); } catch (e) {} }
const mins = (hm, d) => { const m = /^(\d+):(\d+)$/.exec(hm || ''); return m ? +m[1] * 60 + +m[2] : d; };
const dayOn = (cfg, dow) => Array.isArray(cfg.days) ? cfg.days.includes(dow) : dow !== 0;

/** time of the next pop-up inside the work window, or 0 when off */
function nextTime(cfg, now) {
  if (!cfg || !cfg.on) return 0;
  const from = mins(cfg.from, 600), to = mins(cfg.to, 1020), every = Math.max(15, +cfg.every || 60);
  if (to <= from) return 0;
  for (let d = 0; d < 8; d++) {
    const day = new Date(now); day.setHours(0, 0, 0, 0); day.setDate(day.getDate() + d);
    if (!dayOn(cfg, day.getDay())) continue;
    const start = day.getTime() + from * 60000, end = day.getTime() + to * 60000;
    const t = d === 0 && now >= start ? now + every * 60000 : start;
    if (t <= end && t > now) return t;
  }
  return 0;
}

function nextQ() {
  if (!S.qs.length) return null;
  const q = S.qs[S.idx % S.qs.length]; S.idx = (S.idx + 1) % S.qs.length; store(); return q;
}

function show(q) {
  if (!q) return;
  popQ = q;
  if (pop && !pop.isDestroyed()) { pop.webContents.send('qq-question', q); pop.showInactive(); return; }
  const wa = screen.getPrimaryDisplay().workArea, W = 420, H = 560;
  pop = new BrowserWindow({ width: W, height: H, x: wa.x + wa.width - W - 16, y: wa.y + wa.height - H - 16,
    frame: false, resizable: false, alwaysOnTop: true, skipTaskbar: true, show: false, backgroundColor: '#171b23',
    icon: path.join(__dirname, 'build', 'icon.png'), title: 'Quick question',
    webPreferences: { contextIsolation: true, sandbox: true, preload: path.join(__dirname, 'quickPreload.js') } });
  pop.setAlwaysOnTop(true, 'floating');
  pop.loadFile(path.join(__dirname, 'quick.html'));
  pop.once('ready-to-show', () => { pop.showInactive(); });
  pop.on('closed', () => { pop = null; popQ = null; });
  // close by itself after 10 minutes if not answered
  const p = pop; setTimeout(() => { if (p && !p.isDestroyed() && !p.__answered) p.close(); }, 10 * 60000);
}

function check() {
  const now = Date.now();
  if (!S.cfg.on) return;
  if (!S.nextAt || S.nextAt < now - 3 * 3600000) { S.nextAt = nextTime(S.cfg, now); store(); return; }
  if (now >= S.nextAt) {
    const d = new Date(now), m = d.getHours() * 60 + d.getMinutes();
    if (dayOn(S.cfg, d.getDay()) && m >= mins(S.cfg.from, 600) - 1 && m <= mins(S.cfg.to, 1020)) show(nextQ());
    S.nextAt = nextTime(S.cfg, now + 1000); store();
  }
}

function setup(onChange) {
  load();
  ipcMain.on('mts-quick-set', (e, cfg, qs) => {
    try {
      const c = JSON.parse(cfg), q = JSON.parse(qs);
      const changed = JSON.stringify(c) !== JSON.stringify(S.cfg);
      S.cfg = c; if (Array.isArray(q) && q.length) { S.qs = q; S.idx = 0; }
      if (changed) S.nextAt = nextTime(c, Date.now());
      store(); if (onChange) onChange(S.cfg);
    } catch (err) {}
  });
  ipcMain.handle('mts-quick-take', () => { const r = S.res; S.res = []; store(); return r; });
  ipcMain.on('mts-quick-now', () => show(nextQ()));
  // pop-up window
  ipcMain.on('qq-get', e => { e.returnValue = popQ; });
  ipcMain.on('qq-answer', (e, i) => {
    if (!popQ) return;
    const d = new Date(), pad = n => String(n).padStart(2, '0');
    const r = Object.assign({}, popQ, { i: +i, at: `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}` });
    S.res.push(r); if (S.res.length > 300) S.res = S.res.slice(-300); store();
    if (pop && !pop.isDestroyed()) pop.__answered = true;
  });
  ipcMain.on('qq-more', () => { const q = nextQ(); if (q) show(q); });
  ipcMain.on('qq-close', () => { if (pop && !pop.isDestroyed()) pop.close(); });
  ipcMain.on('qq-focus', () => { if (pop && !pop.isDestroyed()) pop.focus(); });
  clearInterval(tick); tick = setInterval(check, 30000); setTimeout(check, 5000);
}

module.exports = { setup, isOn: () => !!(S.cfg && S.cfg.on), now: () => show(nextQ()), nextTime };
