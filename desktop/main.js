// MTS Study for Windows. The whole study app is inside the program, so it works offline.
// When online it downloads the daily updates from GitHub (data/ folder) and keeps them.
const { app, BrowserWindow, shell, Menu, protocol } = require('electron');
const path = require('path');
const fs = require('fs');
const WWW = path.join(__dirname, 'www');
protocol.registerSchemesAsPrivileged([{ scheme: 'mts', privileges: { standard: true, secure: true, supportFetchAPI: true, corsEnabled: true } }]);
if (!app.requestSingleInstanceLock()) app.quit();
const TYPES = { '.html': 'text/html', '.json': 'application/json', '.png': 'image/png', '.js': 'text/javascript' };
let win;
function create() {
  win = new BrowserWindow({ width: 1280, height: 860, minWidth: 380, backgroundColor: '#0e1014', title: 'MTS Study',
    icon: path.join(__dirname, 'build', 'icon.png'), autoHideMenuBar: true,
    webPreferences: { partition: 'persist:mts', contextIsolation: true, sandbox: true } });
  win.loadURL('mts://app/index.html');
  const outside = url => {
    if (!/^https?:/.test(url)) return;
    let host = ''; try { host = new URL(url).hostname; } catch (e) {}
    // Falcon (Classplus) and Testbook open in a window inside the app; your login is kept.
    if (/(^|\.)(classplusapp|testbook)\.com$/.test(host)) return openInside(url);
    shell.openExternal(url);
  };
  win.webContents.setWindowOpenHandler(({ url }) => { outside(url); return { action: 'deny' }; });
  win.webContents.on('will-navigate', (e, url) => { if (!url.startsWith('mts://')) { e.preventDefault(); outside(url); } });
  win.on('page-title-updated', e => e.preventDefault());
}
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
  { type: 'separator' }, { role: 'quit', label: 'Close' }] }]));
app.on('second-instance', () => { if (win) { if (win.isMinimized()) win.restore(); win.focus(); } });
app.whenReady().then(() => {
  app.setAppUserModelId('com.amalmani.mtsstudy');
  require('electron').session.fromPartition('persist:mts').protocol.handle('mts', req => {
    const name = path.basename(decodeURIComponent(new URL(req.url).pathname)) || 'index.html';
    const f = path.join(WWW, name);
    if (!fs.existsSync(f)) return new Response('Not found', { status: 404 });
    return new Response(fs.readFileSync(f), { headers: { 'content-type': TYPES[path.extname(f)] || 'application/octet-stream' } });
  });
  create();
});
app.on('window-all-closed', () => app.quit());
