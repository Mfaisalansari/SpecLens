package io.speclens.model;

/** Where a field's label was resolved from, in order of preference. */
public enum LabelSource {
    ARIA_LABELLEDBY(Confidence.HIGH),
    ARIA_LABEL(Confidence.HIGH),
    LABEL_FOR(Confidence.HIGH),
    WRAPPING_LABEL(Confidence.MEDIUM),
    FIELDSET_LEGEND(Confidence.MEDIUM),
    TITLE(Confidence.MEDIUM),
    /** A label-like element (label, dt, th) next to the field but not tied to it. */
    NEARBY_LABEL(Confidence.MEDIUM),
    PLACEHOLDER(Confidence.LOW),
    NEAREST_TEXT(Confidence.LOW),
    OWN_TEXT(Confidence.HIGH),
    NONE(Confidence.LOW);

    private final Confidence confidence;

    LabelSource(Confidence confidence) {
        this.confidence = confidence;
    }

    public Confidence confidence() {
        return confidence;
    }
}
