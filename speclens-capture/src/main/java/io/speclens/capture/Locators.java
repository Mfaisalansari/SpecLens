package io.speclens.capture;

import io.speclens.model.LabelSource;

import java.util.EnumSet;
import java.util.Set;

/**
 * Suggests a Playwright locator string for an element that survives Pega's generated ids:
 * a test id first, then role plus accessible name, then the name attribute, then an id
 * that scan.js judged hand-written (never a generated one).
 */
final class Locators {

    /** Label sources that match the element's accessible name, so a role locator will find it. */
    private static final Set<LabelSource> ACCESSIBLE_NAME = EnumSet.of(
            LabelSource.ARIA_LABELLEDBY, LabelSource.ARIA_LABEL, LabelSource.LABEL_FOR,
            LabelSource.WRAPPING_LABEL, LabelSource.OWN_TEXT, LabelSource.TITLE);

    private Locators() {
    }

    static String suggest(RawElement e) {
        if (e.testId() != null) {
            return "[" + e.testIdAttr() + "=" + quote(e.testId()) + "]";
        }
        // Radio groups are often synthesised from a shared name, with no real radiogroup element.
        if ("radiogroup".equals(e.role()) && e.name() != null) {
            return "[name=" + quote(e.name()) + "]";
        }
        if (e.label() != null && e.role() != null && ACCESSIBLE_NAME.contains(e.labelSource())) {
            return "role=" + e.role() + "[name=" + quote(e.label()) + "]";
        }
        if (e.name() != null) {
            return "[name=" + quote(e.name()) + "]";
        }
        if (e.stableId() != null) {
            return "#" + e.stableId();
        }
        return null;
    }

    private static String quote(String s) {
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
