package io.speclens.model;

/** How sure SpecLens is about an inferred item. Every item carries one. */
public enum Confidence {
    /** Read directly from a declarative attribute (e.g. required, maxlength, label[for]). */
    HIGH,
    /** Derived from a strong convention (e.g. a wrapping label, a Pega "required" marker). */
    MEDIUM,
    /** Heuristic guess (e.g. nearest preceding text). Worth a human look. */
    LOW
}
