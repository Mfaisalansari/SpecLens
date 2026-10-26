package io.speclens.capture;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Decodes Pega's encoded property references in name attributes.
 * {@code $PpyWorkPage$pApplicant$pAddress$l2$pCity} becomes {@code pyWorkPage.Applicant.Address(2).City}.
 */
final class PegaNames {

    private static final Pattern ENCODED = Pattern.compile("^\\$P([^$]+)((?:\\$[pl][^$]+)+)$");
    private static final Pattern SEGMENT = Pattern.compile("\\$([pl])([^$]+)");

    private PegaNames() {
    }

    /** The decoded property path, or null if the name is not a Pega reference. */
    static String decode(String name) {
        if (name == null) {
            return null;
        }
        Matcher m = ENCODED.matcher(name);
        if (!m.matches()) {
            return null;
        }
        StringBuilder path = new StringBuilder(m.group(1));
        Matcher seg = SEGMENT.matcher(m.group(2));
        while (seg.find()) {
            if (seg.group(1).equals("p")) {
                path.append('.').append(seg.group(2));
            } else {
                path.append('(').append(seg.group(2)).append(')');
            }
        }
        return path.toString();
    }
}
