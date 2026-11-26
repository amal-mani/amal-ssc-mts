// MTS Study for Windows. The whole study app is inside the program, so it works offline.
// When online it downloads the daily updates from GitHub (data/ folder) and keeps them.
const { app, BrowserWindow, shell, Menu, protocol, ipcMain, net, Tray, nativeImage } = require('electron');
const quick = require('./quick');
const { spawn } = require('child_process');
const path = require('path');
const fs = require('fs');
const WWW = path.join(__dirname, 'www');
protocol.registerSchemesAsPrivileged([{ scheme: 'mts', privileges: { standard: true, secure: true, supportFetchAPI: true, corsEnabled: true } }]);
if (!app.requestSingleInstanceLock()) app.quit();
const TYPES = { '.html': 'text/html', '.json': 'application/json', '.png': 'image/png', '.js': 'text/javascript' };
let win, tray, quitting = false;
const HIDDEN = process.argv.includes('--hidden');
function create() {
  win = new BrowserWindow({ width: 1280, height: 860, minWidth: 380, backgroundColor: '#0e1014', title: 'MTS Study', show: !HIDDEN,
    icon: path.join(__dirname, 'build', 'icon.png'), autoHideMenuBar: true,
    webPreferences: { partition: 'persist:mts', contextIsolation: true, sandbox: true, preload: path.join(__dirname, 'preload.js') } });
  win.loadURL('mts://app/index.html');
  const outside = url => {
    if (!/^https?:/.test(url)) return;
    let host = ''; try { host = new URL(url).hostname; } catch (e) {}
    // Falcon (Classplus) and Testbook open in a window inside the app; your login is kept.
    if (/(^|\.)(classplusapp|testbook|youtube)\.com$/.test(host) || host === "youtu.be") return openInside(url);
    shell.openExternal(url);
  };
  win.webContents.setWindowOpenHandler(({ url }) => { outside(url); return { action: 'deny' }; });
  win.webContents.on('will-navigate', (e, url) => { if (!url.startsWith('mts://')) { e.preventDefault(); outside(url); } });
  win.on('page-title-updated', e => e.preventDefault());
  // With the pop-up quiz on, closing the window keeps the app running in the tray (bottom-right, near the clock).
  win.on('close', e => { if (!quitting && quick.isOn()) { e.preventDefault(); win.hide(); trayTip(); } });
}
function showMain() { if (!win || win.isDestroyed()) create(); win.show(); if (win.isMinimized()) win.restore(); win.focus(); }
let tipped = false;
function trayTip() { if (tray && !tipped && tray.displayBalloon) { tipped = true; try { tray.displayBalloon({ title: 'MTS Study is still running', content: 'Pop-up questions will come during your work hours. Right-click this icon to open or close the app.' }); } catch (e) {} } }
function makeTray() {
  if (tray) return;
  tray = new Tray(nativeImage.createFromPath(path.join(__dirname, 'build', 'icon.png')).resize({ width: 16, height: 16 }));
  tray.setToolTip('MTS Study');
  tray.setContextMenu(Menu.buildFromTemplate([
    { label: 'Open MTS Study', click: showMain },
    { label: 'Quick question now', click: () => quick.now() },
    { type: 'separator' },
    { label: 'Close MTS Study completely', click: () => { quitting = true; app.quit(); } }]));
  tray.on('click', showMain);
}
function loginItem(on) { try { app.setLoginItemSettings({ openAtLogin: !!on, args: ['--hidden'] }); } catch (e) {} }
let inside;
function openInside(url) {
  if (inside && !inside.isDestroyed()) { inside.loadURL(url); inside.focus(); return; }
  inside = new BrowserWindow({ width: 1200, height: 820, parent: win, backgroundColor: '#0e1014', title: 'MTS Study – course and tests',
    icon: path.join(__dirname, 'build', 'icon.png'), autoHideMenuBar: true,
    webPreferences: { partition: 'persist:web', contextIsolation: true, sandbox: true } });
  inside.webContents.setWindowOpenHandler(({ url: u }) => { if (/^https?:/.test(u)) inside.loadURL(u); return { action: 'deny' }; });
  inside.loadURL(url);
}
Menu.setApplicationMenu(Menu.buildFromTemplate([{ label: 'App', submenu: [
  { role: 'reload', label: 'Refresh (get latest updates)', accelerator: 'F5' },
  { label: 'Open the course/test page in my browser', accelerator: 'F8', click: () => { if (inside && !inside.isDestroyed()) shell.openExternal(inside.webContents.getURL()); } },
  { type: 'separator' }, { role: 'zoomIn', label: 'Bigger text' }, { role: 'zoomOut', label: 'Smaller text' }, { role: 'resetZoom', label: 'Normal text size' },
  { type: 'separator' }, { label: 'Close completely', click: () => { quitting = true; app.quit(); } }] }]));
// Self-update: download the new installer from GitHub releases, run it silently, and reopen the app.
ipcMain.on('mts-version', e => { e.returnValue = app.getVersion(); });
ipcMain.handle('mts-update', async (e, url) => {
  if (!/^https:\/\/github\.com\/amal-mani\/amal-ssc-mts\/releases\//.test(url)) throw new Error('bad url');
  const res = await net.fetch(url);
  if (!res.ok) throw new Error('Download failed: ' + res.status);
  const total = +res.headers.get('content-length') || 0, parts = []; let got = 0, last = -1;
  const reader = res.body.getReader();
  for (;;) { const { done, value } = await reader.read(); if (done) break; parts.push(Buffer.from(value)); got += value.length;
    const pct = total ? Math.floor(got / total * 100) : 0; if (pct !== last) { last = pct; e.sender.send('mts-update-progress', pct); } }
  const file = path.join(app.getPath('temp'), 'MTS-Study-Setup-update.exe');
  fs.writeFileSync(file, Buffer.concat(parts));
  spawn(file, ['/S', '--force-run'], { detached: true, stdio: 'ignore' }).unref();
  setTimeout(() => { quitting = true; app.quit(); }, 800);
  return true;
});
app.on('second-instance', () => showMain());
app.whenReady().then(() => {
  app.setAppUserModelId('com.amalmani.mtsstudy');
  require('electron').session.fromPartition('persist:mts').protocol.handle('mts', req => {
    const name = path.basename(decodeURIComponent(new URL(req.url).pathname)) || 'index.html';
    const f = path.join(WWW, name);
    if (!fs.existsSync(f)) return new Response('Not found', { status: 404 });
    return new Response(fs.readFileSync(f), { headers: { 'content-type': TYPES[path.extname(f)] || 'application/octet-stream' } });
  });
  quick.setup(cfg => { loginItem(cfg.on); if (cfg.on) makeTray(); });
  if (quick.isOn()) { makeTray(); loginItem(true); }
  create();
});
app.on('before-quit', () => { quitting = true; });
app.on('window-all-closed', () => { if (!quick.isOn()) app.quit(); });
