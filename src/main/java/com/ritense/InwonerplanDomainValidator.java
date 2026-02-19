package com.ritense;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;

public class InwonerplanDomainValidator {

    /**
     * Validate domain-specific rules for an inwonerplan.
     * Only reports "Validatie ok" when no domain errors are found (#6).
     */
    public static List<String> validateInwonerPlan(ObjectMapper objectMapper, String inwonerplan) {
        List<String> response = new ArrayList<>();
        InwonerplanSchema inwonerplanObj;
        try {
            inwonerplanObj = objectMapper.readValue(inwonerplan, InwonerplanSchema.class);
        } catch (JsonProcessingException e) {
            response.add(e.getMessage());
            return response;
        }

        boolean hasErrors = false;

        if (StringUtils.isEmpty(inwonerplanObj.getZaaknummer())) {
            response.add("Zaaknummer is empty!");
            hasErrors = true;
        }

        if (StringUtils.isEmpty(inwonerplanObj.getInwonerprofielId())) {
            response.add("Inwonerprofiel is empty!");
            hasErrors = true;
        }

        if (inwonerplanObj.getInwonerplan().getDoelen().isEmpty()) {
            response.add("Een inwonerplan moet minimaal 1 doel hebben");
            hasErrors = true;
        }

        if (!hasErrors) {
            response.add("Validatie ok");
        }

        return response;
    }
}
