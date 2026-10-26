package io.speclens.model;

/** Something observation could not settle and a person has to answer. */
public record Question(String id, String screenId, String fieldId, String text) {
}
