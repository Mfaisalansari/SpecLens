package io.speclens.capture;

import com.fasterxml.jackson.core.type.TypeReference;
import com.microsoft.playwright.Frame;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.options.LoadState;
import io.speclens.model.Evidence;
import io.speclens.model.Field;
import io.speclens.model.Json;
import io.speclens.model.Option;
import io.speclens.model.Screen;

import java.io.IOException;
import java.net.URI;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Captures the screen currently shown in a {@link BrowserSession}: waits for it to settle,
 * finds every interactive element in every frame, and writes screenshots and the aria
 * snapshot under {@code <outDir>/screens/<screenId>/}.
 */
public final class ScreenCapturer {

    private static final TypeReference<List<RawElement>> RAW_LIST = new TypeReference<>() { };
    private static final TypeReference<List<Option>> OPTION_LIST = new TypeReference<>() { };

    private final CaptureOptions options;
    private final Consumer<String> log;

    public ScreenCapturer(CaptureOptions options, Consumer<String> log) {
        this.options = options;
        this.log = log;
    }

    /**
     * @param screenName optional human name for the screen; defaults to its heading or title
     */
    public Screen capture(BrowserSession session, Path outDir, String screenName) {
        Page page = session.page();
        waitForStable(page);

        List<FrameScan> scans = scanFrames(page);
        if (options.openDropdowns()) {
            scans = scans.stream().map(this::openDropdowns).toList();
            waitForStable(page);
        }

        String heading = screenName != null ? screenName : headingOf(scans, page);
        String screenId = Slug.of(heading != null ? heading : page.title().isBlank() ? page.url() : page.title());
        Path screenDir = outDir.resolve("screens").resolve(screenId);
        Path cropDir = screenDir.resolve("fields");
        createDirectories(cropDir);

        String screenshot = relative(outDir, screenDir.resolve("screen.png"));
        page.screenshot(new Page.ScreenshotOptions().setPath(screenDir.resolve("screen.png")).setFullPage(true));
        String aria = writeAriaSnapshot(page, outDir, screenDir);

        List<Field> fields = new ArrayList<>();
        for (FrameScan scan : scans) {
            for (RawElement raw : scan.elements()) {
                String fieldId = "f%03d".formatted(fields.size() + 1);
                List<Evidence> evidence = new ArrayList<>();
                Path crop = cropDir.resolve(fieldId + ".png");
                if (screenshotElement(scan.frame(), raw.sid(), crop)) {
                    evidence.add(Evidence.crop(relative(outDir, crop)));
                }
                evidence.add(Evidence.dom(raw.html()));
                fields.add(toField(fieldId, raw, scan.frameLabel(), evidence));
            }
        }
        log.accept("Captured %d fields on \"%s\" → %s".formatted(fields.size(), heading, relative(outDir, screenDir)));

        return new Screen(screenId, page.url(), page.title(), heading, Instant.now(), screenshot, aria,
                fields, session.drainNetwork());
    }

    private record FrameScan(Frame frame, String frameLabel, List<RawElement> elements) {
        FrameScan withElements(List<RawElement> newElements) {
            return new FrameScan(frame, frameLabel, newElements);
        }
    }

    private void waitForStable(Page page) {
        try {
            page.waitForLoadState(LoadState.NETWORKIDLE, new Page.WaitForLoadStateOptions().setTimeout(options.timeoutMs()));
        } catch (PlaywrightException e) {
            // Apps that poll (Pega does) may never go idle; DOM stability below is the real signal.
            log.accept("Network did not go idle within %d ms; continuing.".formatted(options.timeoutMs()));
        }
        for (Frame frame : page.frames()) {
            try {
                Object stable = frame.evaluate(Scripts.STABLE, List.of(options.quietMs(), options.timeoutMs()));
                if (!Boolean.TRUE.equals(stable)) {
                    log.accept("Frame %s kept changing for %d ms; capturing anyway.".formatted(labelOf(frame, page), options.timeoutMs()));
                }
            } catch (PlaywrightException e) {
                // Detached or cross-origin frames that cannot be evaluated are skipped.
            }
        }
    }

    private List<FrameScan> scanFrames(Page page) {
        List<FrameScan> scans = new ArrayList<>();
        List<Frame> frames = page.frames();
        for (int i = 0; i < frames.size(); i++) {
            Frame frame = frames.get(i);
            // Ads and third-party widgets live in cross-origin frames; the app's own frames
            // (Pega's case gadget, srcdoc and about:blank frames) share the page's origin.
            if (frame != page.mainFrame() && !sameOrigin(frame.url(), page.url())) {
                log.accept("Skipped third-party frame " + truncate(frame.url()));
                continue;
            }
            try {
                Object result = frame.evaluate(Scripts.SCAN, "fr" + i);
                List<RawElement> elements = Json.mapper().convertValue(result, RAW_LIST);
                if (!elements.isEmpty()) {
                    scans.add(new FrameScan(frame, frame == page.mainFrame() ? null : labelOf(frame, page), elements));
                }
            } catch (PlaywrightException e) {
                log.accept("Skipped frame %s: %s".formatted(labelOf(frame, page), firstLine(e.getMessage())));
            }
        }
        return scans;
    }

    private FrameScan openDropdowns(FrameScan scan) {
        List<RawElement> updated = new ArrayList<>();
        for (RawElement raw : scan.elements()) {
            if (raw.needsOpen() && !raw.disabled() && !raw.readOnly() && (raw.options() == null || raw.options().isEmpty())) {
                updated.add(raw.withOptions(readCustomOptions(scan.frame(), raw)));
            } else {
                updated.add(raw);
            }
        }
        return scan.withElements(updated);
    }

    private List<Option> readCustomOptions(Frame frame, RawElement raw) {
        Locator combo = frame.locator("[data-speclens-id=\"" + raw.sid() + "\"]");
        try {
            combo.click(new Locator.ClickOptions().setTimeout(3_000));
            frame.page().waitForTimeout(400);
            Object result = frame.evaluate(Scripts.OPTIONS, Arrays.asList(raw.sid(), raw.listboxId()));
            List<Option> found = Json.mapper().convertValue(result, OPTION_LIST);
            return found.isEmpty() ? null : found;
        } catch (PlaywrightException e) {
            log.accept("Could not open dropdown \"%s\": %s".formatted(raw.label(), firstLine(e.getMessage())));
            return null;
        } finally {
            try {
                combo.press("Escape", new Locator.PressOptions().setTimeout(1_000));
                // Leave no focus ring behind for the crop.
                frame.evaluate("() => document.activeElement && document.activeElement.blur()");
            } catch (PlaywrightException ignored) {
                // Already closed or gone.
            }
        }
    }

    private String headingOf(List<FrameScan> scans, Page page) {
        // Pega renders the case in an iframe; the frame holding the most fields has the real heading.
        Frame busiest = scans.stream()
                .max((a, b) -> Integer.compare(a.elements().size(), b.elements().size()))
                .map(FrameScan::frame)
                .orElse(page.mainFrame());
        for (Frame frame : List.of(busiest, page.mainFrame())) {
            try {
                Object heading = frame.evaluate(Scripts.HEADING);
                if (heading instanceof String s && !s.isBlank()) {
                    return s;
                }
            } catch (PlaywrightException ignored) {
                // Fall through to the next candidate.
            }
        }
        return page.title().isBlank() ? null : page.title();
    }

    private boolean screenshotElement(Frame frame, String sid, Path file) {
        try {
            frame.locator("[data-speclens-crop=\"" + sid + "\"]").first()
                    .screenshot(new Locator.ScreenshotOptions().setPath(file).setTimeout(5_000));
            return true;
        } catch (PlaywrightException e) {
            return false;
        }
    }

    private String writeAriaSnapshot(Page page, Path outDir, Path screenDir) {
        try {
            Path file = screenDir.resolve("aria.yml");
            Files.writeString(file, page.locator("body").ariaSnapshot());
            return relative(outDir, file);
        } catch (PlaywrightException e) {
            log.accept("Aria snapshot failed: " + firstLine(e.getMessage()));
            return null;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Field toField(String id, RawElement raw, String frame, List<Evidence> evidence) {
        return new Field(
                id, raw.kind(), raw.role(), raw.tag(), raw.inputType(), raw.name(), PegaNames.decode(raw.name()),
                raw.testId(), raw.controlHint(), raw.label(), raw.labelSource(), raw.labelSource().confidence(),
                raw.required() != null, raw.required(), raw.validations(), raw.maxLength(), raw.minLength(), raw.pattern(),
                raw.min(), raw.max(), raw.readOnly(), raw.disabled(), raw.value(), raw.placeholder(),
                raw.options(), Locators.suggest(raw), frame, raw.box(), evidence);
    }

    private static String labelOf(Frame frame, Page page) {
        if (frame == page.mainFrame()) {
            return "main";
        }
        return frame.name() != null && !frame.name().isBlank() ? frame.name() : frame.url();
    }

    static boolean sameOrigin(String frameUrl, String pageUrl) {
        if (frameUrl == null || frameUrl.isEmpty() || frameUrl.startsWith("about:")) {
            return true;
        }
        try {
            URI a = URI.create(frameUrl);
            URI b = URI.create(pageUrl);
            return Objects.equals(a.getScheme(), b.getScheme())
                    && Objects.equals(a.getHost(), b.getHost())
                    && a.getPort() == b.getPort();
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static String truncate(String url) {
        return url.length() > 100 ? url.substring(0, 100) + "…" : url;
    }

    private static String relative(Path outDir, Path file) {
        return outDir.toAbsolutePath().relativize(file.toAbsolutePath()).toString().replace('\\', '/');
    }

    private static void createDirectories(Path dir) {
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String firstLine(String message) {
        if (message == null) {
            return "";
        }
        int nl = message.indexOf('\n');
        return nl < 0 ? message : message.substring(0, nl);
    }
}
