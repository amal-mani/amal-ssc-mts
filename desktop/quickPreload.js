// Gives the pop-up quiz window: get the question, send the answer, one more, close.
const { contextBridge, ipcRenderer } = require('electron');
contextBridge.exposeInMainWorld('qq', {
  get: () => ipcRenderer.sendSync('qq-get'),
  sound: () => ipcRenderer.sendSync('qq-sound'),
  answer: i => ipcRenderer.send('qq-answer', i),
  more: () => ipcRenderer.send('qq-more'),
  close: () => ipcRenderer.send('qq-close'),
  focus: () => ipcRenderer.send('qq-focus'),
  onQuestion: fn => ipcRenderer.on('qq-question', (_e, q) => fn(q))
});
