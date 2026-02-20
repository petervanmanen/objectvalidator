# Inwonerplan Validator

A browser-based JSON Schema validator, editor, and sanitizer for **Inwonerplan** (resident plan) data, built for the Municipality of Utrecht. Runs entirely client-side with no server component.

**Live:** [petervanmanen.github.io/objectvalidator](https://petervanmanen.github.io/objectvalidator/)

## Features

- **JSON editing** with syntax highlighting, code folding, and line numbers (CodeMirror 6)
- **Schema validation** against the Inwonerplan JSON Schema (Draft-07)
- **Domain validation** — checks required fields (zaaknummer, inwonerprofielId, doelen)
- **Sanitization** — deduplicates doelen, subdoelen, aanbod, and activiteiten; fixes malformed timestamps
- **Tree visualization** — collapsible tree view of JSON structure
- **Dark/light theme** toggle
- **File operations** — open, save, format JSON
- **Keyboard shortcuts** — Ctrl+S (save), F5 (validate), F6 (sanitize), F7 (visualize)

## Getting Started

Requires Node.js >= 18.

```bash
cd web
npm install
npm run dev   # opens at http://localhost:5173
```

To build for production:

```bash
cd web
npm run build   # output in web/dist/
```

## Project Structure

```
web/src/
  main.js        — App entry point, UI wiring
  editor.js      — CodeMirror 6 JSON editor
  validator.js   — Ajv schema + domain validation
  sanitizer.js   — Regex cleanup + deduplication
  tree.js        — Collapsible JSON tree view
  style.css      — Light/dark theme styling
  data/
    inwonerplan.schema.json — JSON Schema (Draft-07)
    inwonerplan.json        — Sample data
```

## Tech Stack

- **Editor:** CodeMirror 6
- **Schema validation:** Ajv
- **Build tool:** Vite
- **UI:** Vanilla JS + CSS custom properties

## License

Apache License 2.0
