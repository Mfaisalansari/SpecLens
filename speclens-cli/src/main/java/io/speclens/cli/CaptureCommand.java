package io.speclens.cli;

import com.microsoft.playwright.PlaywrightException;
import io.speclens.capture.BrowserSession;
import io.speclens.capture.CaptureOptions;
import io.speclens.capture.ScreenCapturer;
import io.speclens.model.Json;
import io.speclens.model.Observations;
import io.speclens.model.Screen;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

@Command(name = "capture",
        description = {
                "Capture one screen (observations.json and screenshots), then infer spec.json and render the SRS (spec.html).",
                "Capturing into an existing output directory adds the screen to it, replacing one with the same id."})
final class CaptureCommand implements Callable<Integer> {

    @Parameters(index = "0", description = "URL to open.")
    String url;

    @Option(names = {"-o", "--out"}, defaultValue = "speclens-out",
            description = "Output directory (default: ${DEFAULT-VALUE}).")
    Path outDir;

    @Option(names = {"-s", "--storage-state"},
            description = "Playwright storage-state file from 'speclens login', to start signed in.")
    Path storageState;

    @Option(names = {"-n", "--name"}, description = "Screen name. Defaults to the screen's heading or page title.")
    String screenName;

    @Option(names = {"-i", "--interactive"},
            description = "Open a visible browser and wait for Enter before capturing, so you can navigate to the screen first.")
    boolean interactive;

    @Option(names = "--headed", description = "Show the browser window.")
    boolean headed;

    @Option(names = "--no-open-dropdowns",
            description = "Do not click custom dropdowns open to read their options (clicking can trigger app logic).")
    boolean noOpenDropdowns;

    @Option(names = "--quiet-ms", defaultValue = "500",
            description = "DOM must be unchanged this long to count as stable (default: ${DEFAULT-VALUE}).")
    int quietMs;

    @Option(names = "--timeout-ms", defaultValue = "30000",
            description = "Upper bound for navigation and each wait (default: ${DEFAULT-VALUE}).")
    int timeoutMs;

    @Option(names = {"-a", "--app-name"}, description = "Application name for the document. Defaults to the host name.")
    String applicationName;

    @Option(names = "--no-report", description = "Only write observations.json; skip infer and the SRS document.")
    boolean noReport;

    @Override
    public Integer call() {
        if (storageState != null && !Files.isRegularFile(storageState)) {
            System.err.println("Storage state not found: " + storageState);
            return 2;
        }
        CaptureOptions defaults = CaptureOptions.defaults();
        CaptureOptions options = new CaptureOptions(storageState, !(headed || interactive), !noOpenDropdowns,
                quietMs, timeoutMs, defaults.viewportWidth(), defaults.viewportHeight());

        Screen screen;
        try (BrowserSession session = BrowserSession.open(options)) {
            try {
                session.page().navigate(url);
            } catch (PlaywrightException e) {
                String reason = e.getMessage() == null ? "unknown error" : e.getMessage().lines()
                        .filter(l -> l.contains("net::") || l.contains("Timeout")).findFirst().orElse(e.getMessage().lines().findFirst().orElse(""))
                        .replaceFirst("^\\s*message='", "");
                System.err.println("Could not open " + url + ": " + reason.strip());
                if (reason.contains("ERR_HTTP2_PROTOCOL_ERROR") || reason.contains("ERR_CONNECTION_RESET")) {
                    System.err.println("The site closed the connection. It probably blocks automated browsers; SpecLens does not try to get around that.");
                }
                return 1;
            }
            if (interactive) {
                Console.waitForEnter("Go to the screen you want in the browser window, then press Enter here to capture it.");
            }
            screen = new ScreenCapturer(options, System.out::println).capture(session, outDir, screenName);
        }

        Path observationsFile = outDir.resolve(Pipeline.OBSERVATIONS);
        Observations observations = merge(observationsFile, screen);
        Json.write(observationsFile, observations);
        System.out.println("Wrote " + observationsFile);

        if (!noReport) {
            Pipeline.report(outDir, Pipeline.infer(outDir, applicationName));
        }
        return 0;
    }

    private static Observations merge(Path file, Screen screen) {
        List<Screen> screens = new ArrayList<>();
        if (Files.isRegularFile(file)) {
            screens.addAll(Json.read(file, Observations.class).screens());
        }
        int existing = indexOf(screens, screen.id());
        if (existing >= 0) {
            screens.set(existing, screen);
        } else {
            screens.add(screen);
        }
        return new Observations(Observations.SCHEMA_VERSION, Instant.now(), screens);
    }

    private static int indexOf(List<Screen> screens, String id) {
        for (int i = 0; i < screens.size(); i++) {
            if (screens.get(i).id().equals(id)) {
                return i;
            }
        }
        return -1;
    }
}
