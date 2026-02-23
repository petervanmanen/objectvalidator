import './style.css';
import {
  createEditor,
  getText,
  setText,
  getCursorPosition,
  setDarkTheme,
  setWordWrap,
  openFind,
  doUndo,
  doRedo,
} from './editor.js';
import { sanitizeInwonerplan } from './sanitizer.js';
import { validateInwonerplan } from './validator.js';
import { renderTree } from './tree.js';
import defaultData from './data/inwonerplan.json';
import schemaData from './data/inwonerplan.schema.json';

// ---- State ----
let currentFileName = null;
let modified = false;
let objectEditor = null;
let schemaEditor = null;

// ---- DOM References ----
const $ = (sel) => document.querySelector(sel);

// ---- Initialize ----
document.addEventListener('DOMContentLoaded', init);

function init() {
  // Create editors
  objectEditor = createEditor($('#editor-object'), JSON.stringify(defaultData, null, 2), {
    onUpdate: () => markModified(),
  });

  schemaEditor = createEditor($('#editor-schema'), JSON.stringify(schemaData, null, 2), {
    readOnly: true,
  });

  // Cursor position tracking
  objectEditor.dom.addEventListener('keyup', updateCursor);
  objectEditor.dom.addEventListener('mouseup', updateCursor);

  // Wire buttons
  $('#btn-format').addEventListener('click', doFormat);
  $('#btn-validate').addEventListener('click', doValidate);
  $('#btn-sanitize').addEventListener('click', doSanitize);
  $('#btn-visualize').addEventListener('click', doVisualize);

  // Wire tabs
  wireTabBar('.left-panel .tab-bar', '.left-panel .tab-content');
  wireTabBar('.right-panel .tab-bar', '.right-panel .tab-content');

  // Wire menu
  wireMenuBar();

  // Wire file input
  $('#file-input').addEventListener('change', handleFileOpen);

  // Wire resize handle
  wireResize();

  // Wire keyboard shortcuts
  document.addEventListener('keydown', handleKeydown);

  // Restore saved preferences
  restorePreferences();
}

// ---- Tab Switching ----

function wireTabBar(barSelector, contentSelector) {
  const bar = $(barSelector);
  const content = $(contentSelector);
  bar.addEventListener('click', (e) => {
    const tab = e.target.closest('.tab');
    if (!tab) return;

    bar.querySelectorAll('.tab').forEach((t) => t.classList.remove('active'));
    tab.classList.add('active');

    const tabName = tab.dataset.tab;
    content.querySelectorAll('.tab-pane').forEach((p) => p.classList.remove('active'));
    $(`#tab-${tabName}`).classList.add('active');
  });
}

// ---- Menu Bar ----

function wireMenuBar() {
  let openMenu = null;

  document.querySelectorAll('.menu-item').forEach((item) => {
    const trigger = item.querySelector('.menu-trigger');
    trigger.addEventListener('click', (e) => {
      e.stopPropagation();
      if (openMenu === item) {
        closeMenus();
      } else {
        closeMenus();
        item.classList.add('open');
        openMenu = item;
      }
    });

    // Hover-to-switch when a menu is already open
    item.addEventListener('mouseenter', () => {
      if (openMenu && openMenu !== item) {
        closeMenus();
        item.classList.add('open');
        openMenu = item;
      }
    });
  });

  document.addEventListener('click', closeMenus);

  function closeMenus() {
    document.querySelectorAll('.menu-item.open').forEach((m) => m.classList.remove('open'));
    openMenu = null;
  }

  // Menu actions
  document.querySelectorAll('.menu-dropdown button[data-action]').forEach((btn) => {
    btn.addEventListener('click', (e) => {
      e.stopPropagation();
      closeMenus();
      const action = btn.dataset.action;
      switch (action) {
        case 'new': doNew(); break;
        case 'open': doOpen(); break;
        case 'save': doSave(); break;
        case 'save-as': doSaveAs(); break;
        case 'undo': doUndo(objectEditor); break;
        case 'redo': doRedo(objectEditor); break;
        case 'find': openFind(objectEditor); break;
        case 'format': doFormat(); break;
        case 'validate': doValidate(); break;
        case 'sanitize': doSanitize(); break;
        case 'visualize': doVisualize(); break;
        case 'about': $('#about-dialog').showModal(); break;
      }
    });
  });

  // View toggles
  $('#darkThemeToggle').addEventListener('change', (e) => {
    applyDarkTheme(e.target.checked);
  });

  $('#wordWrapToggle').addEventListener('change', (e) => {
    applyWordWrap(e.target.checked);
  });
}

// ---- Keyboard Shortcuts ----

function handleKeydown(e) {
  const mod = e.ctrlKey || e.metaKey;

  if (e.key === 'F5') {
    e.preventDefault();
    doValidate();
  } else if (e.key === 'F6') {
    e.preventDefault();
    doSanitize();
  } else if (e.key === 'F7') {
    e.preventDefault();
    doVisualize();
  } else if (mod && e.shiftKey && e.key.toLowerCase() === 'f') {
    e.preventDefault();
    doFormat();
  } else if (mod && e.shiftKey && e.key.toLowerCase() === 's') {
    e.preventDefault();
    doSaveAs();
  } else if (mod && e.key.toLowerCase() === 's' && !e.shiftKey) {
    e.preventDefault();
    doSave();
  } else if (mod && e.key.toLowerCase() === 'n') {
    e.preventDefault();
    doNew();
  } else if (mod && e.key.toLowerCase() === 'o') {
    e.preventDefault();
    doOpen();
  }
}

// ---- Resize Handle ----

function wireResize() {
  const handle = $('#resize-handle');
  const container = $('.main-container');
  const leftPanel = $('.left-panel');
  const rightPanel = $('.right-panel');

  let startX, startLeftWidth;

  handle.addEventListener('mousedown', (e) => {
    e.preventDefault();
    startX = e.clientX;
    startLeftWidth = leftPanel.offsetWidth;
    handle.classList.add('dragging');
    document.addEventListener('mousemove', onDrag);
    document.addEventListener('mouseup', onDragEnd);
  });

  function onDrag(e) {
    const dx = e.clientX - startX;
    const containerWidth = container.offsetWidth - handle.offsetWidth;
    const newLeftWidth = Math.max(200, Math.min(containerWidth - 200, startLeftWidth + dx));
    const leftPct = (newLeftWidth / container.offsetWidth) * 100;
    const rightPct = 100 - leftPct - (handle.offsetWidth / container.offsetWidth) * 100;
    leftPanel.style.flex = `0 0 ${leftPct}%`;
    rightPanel.style.flex = `0 0 ${rightPct}%`;
  }

  function onDragEnd() {
    handle.classList.remove('dragging');
    document.removeEventListener('mousemove', onDrag);
    document.removeEventListener('mouseup', onDragEnd);
  }
}

// ---- File Operations ----

function doNew() {
  setText(objectEditor, '');
  currentFileName = null;
  modified = false;
  updateTitle();
  setStatus('New file');
}

function doOpen() {
  $('#file-input').click();
}

function handleFileOpen(e) {
  const file = e.target.files[0];
  if (!file) return;

  const reader = new FileReader();
  reader.onload = (ev) => {
    setText(objectEditor, ev.target.result);
    currentFileName = file.name;
    modified = false;
    updateTitle();
    $('#status-file').textContent = file.name;
    setStatus('Opened ' + file.name);
  };
  reader.readAsText(file);
  // Reset so the same file can be re-opened
  e.target.value = '';
}

function doSave() {
  doSaveAs();
}

function doSaveAs() {
  const text = getText(objectEditor);
  const name = currentFileName || 'inwonerplan.json';
  const blob = new Blob([text], { type: 'application/json' });
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = name;
  a.click();
  URL.revokeObjectURL(url);
  currentFileName = name;
  modified = false;
  updateTitle();
  $('#status-file').textContent = name;
  setStatus('Saved ' + name);
}

// ---- Actions ----

function doFormat() {
  const text = getText(objectEditor);
  try {
    const obj = JSON.parse(text);
    setText(objectEditor, JSON.stringify(obj, null, 2));
    setStatus('Formatted');
  } catch (e) {
    setStatus('Invalid JSON provided!');
  }
}

function doValidate() {
  const text = getText(objectEditor);
  if (!text.trim()) {
    setStatus('Please enter valid JSON text!');
    return;
  }

  const results = validateInwonerplan(text);
  const output = $('#validation-output');
  output.innerHTML = '';

  for (const line of results) {
    const span = document.createElement('span');
    span.textContent = line + '\n';
    if (line.includes('successfully') || line === 'Validatie ok') {
      span.className = 'success';
    } else if (!line.startsWith('JSON validated')) {
      span.className = 'error';
    }
    output.appendChild(span);
  }

  // Switch to validation tab
  switchRightTab('validation');

  const hasSuccess = results.some((r) => r.includes('successfully'));
  if (hasSuccess) {
    setStatus('Validation passed');
  } else {
    setStatus(results.length + ' validation issue(s)');
  }
}

function doSanitize() {
  const text = getText(objectEditor);
  try {
    const result = sanitizeInwonerplan(text);
    setText(objectEditor, result);
    setStatus('Sanitized');
  } catch (e) {
    setStatus('Error: ' + e.message);
  }
}

function doVisualize() {
  const text = getText(objectEditor);
  if (!text.trim()) return;

  try {
    const data = JSON.parse(text);
    renderTree($('#json-tree'), data);
    switchRightTab('tree');
    setStatus('Tree visualized');
  } catch (e) {
    setStatus('Invalid JSON provided!');
  }
}

// ---- Helpers ----

function switchRightTab(tabName) {
  const bar = document.querySelector('.right-panel .tab-bar');
  bar.querySelectorAll('.tab').forEach((t) => {
    t.classList.toggle('active', t.dataset.tab === tabName);
  });
  document.querySelectorAll('.right-panel .tab-pane').forEach((p) => p.classList.remove('active'));
  $(`#tab-${tabName}`).classList.add('active');
}

function markModified() {
  if (!modified) {
    modified = true;
    updateTitle();
  }
}

function updateTitle() {
  const name = currentFileName || 'Untitled';
  const mod = modified ? ' *' : '';
  document.title = `Inwonerplan Editor — ${name}${mod}`;
}

function updateCursor() {
  const pos = getCursorPosition(objectEditor);
  $('#status-cursor').textContent = `Ln ${pos.line}, Col ${pos.col}`;
}

function setStatus(message) {
  $('#status-message').textContent = message;
}

// ---- Preferences (localStorage) ----

function applyDarkTheme(dark) {
  document.documentElement.setAttribute('data-theme', dark ? 'dark' : 'light');
  setDarkTheme(objectEditor, dark);
  setDarkTheme(schemaEditor, dark);
  localStorage.setItem('darkTheme', dark);
}

function applyWordWrap(wrap) {
  setWordWrap(objectEditor, wrap);
  setWordWrap(schemaEditor, wrap);
  localStorage.setItem('wordWrap', wrap);
}

function restorePreferences() {
  const dark = localStorage.getItem('darkTheme') === 'true';
  if (dark) {
    $('#darkThemeToggle').checked = true;
    applyDarkTheme(true);
  }

  const wrap = localStorage.getItem('wordWrap') === 'true';
  if (wrap) {
    $('#wordWrapToggle').checked = true;
    applyWordWrap(true);
  }
}
