package io.speclens.model;

/** Bounding box in CSS pixels, relative to the top of the page. */
public record Box(double x, double y, double width, double height) {
}
