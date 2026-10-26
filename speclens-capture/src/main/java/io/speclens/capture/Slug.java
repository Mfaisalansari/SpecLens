package io.speclens.capture;

import java.text.Normalizer;
import java.util.Locale;

final class Slug {

    private Slug() {
    }

    /** "Submission Details (Step 2)" becomes "submission-details-step-2". */
    static String of(String text) {
        String ascii = Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        String slug = ascii.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
        if (slug.length() > 60) {
            slug = slug.substring(0, 60).replaceAll("-$", "");
        }
        return slug.isEmpty() ? "screen" : slug;
    }
}
