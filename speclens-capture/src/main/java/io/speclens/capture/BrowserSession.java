package io.speclens.capture;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import io.speclens.model.NetworkCall;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** One browser, context and page, plus a record of the fetch/XHR traffic the page makes. */
public final class BrowserSession implements AutoCloseable {

    private static final Set<String> RECORDED_TYPES = Set.of("document", "xhr", "fetch");
    private static final int MAX_RECORDED = 500;

    private final Playwright playwright;
    private final Browser browser;
    private final BrowserContext context;
    private final Page page;
    private final List<NetworkCall> network = new ArrayList<>();

    private BrowserSession(CaptureOptions options) {
        playwright = Playwright.create();
        browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(options.headless()));
        Browser.NewContextOptions contextOptions = new Browser.NewContextOptions()
                .setViewportSize(options.viewportWidth(), options.viewportHeight());
        if (options.storageState() != null) {
            contextOptions.setStorageStatePath(options.storageState());
        }
        context = browser.newContext(contextOptions);
        context.setDefaultTimeout(options.timeoutMs());
        page = context.newPage();
        page.onResponse(response -> {
            String type = response.request().resourceType();
            if (RECORDED_TYPES.contains(type) && network.size() < MAX_RECORDED) {
                network.add(new NetworkCall(response.request().method(), truncate(response.url()), response.status(), type));
            }
        });
    }

    public static BrowserSession open(CaptureOptions options) {
        return new BrowserSession(options);
    }

    public Page page() {
        return page;
    }

    /** Traffic recorded since the last call; clears the record. */
    List<NetworkCall> drainNetwork() {
        List<NetworkCall> calls = List.copyOf(network);
        network.clear();
        return calls;
    }

    public void saveStorageState(Path file) {
        context.storageState(new BrowserContext.StorageStateOptions().setPath(file));
    }

    @Override
    public void close() {
        context.close();
        browser.close();
        playwright.close();
    }

    private static String truncate(String url) {
        return url.length() > 300 ? url.substring(0, 300) + "…" : url;
    }
}
