package io.speclens.model;

import java.time.Instant;
import java.util.List;

/**
 * One captured screen.
 *
 * @param screenshot   full-page screenshot, relative to the output directory
 * @param ariaSnapshot accessibility tree (Playwright aria snapshot, YAML), relative to the output directory
 */
public record Screen(
        String id,
        String url,
        String title,
        String heading,
        Instant capturedAt,
        String screenshot,
        String ariaSnapshot,
        List<Field> fields,
        List<NetworkCall> network) {
}
