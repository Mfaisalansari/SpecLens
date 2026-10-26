package io.speclens.capture;

import io.speclens.model.Box;
import io.speclens.model.FieldKind;
import io.speclens.model.LabelSource;
import io.speclens.model.Option;
import io.speclens.model.Validation;

import java.util.List;

/** One element exactly as scan.js reports it, before it becomes a {@link io.speclens.model.Field}. */
record RawElement(
        String sid,
        FieldKind kind,
        String role,
        String tag,
        String inputType,
        String name,
        String stableId,
        String testIdAttr,
        String testId,
        String controlHint,
        String label,
        LabelSource labelSource,
        String required,
        List<Validation> validations,
        Integer maxLength,
        Integer minLength,
        String pattern,
        String min,
        String max,
        boolean readOnly,
        boolean disabled,
        String value,
        String placeholder,
        List<Option> options,
        boolean needsOpen,
        String listboxId,
        Box box,
        String html) {

    RawElement withOptions(List<Option> newOptions) {
        return new RawElement(sid, kind, role, tag, inputType, name, stableId, testIdAttr, testId, controlHint, label,
                labelSource, required, validations, maxLength, minLength, pattern, min, max, readOnly, disabled, value,
                placeholder, newOptions, needsOpen, listboxId, box, html);
    }
}
