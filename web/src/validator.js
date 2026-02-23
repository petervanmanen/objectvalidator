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
 * Domain-specific validation rules (port of InwonerplanDomainValidator.java).
 */
function validateDomain(obj) {
  const results = [];
  let hasErrors = false;

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
