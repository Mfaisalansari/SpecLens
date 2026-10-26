package io.speclens.capture;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/** The JavaScript snippets evaluated inside pages, loaded once from the classpath. */
final class Scripts {

    static final String SCAN = load("scan.js");
    static final String STABLE = load("stable.js");
    static final String OPTIONS = load("options.js");
    static final String HEADING = load("heading.js");

    private Scripts() {
    }

    private static String load(String name) {
        try (InputStream in = Scripts.class.getResourceAsStream(name)) {
            if (in == null) {
                throw new IllegalStateException("Missing script resource " + name);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
