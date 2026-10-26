package io.speclens.model;

/** A fetch/XHR request seen while the screen loaded. */
public record NetworkCall(String method, String url, int status, String resourceType) {
}
