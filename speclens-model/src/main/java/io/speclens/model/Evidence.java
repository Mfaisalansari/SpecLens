package io.speclens.model;

/**
 * Something a reviewer can open to verify a claim.
 *
 * @param kind   what sort of artifact this is
 * @param path   path relative to the output directory (for files), or null
 * @param detail free text, e.g. the attribute or DOM snippet a value was read from
 */
public record Evidence(Kind kind, String path, String detail) {

    public enum Kind { SCREENSHOT, CROP, ARIA_SNAPSHOT, DOM, NETWORK }

    public static Evidence crop(String path) {
        return new Evidence(Kind.CROP, path, null);
    }

    public static Evidence dom(String detail) {
        return new Evidence(Kind.DOM, null, detail);
    }
}
