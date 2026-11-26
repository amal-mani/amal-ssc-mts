// Gives the study page: the app version, "update now", and the pop-up quiz (send questions, get answers).
const { contextBridge, ipcRenderer } = require('electron');
contextBridge.exposeInMainWorld('mtsDesktop', {
  version: () => ipcRenderer.sendSync('mts-version'),
  update: url => ipcRenderer.invoke('mts-update', String(url)),
  onProgress: fn => ipcRenderer.on('mts-update-progress', (_e, p) => fn(p)),
  quickSet: (cfg, qs) => ipcRenderer.send('mts-quick-set', String(cfg), String(qs)),
  quickTake: () => ipcRenderer.invoke('mts-quick-take'),
  quickNow: () => ipcRenderer.send('mts-quick-now')
});
