import { EditorView, basicSetup } from 'codemirror';
import { json } from '@codemirror/lang-json';
import { oneDark } from '@codemirror/theme-one-dark';
import { search, openSearchPanel } from '@codemirror/search';
import { keymap } from '@codemirror/view';
import { EditorState, Compartment } from '@codemirror/state';
import { indentUnit } from '@codemirror/language';
import { undo, redo } from '@codemirror/commands';
import { foldAll, unfoldAll } from '@codemirror/language';

const themeCompartment = new Compartment();
const wrapCompartment = new Compartment();
const readOnlyCompartment = new Compartment();

/**
 * Create a CodeMirror 6 editor instance.
 * @param {HTMLElement} parent - DOM element to attach the editor to
 * @param {string} content - Initial editor content
 * @param {object} options
 * @param {boolean} options.readOnly - Whether the editor is read-only
 * @param {function} options.onUpdate - Called with EditorView on doc changes
 * @returns {EditorView}
 */
export function createEditor(parent, content, { readOnly = false, onUpdate } = {}) {
  const extensions = [
    basicSetup,
    json(),
    search(),
    indentUnit.of('  '),
    themeCompartment.of([]),
    wrapCompartment.of([]),
    readOnlyCompartment.of(EditorState.readOnly.of(readOnly)),
  ];

  if (onUpdate) {
    extensions.push(
      EditorView.updateListener.of((update) => {
        if (update.docChanged) {
          onUpdate(update.view);
        }
      })
    );
  }

  const view = new EditorView({
    state: EditorState.create({
      doc: content,
      extensions,
    }),
    parent,
  });

  return view;
}

/**
 * Get the full document text from an editor.
 */
export function getText(view) {
  return view.state.doc.toString();
}

/**
 * Replace the full document text.
 */
export function setText(view, text) {
  view.dispatch({
    changes: { from: 0, to: view.state.doc.length, insert: text },
  });
}

/**
 * Get cursor line and column (1-based).
 */
export function getCursorPosition(view) {
  const pos = view.state.selection.main.head;
  const line = view.state.doc.lineAt(pos);
  return { line: line.number, col: pos - line.from + 1 };
}

/**
 * Toggle dark theme on/off for an editor.
 */
export function setDarkTheme(view, dark) {
  view.dispatch({
    effects: themeCompartment.reconfigure(dark ? oneDark : []),
  });
}

/**
 * Toggle word wrap on/off for an editor.
 */
export function setWordWrap(view, wrap) {
  view.dispatch({
    effects: wrapCompartment.reconfigure(wrap ? EditorView.lineWrapping : []),
  });
}

/**
 * Trigger the find/replace panel.
 */
export function openFind(view) {
  openSearchPanel(view);
}

/**
 * Perform undo.
 */
export function doUndo(view) {
  undo(view);
}

/**
 * Perform redo.
 */
export function doRedo(view) {
  redo(view);
}
