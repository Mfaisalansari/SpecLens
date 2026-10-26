package io.speclens.cli;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

final class Console {

    private static final long POLL_MS = 300;

    private Console() {
    }

    /**
     * Blocks until the user presses Enter or {@code readyFile} appears (then deletes it).
     * The file lets a run started without a terminal, e.g. by another tool, be released too.
     */
    static void waitForEnter(String prompt, Path readyFile) {
        System.out.println(prompt);
        System.out.println("(Or create " + readyFile.toAbsolutePath() + " to continue.)");
        try {
            Files.deleteIfExists(readyFile);
            while (true) {
                if (Files.exists(readyFile)) {
                    Files.deleteIfExists(readyFile);
                    return;
                }
                if (System.in.available() > 0) {
                    int c = System.in.read();
                    if (c == '\n' || c == -1) {
                        return;
                    }
                    continue;
                }
                Thread.sleep(POLL_MS);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
