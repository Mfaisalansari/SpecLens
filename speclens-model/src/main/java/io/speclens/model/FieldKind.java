package io.speclens.model;

/** Coarse grouping of interactive elements, used to lay out the field dictionary. */
public enum FieldKind {
    /** Free entry: text, number, date, textarea. */
    INPUT,
    /** Pick from a list: select, combobox, radio group. */
    CHOICE,
    /** On/off: checkbox, switch. */
    TOGGLE,
    /** Buttons and button-like links. */
    ACTION
}
