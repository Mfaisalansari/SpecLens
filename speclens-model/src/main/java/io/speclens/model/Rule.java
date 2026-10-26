package io.speclens.model;

import java.util.List;

/**
 * An inferred business rule (produced by the infer stage, Phase 2+).
 *
 * @param fieldIds    fields the rule applies to; more than one for cross-field rules
 * @param statement   the rule as a requirement sentence, e.g. "The system shall require ..."
 * @param message     the message the app shows when the rule is broken, if known
 * @param condition   when the rule applies, or null if always
 */
public record Rule(
        String id,
        String screenId,
        List<String> fieldIds,
        RuleType type,
        String statement,
        String message,
        String condition,
        Confidence confidence,
        List<Evidence> evidence) {
}
