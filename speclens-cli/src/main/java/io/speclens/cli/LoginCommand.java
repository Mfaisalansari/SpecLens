package io.speclens.cli;

import io.speclens.capture.BrowserSession;
import io.speclens.capture.CaptureOptions;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.nio.file.Path;
import java.util.concurrent.Callable;

@Command(name = "login",
        description = "Open a browser, let you sign in by hand, then save the session for later captures.")
final class LoginCommand implements Callable<Integer> {

    @Parameters(index = "0", description = "Login page URL.")
    String url;

    @Option(names = {"-o", "--save"}, defaultValue = "auth.json",
            description = "Where to write the Playwright storage state (default: ${DEFAULT-VALUE}).")
    Path storageState;

    @Override
    public Integer call() {
        CaptureOptions defaults = CaptureOptions.defaults();
        CaptureOptions options = new CaptureOptions(null, false, false, defaults.quietMs(), defaults.timeoutMs(),
                defaults.viewportWidth(), defaults.viewportHeight());
        try (BrowserSession session = BrowserSession.open(options)) {
            session.page().navigate(url);
            Console.waitForEnter("Sign in in the browser window, then press Enter here to save the session.",
                    storageState.toAbsolutePath().resolveSibling(".speclens-ready"));
            session.saveStorageState(storageState);
        }
        System.out.println("Saved session to " + storageState.toAbsolutePath()
                + ". Pass it to capture with --storage-state. It contains login cookies: keep it out of version control.");
        return 0;
    }
}
