package io.speclens.infer;

import io.speclens.model.Field;
import io.speclens.model.Option;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Small wording helpers so every generated sentence reads the same way. */
final class Phrases {

    private static final Pattern PLACEHOLDER_OPTION =
            Pattern.compile("^(-+|select\\b.*|choose\\b.*|please select\\b.*|none|\\(none\\)|.*\\.\\.\\.|.*…)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern DIGITS_EXACT = Pattern.compile("^\\^?(?:\\[0-9]|\\\\d)\\{(\\d+)}\\$?$");
    private static final Pattern DIGITS_RANGE = Pattern.compile("^\\^?(?:\\[0-9]|\\\\d)\\{(\\d+),(\\d+)}\\$?$");
    private static final Pattern LETTERS_EXACT = Pattern.compile("^\\^?\\[A-Z]\\{(\\d+)}\\$?$");

    private Phrases() {
    }

    /** “First name”, or a description of an unlabelled field. */
    static String name(Field f) {
        if (f.label() != null) {
            return "“" + f.label() + "”";
        }
        return "the unlabelled " + (f.role() == null ? "field" : f.role()) + " " + f.id();
    }

    static String quote(String s) {
        return "“" + s + "”";
    }

    /** A quoted message closing a sentence: no extra full stop when the message has its own. */
    static String closing(String message) {
        String q = quote(message);
        return message.matches(".*[.!?]$") ? q : q + ".";
    }

    /** a, b and c */
    static String join(List<String> items) {
        if (items.isEmpty()) {
            return "";
        }
        if (items.size() == 1) {
            return items.getFirst();
        }
        return String.join(", ", items.subList(0, items.size() - 1)) + " and " + items.getLast();
    }

    static String count(int n, String singular) {
        return n + " " + singular + (n == 1 ? "" : "s");
    }

    static String isAre(int n) {
        return n == 1 ? "is" : "are";
    }

    /** Options such as "Select...", "-- choose --" or an empty value are prompts, not values. */
    static boolean isPlaceholder(Option o) {
        String label = o.label() == null ? "" : o.label().trim();
        return label.isEmpty() || (o.value() != null && o.value().isEmpty()) || PLACEHOLDER_OPTION.matcher(label).matches();
    }

    /** A plain-language reading of common patterns, or null. */
    static String describePattern(String pattern) {
        Matcher m = DIGITS_EXACT.matcher(pattern);
        if (m.matches()) {
            return "exactly " + m.group(1) + " digits";
        }
        m = DIGITS_RANGE.matcher(pattern);
        if (m.matches()) {
            return "between " + m.group(1) + " and " + m.group(2) + " digits";
        }
        m = LETTERS_EXACT.matcher(pattern);
        if (m.matches()) {
            return "exactly " + m.group(1) + " capital letters";
        }
        return null;
    }

    /** "Build your own computer" → "BOC", "Register" → "REG". */
    static String code(String name) {
        List<String> words = List.of(name.toUpperCase(Locale.ROOT).split("[^A-Z0-9]+")).stream()
                .filter(w -> !w.isEmpty() && !STOP_WORDS.contains(w))
                .toList();
        if (words.isEmpty()) {
            return "SCR";
        }
        if (words.size() == 1) {
            String w = words.getFirst();
            return w.length() <= 3 ? w : w.substring(0, 3);
        }
        StringBuilder code = new StringBuilder();
        for (String w : words.subList(0, Math.min(4, words.size()))) {
            code.append(w.charAt(0));
        }
        return code.toString();
    }

    private static final Set<String> STOP_WORDS = Set.of(
            "A", "AN", "THE", "TO", "OF", "AND", "OR", "FOR", "OUR", "YOUR", "MY", "PLEASE", "WELCOME", "US", "IN", "ON");
}
