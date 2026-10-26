package io.speclens.cli;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

final class Console {

    private Console() {
    }

    static void waitForEnter(String prompt) {
        System.out.println(prompt);
        try {
            new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8)).readLine();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
