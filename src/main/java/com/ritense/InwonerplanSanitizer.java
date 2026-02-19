package com.ritense;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

public class InwonerplanSanitizer {

    // Pre-compiled regex patterns for JSON cleanup (#9)
    private static final Pattern NONE_PATTERN = Pattern.compile("None");
    private static final Pattern TRUE_PATTERN = Pattern.compile("True");
    private static final Pattern NANOSECONDS_PATTERN = Pattern.compile(
            "(\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{3})\\d*(Z?)");
    private static final Pattern GMT_PATTERN = Pattern.compile("\\[GMT]");
    private static final Pattern MIDNIGHT_PATTERN = Pattern.compile(
            "00:00:00\\.00000Z"); // Escaped dots (#20)
    private static final Pattern TIMEZONE_OFFSET_PATTERN = Pattern.compile(
            "\\+\\d\\d:?\\d\\d");

    /**
     * Deduplicate aanbod, activiteiten and subdoelen in the inwonerplan.
     * Throws JsonProcessingException on parse failure instead of silently continuing (#3).
     */
    public static String ontdubbelInwonerplan(ObjectMapper objectMapper, String inwonerplan)
            throws JsonProcessingException {
        String inwonerplanJson = cleanupJson(inwonerplan);
        InwonerplanSchema inwonerplanObj = objectMapper.readValue(inwonerplanJson, InwonerplanSchema.class);

        // Deduplicate aanbod and activiteiten per subdoel
        for (Doel doel : inwonerplanObj.getInwonerplan().getDoelen()) {
            for (Subdoel s : doel.getSubdoelen()) {
                s.setAanbod(deduplicateAanbod(s.getAanbod()));
                s.setActiviteiten(deduplicateActiviteiten(s.getActiviteiten()));
            }
        }

        // Subdoel deduplication: for active doelen, remove completed duplicates but always keep unique ones (#1)
        for (Doel doel : inwonerplanObj.getInwonerplan().getDoelen()) {
            if ("1".equalsIgnoreCase(doel.getCodeStatusDoel())) {
                List<Subdoel> subdoelList = new ArrayList<>();
                for (Subdoel s : doel.getSubdoelen()) {
                    long count = doel.getSubdoelen().stream()
                            .filter(subdoel -> subdoelEquals(subdoel, s))
                            .count();
                    if (count > 1) {
                        // Has duplicates: only keep non-completed ones
                        if (!"2".equalsIgnoreCase(s.getCodeStatusSubdoel())) {
                            subdoelList.add(s);
                        }
                    } else {
                        // Unique subdoel: always keep (#1 fix)
                        subdoelList.add(s);
                    }
                }
                doel.setSubdoelen(subdoelList);
            }
        }

        return objectMapper.writeValueAsString(inwonerplanObj);
    }

    /**
     * Clean up malformed JSON values using pre-compiled patterns.
     * Single pass for nanosecond trimming — removed duplicate trimDateTimeNanoseconds call (#9).
     */
    static String cleanupJson(String inwonerplan) {
        String json = NONE_PATTERN.matcher(inwonerplan).replaceAll("null");
        json = TRUE_PATTERN.matcher(json).replaceAll("true");
        // Trim nanoseconds to 3 digits and ensure Z suffix (single pass)
        json = NANOSECONDS_PATTERN.matcher(json).replaceAll("$1Z");
        json = GMT_PATTERN.matcher(json).replaceAll("");
        // Fix midnight timestamps that are not valid format
        json = MIDNIGHT_PATTERN.matcher(json).replaceAll("00:00:01.001Z");
        json = TIMEZONE_OFFSET_PATTERN.matcher(json).replaceAll("Z");
        return json;
    }

    /**
     * Deduplicate aanbod list using Set-based approach for O(n) performance (#10).
     */
    private static List<Aanbod> deduplicateAanbod(List<Aanbod> aanbodList) {
        Set<String> seen = new HashSet<>();
        List<Aanbod> result = new ArrayList<>();
        for (Aanbod a : aanbodList) {
            if (a == null) {
                result.add(null);
                continue;
            }
            String key = aanbodKey(a);
            if (seen.add(key)) {
                result.add(a);
            }
        }
        return result;
    }

    private static String aanbodKey(Aanbod a) {
        return keyPart(a.getCodeAanbod()) + "|"
                + keyPart(a.getCodeRedenStatusAanbod()) + "|"
                + keyPart(a.getCodeResultaatAanbod());
    }

    /**
     * Deduplicate activiteiten using two-set approach for UUID and code combo matching (#10).
     * Checks UUID match OR code combo match, consistent with activiteitEquals semantics.
     */
    private static List<Activiteit> deduplicateActiviteiten(List<Activiteit> activiteitList) {
        Set<String> seenUuids = new HashSet<>();
        Set<String> seenCodes = new HashSet<>();
        List<Activiteit> result = new ArrayList<>();
        for (Activiteit a : activiteitList) {
            if (a == null) {
                result.add(null);
                continue;
            }
            boolean isDuplicate = false;
            String uuidKey = a.getUuid() != null ? a.getUuid().toLowerCase() : null;
            String codeKey = (a.getCodeAanbod() != null && a.getCodeAanbodactiviteit() != null)
                    ? a.getCodeAanbod().toLowerCase() + "|" + a.getCodeAanbodactiviteit().toLowerCase()
                    : null;

            // Check against already-seen items (separate from add to avoid phantom state)
            if (uuidKey != null && seenUuids.contains(uuidKey)) {
                isDuplicate = true;
            }
            if (!isDuplicate && codeKey != null && seenCodes.contains(codeKey)) {
                isDuplicate = true;
            }

            if (!isDuplicate) {
                if (uuidKey != null) seenUuids.add(uuidKey);
                if (codeKey != null) seenCodes.add(codeKey);
                result.add(a);
            }
        }
        return result;
    }

    private static String keyPart(String s) {
        return s != null ? s.toLowerCase() : "\0";
    }

    // Null-safe equals methods for use in subdoel deduplication and testing (#2, #4, #5)

    static boolean aanbodEquals(Aanbod a1, Aanbod a2) {
        if (a1 == null || a2 == null) {
            return false;
        }
        // Null-safe comparison (#4)
        if (!StringUtils.equalsIgnoreCase(a1.getCodeAanbod(), a2.getCodeAanbod())) {
            return false;
        }
        // Use != 0 for equality check, not > 0 (#5)
        if (StringUtils.compareIgnoreCase(a1.getCodeRedenStatusAanbod(), a2.getCodeRedenStatusAanbod()) != 0) {
            return false;
        }
        if (StringUtils.compareIgnoreCase(a1.getCodeResultaatAanbod(), a2.getCodeResultaatAanbod()) != 0) {
            return false;
        }
        return true;
    }

    static boolean subdoelEquals(Subdoel s1, Subdoel s2) {
        // Null checks BEFORE dereferencing (#2)
        if (s1.getAandachtspuntId() != null && s2.getAandachtspuntId() != null
                && s1.getAandachtspuntId().equalsIgnoreCase(s2.getAandachtspuntId())) {
            return true;
        }
        if (s1.getOntwikkelwensId() != null && s2.getOntwikkelwensId() != null
                && s1.getOntwikkelwensId().equalsIgnoreCase(s2.getOntwikkelwensId())) {
            return true;
        }
        return false;
    }

    static boolean activiteitEquals(Activiteit a1, Activiteit a2) {
        if (a1 == null || a2 == null) {
            return false;
        }
        // Null-safe comparisons (#4)
        if (a1.getUuid() != null && a2.getUuid() != null
                && a1.getUuid().equalsIgnoreCase(a2.getUuid())) {
            return true;
        }
        if (a1.getCodeAanbod() != null && a2.getCodeAanbod() != null
                && a1.getCodeAanbodactiviteit() != null && a2.getCodeAanbodactiviteit() != null
                && a1.getCodeAanbod().equalsIgnoreCase(a2.getCodeAanbod())
                && a1.getCodeAanbodactiviteit().equalsIgnoreCase(a2.getCodeAanbodactiviteit())) {
            return true;
        }
        return false;
    }
}
