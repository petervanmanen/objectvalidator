# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Inwonerplan Validator is a browser-based application for validating, editing, and sanitizing JSON documents against a JSON Schema. It is built for the Municipality of Utrecht to work with "Inwonerplan" (resident plan) data — a domain model of goals (doelen), sub-goals (subdoelen), offers (aanbod), and activities (activiteiten). The app runs entirely client-side with no server component.

## Build & Run Commands

```bash
# Install dependencies
cd web && npm install

# Run dev server (http://localhost:5173)
cd web && npm run dev

# Production build
cd web && npm run build

# Preview production build
cd web && npm run preview
```

Requires Node.js >= 18. Use `nvm use 20` if using nvm (`.nvmrc` is in `web/`).

## Architecture

**Source files** in `web/src/`:
- `main.js` — App entry: wires UI, keyboard shortcuts, file operations, menu bar, tabs, resize handle
- `editor.js` — CodeMirror 6 setup (JSON mode, dark/light themes, word wrap, find/replace)
- `validator.js` — JSON Schema validation (Ajv, Draft-07) + domain-specific validation rules
- `sanitizer.js` — Sanitization logic: regex cleanup of malformed values + deduplication of doelen, subdoelen, aanbod, and activiteiten
- `tree.js` — JSON → collapsible HTML tree using `<details>/<summary>` elements
- `style.css` — All styling with light/dark themes via CSS custom properties

**Data files** in `web/src/data/`:
- `inwonerplan.schema.json` — JSON Schema (Draft-07) defining the Inwonerplan structure
- `inwonerplan.json` — Sample data loaded as default in the editor

## Key Technical Details

- **Vite** for dev server and production builds
- **CodeMirror 6** for JSON editing with syntax highlighting, code folding, line numbers
- **Ajv** for JSON Schema validation (Draft-07) with format validation via `ajv-formats`
- **No framework** — vanilla JS + HTML + CSS
- **Domain language** is Dutch — field names and validation messages are in Dutch
- **GitHub Pages** deployment via `.github/workflows/deploy-pages.yml`
- **Base path** is `/objectvalidator/` (set in `web/vite.config.js` for GitHub Pages)
