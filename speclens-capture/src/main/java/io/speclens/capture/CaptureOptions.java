package io.speclens.capture;

import java.nio.file.Path;

/**
 * Settings for a capture run.
 *
 * @param storageState  Playwright storage-state file to start logged in, or null
 * @param headless      run the browser without a window
 * @param openDropdowns click custom comboboxes open to read their options (may trigger app logic)
 * @param quietMs       how long the DOM must stay unchanged before the screen counts as stable
 * @param timeoutMs     upper bound for each wait (navigation, network idle, stability)
 */
public record CaptureOptions(
        Path storageState,
        boolean headless,
        boolean openDropdowns,
        int quietMs,
        int timeoutMs,
        int viewportWidth,
        int viewportHeight) {

    public static CaptureOptions defaults() {
        return new CaptureOptions(null, true, true, 500, 30_000, 1440, 900);
    }
}
