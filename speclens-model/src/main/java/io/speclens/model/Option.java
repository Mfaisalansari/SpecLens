package io.speclens.model;

/** One choice of a select, combobox or radio group. */
public record Option(String value, String label, boolean selected) {
}
