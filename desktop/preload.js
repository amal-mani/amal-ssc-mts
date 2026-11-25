// Gives the study page two small things: the app version, and "update now".
const { contextBridge, ipcRenderer } = require('electron');
contextBridge.exposeInMainWorld('mtsDesktop', {
  version: () => ipcRenderer.sendSync('mts-version'),
  update: url => ipcRenderer.invoke('mts-update', String(url)),
  onProgress: fn => ipcRenderer.on('mts-update-progress', (_e, p) => fn(p))
});
