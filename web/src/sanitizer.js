/**
 * Port of InwonerplanSanitizer.java — regex cleanup + deduplication logic.
 */

// Pre-compiled regex patterns for JSON cleanup
const NONE_PATTERN = /None/g;
const TRUE_PATTERN = /True/g;
const NANOSECONDS_PATTERN = /(\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}\.\d{3})\d*(Z?)/g;
const GMT_PATTERN = /\[GMT]/g;
const MIDNIGHT_PATTERN = /00:00:00\.00000Z/g;
const TIMEZONE_OFFSET_PATTERN = /\+\d\d:?\d\d/g;

/**
 * Clean up malformed JSON values using regex patterns.
 */
export function cleanupJson(text) {
  let json = text.replace(NONE_PATTERN, 'null');
  json = json.replace(TRUE_PATTERN, 'true');
  json = json.replace(NANOSECONDS_PATTERN, '$1Z');
  json = json.replace(GMT_PATTERN, '');
  json = json.replace(MIDNIGHT_PATTERN, '00:00:01.001Z');
  json = json.replace(TIMEZONE_OFFSET_PATTERN, 'Z');
  return json;
}

/**
 * Sanitize and deduplicate an inwonerplan JSON string.
 * Returns the sanitized JSON string.
 * Throws on parse failure.
 */
export function sanitizeInwonerplan(text) {
  const cleaned = cleanupJson(text);
  const obj = JSON.parse(cleaned);

  // Deduplicate aanbod and activiteiten per subdoel
  if (obj.inwonerplan?.doelen) {
    for (const doel of obj.inwonerplan.doelen) {
      if (doel.subdoelen) {
        for (const subdoel of doel.subdoelen) {
          if (subdoel.aanbod) {
            subdoel.aanbod = deduplicateAanbod(subdoel.aanbod);
          }
          if (subdoel.activiteiten) {
            subdoel.activiteiten = deduplicateActiviteiten(subdoel.activiteiten);
          }
        }
      }
    }
  }

  // Subdoel deduplication: for active doelen, remove completed duplicates
  if (obj.inwonerplan?.doelen) {
    for (const doel of obj.inwonerplan.doelen) {
      if (String(doel.codeStatusDoel).toLowerCase() === '1' && doel.subdoelen) {
        const result = [];
        for (const s of doel.subdoelen) {
          const count = doel.subdoelen.filter((other) => subdoelEquals(other, s)).length;
          if (count > 1) {
            // Has duplicates: only keep non-completed ones
            if (String(s.codeStatusSubdoel).toLowerCase() !== '2') {
              result.push(s);
            }
          } else {
            // Unique subdoel: always keep
            result.push(s);
          }
        }
        doel.subdoelen = result;
      }
    }
  }

  return JSON.stringify(obj, null, 2);
}

function keyPart(s) {
  return s != null ? String(s).toLowerCase() : '\0';
}

function aanbodKey(a) {
  return keyPart(a.codeAanbod) + '|' + keyPart(a.codeRedenStatusAanbod) + '|' + keyPart(a.codeResultaatAanbod);
}

function deduplicateAanbod(list) {
  const seen = new Set();
  const result = [];
  for (const a of list) {
    if (a == null) {
      result.push(null);
      continue;
    }
    const key = aanbodKey(a);
    if (!seen.has(key)) {
      seen.add(key);
      result.push(a);
    }
  }
  return result;
}

function deduplicateActiviteiten(list) {
  const seenUuids = new Set();
  const seenCodes = new Set();
  const result = [];
  for (const a of list) {
    if (a == null) {
      result.push(null);
      continue;
    }
    let isDuplicate = false;
    const uuidKey = a.uuid != null ? String(a.uuid).toLowerCase() : null;
    const codeKey =
      a.codeAanbod != null && a.codeAanbodactiviteit != null
        ? String(a.codeAanbod).toLowerCase() + '|' + String(a.codeAanbodactiviteit).toLowerCase()
        : null;

    if (uuidKey != null && seenUuids.has(uuidKey)) {
      isDuplicate = true;
    }
    if (!isDuplicate && codeKey != null && seenCodes.has(codeKey)) {
      isDuplicate = true;
    }

    if (!isDuplicate) {
      if (uuidKey != null) seenUuids.add(uuidKey);
      if (codeKey != null) seenCodes.add(codeKey);
      result.push(a);
    }
  }
  return result;
}

function subdoelEquals(s1, s2) {
  if (
    s1.aandachtspuntId != null &&
    s2.aandachtspuntId != null &&
    String(s1.aandachtspuntId).toLowerCase() === String(s2.aandachtspuntId).toLowerCase()
  ) {
    return true;
  }
  if (
    s1.ontwikkelwensId != null &&
    s2.ontwikkelwensId != null &&
    String(s1.ontwikkelwensId).toLowerCase() === String(s2.ontwikkelwensId).toLowerCase()
  ) {
    return true;
  }
  return false;
}
