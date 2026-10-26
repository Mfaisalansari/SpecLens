package io.speclens.model;

import java.time.Instant;
import java.util.List;

/** Output of the capture stage: observations.json. */
public record Observations(String schemaVersion, Instant createdAt, List<Screen> screens) {

    public static final String SCHEMA_VERSION = "1";
}
