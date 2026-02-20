import Ajv from 'ajv';
import addFormats from 'ajv-formats';
import schema from './data/inwonerplan.schema.json';

let ajvInstance = null;
let validateFn = null;

function getValidator() {
  if (!validateFn) {
    ajvInstance = new Ajv({ allErrors: true, strict: false });
    addFormats(ajvInstance);
    validateFn = ajvInstance.compile(schema);
  }
  return validateFn;
}

/**
 * Validate JSON text against the inwonerplan schema and domain rules.
 * Returns an array of result strings.
 */
export function validateInwonerplan(jsonText) {
  const results = [];

  // Parse
  let obj;
  try {
    obj = JSON.parse(jsonText);
  } catch (e) {
    results.push(`JSON parse error: ${e.message}`);
    return results;
  }

  // Schema validation
  const validate = getValidator();
  const valid = validate(obj);
  if (valid) {
    results.push('JSON validated successfully against schema!');
  } else {
    for (const err of validate.errors) {
      const path = err.instancePath || '/';
      results.push(`${path}: ${err.message}`);
    }
  }

  // Domain validation
  results.push(...validateDomain(obj));

  return results;
}

/**
 * Port of InwonerplanDomainValidator.java — 3 domain rules.
 */
function validateDomain(obj) {
  const results = [];
  let hasErrors = false;

  if (!obj.zaaknummer) {
    results.push('Zaaknummer is empty!');
    hasErrors = true;
  }

  if (!obj.inwonerprofielId) {
    results.push('Inwonerprofiel is empty!');
    hasErrors = true;
  }

  if (!obj.inwonerplan?.doelen?.length) {
    results.push('Een inwonerplan moet minimaal 1 doel hebben');
    hasErrors = true;
  }

  if (!hasErrors) {
    results.push('Validatie ok');
  }

  return results;
}

/**
 * Validate custom schema text against JSON text.
 * Used when the user edits the schema tab.
 */
export function validateWithCustomSchema(jsonText, schemaText) {
  const results = [];

  let obj;
  try {
    obj = JSON.parse(jsonText);
  } catch (e) {
    results.push(`JSON parse error: ${e.message}`);
    return results;
  }

  let customSchema;
  try {
    customSchema = JSON.parse(schemaText);
  } catch (e) {
    results.push(`Schema parse error: ${e.message}`);
    return results;
  }

  try {
    const ajv = new Ajv({ allErrors: true, strict: false });
    addFormats(ajv);
    const validate = ajv.compile(customSchema);
    const valid = validate(obj);
    if (valid) {
      results.push('JSON validated successfully against schema!');
    } else {
      for (const err of validate.errors) {
        const path = err.instancePath || '/';
        results.push(`${path}: ${err.message}`);
      }
    }
  } catch (e) {
    results.push(`Schema compilation error: ${e.message}`);
  }

  // Domain validation
  const domainResults = [];
  let hasErrors = false;

  if (!obj.zaaknummer) {
    domainResults.push('Zaaknummer is empty!');
    hasErrors = true;
  }

  if (!obj.inwonerprofielId) {
    domainResults.push('Inwonerprofiel is empty!');
    hasErrors = true;
  }

  if (!obj.inwonerplan?.doelen?.length) {
    domainResults.push('Een inwonerplan moet minimaal 1 doel hebben');
    hasErrors = true;
  }

  if (!hasErrors) {
    domainResults.push('Validatie ok');
  }

  results.push(...domainResults);
  return results;
}
