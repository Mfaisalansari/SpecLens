package io.speclens.model;

import java.util.List;

/**
 * One interactive element on a screen, as observed (not yet inferred).
 *
 * @param id              stable id within the screen, e.g. "f007"
 * @param kind            coarse grouping
 * @param role            ARIA role, explicit or implicit (textbox, combobox, radiogroup, ...)
 * @param tag             HTML tag name
 * @param inputType       the input's type attribute, if any
 * @param name            the name attribute, if any
 * @param property        app-level property path decoded from the name (Pega: "pyWorkPage.PolicyNumber"), or null
 * @param testId          stable test id attribute (data-test-id / data-testid), or null
 * @param controlHint     framework control type (Pega data-ctl, e.g. "DatePicker"), or null
 * @param label           resolved human label
 * @param labelSource     where the label came from
 * @param labelConfidence confidence in the label
 * @param required        declared required (attribute, aria-required, or Pega marker)
 * @param requiredSource  what the required flag was read from, or null if not required
 * @param validations     validation rules declared in markup, with their messages
 * @param locator         suggested Playwright locator that does not depend on generated ids
 * @param frame           name or url of the frame the element lives in, null for the main frame
 * @param box             bounding box relative to the element's own frame
 * @param evidence        crops and DOM snippets backing this entry
 */
public record Field(
        String id,
        FieldKind kind,
        String role,
        String tag,
        String inputType,
        String name,
        String property,
        String testId,
        String controlHint,
        String label,
        LabelSource labelSource,
        Confidence labelConfidence,
        boolean required,
        String requiredSource,
        List<Validation> validations,
        Integer maxLength,
        Integer minLength,
        String pattern,
        String min,
        String max,
        boolean readOnly,
        boolean disabled,
        String defaultValue,
        String placeholder,
        List<Option> options,
        String locator,
        String frame,
        Box box,
        List<Evidence> evidence) {
}
