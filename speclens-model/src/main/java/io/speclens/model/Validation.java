package io.speclens.model;

import java.util.Map;

/**
 * A validation rule the page declares in markup, with the message the app will show.
 * ASP.NET unobtrusive validation, for example, renders {@code data-val-length="Too long"}
 * plus {@code data-val-length-max="20"}: rule "length", params {max=20}.
 */
public record Validation(String rule, String message, Map<String, String> params) {
}
