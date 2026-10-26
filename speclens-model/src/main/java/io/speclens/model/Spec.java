package io.speclens.model;

import java.time.Instant;
import java.util.List;

/**
 * Output of the infer stage: spec.json, the inferred functional specification.
 *
 * @param application name of the application the spec describes
 * @param common      elements shared by most screens (header search, newsletter box), described once; may be null
 */
public record Spec(
        String schemaVersion,
        Instant createdAt,
        String application,
        List<ScreenSpec> screens,
        ScreenSpec common,
        List<Question> questions) {

    public static final String SCHEMA_VERSION = "1";
}
