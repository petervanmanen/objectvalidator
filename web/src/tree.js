/**
 * Build a collapsible HTML tree from parsed JSON using <details>/<summary>.
 */

const MAX_DEPTH = 50;
const DEFAULT_EXPAND_DEPTH = 3;

/**
 * Render a JSON value as a collapsible tree into a container element.
 * @param {HTMLElement} container - Element to render into (will be cleared)
 * @param {*} data - Parsed JSON value
 * @param {number} expandDepth - How many levels to expand by default
 */
export function renderTree(container, data, expandDepth = DEFAULT_EXPAND_DEPTH) {
  container.innerHTML = '';
  const tree = buildNode(data, 'Object', 0, expandDepth);
  container.appendChild(tree);
}

function buildNode(value, key, depth, expandDepth) {
  if (depth > MAX_DEPTH) {
    const el = document.createElement('div');
    el.className = 'tree-leaf';
    el.innerHTML = `<span class="tree-key">${escapeHtml(key)}</span>: <span class="tree-null">[depth limit reached]</span>`;
    return el;
  }

  if (value === null) {
    return createLeaf(key, '<span class="tree-null">null</span>');
  }

  if (Array.isArray(value)) {
    const details = document.createElement('details');
    if (depth < expandDepth) details.open = true;

    const summary = document.createElement('summary');
    summary.innerHTML = `<span class="tree-key">${escapeHtml(key)}</span> <span class="tree-count">[${value.length}]</span>`;
    details.appendChild(summary);

    for (let i = 0; i < value.length; i++) {
      details.appendChild(buildNode(value[i], `[${i}]`, depth + 1, expandDepth));
    }
    return details;
  }

  if (typeof value === 'object') {
    const entries = Object.entries(value);
    const details = document.createElement('details');
    if (depth < expandDepth) details.open = true;

    const summary = document.createElement('summary');
    summary.innerHTML = `<span class="tree-key">${escapeHtml(key)}</span> <span class="tree-count">{${entries.length}}</span>`;
    details.appendChild(summary);

    for (const [k, v] of entries) {
      details.appendChild(buildNode(v, k, depth + 1, expandDepth));
    }
    return details;
  }

  // Primitive value
  const cls = typeof value === 'number' ? 'tree-number'
    : typeof value === 'boolean' ? 'tree-bool'
    : 'tree-string';

  const display = typeof value === 'string' ? `"${escapeHtml(value)}"` : String(value);
  return createLeaf(key, `<span class="${cls}">${display}</span>`);
}

function createLeaf(key, valueHtml) {
  const div = document.createElement('div');
  div.className = 'tree-leaf';
  div.innerHTML = `<span class="tree-key">${escapeHtml(key)}</span>: ${valueHtml}`;
  return div;
}

function escapeHtml(text) {
  const div = document.createElement('div');
  div.textContent = text;
  return div.innerHTML;
}
