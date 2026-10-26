package io.speclens.model;

import java.util.List;

/**
 * One screen of the inferred specification. Self-contained: it carries its own fields,
 * so spec.json can be read (and later diffed) without observations.json.
 *
 * @param code      short prefix for requirement ids, e.g. "REG"
 * @param summary   description paragraphs, generated from the observations
 * @param fields    the screen's own fields and actions (shared elements live in {@link Spec#common()})
 * @param appearsOn for the shared-elements section only: the screens these elements appear on
 */
public record ScreenSpec(
        String screenId,
        String code,
        String name,
        String url,
        String screenshot,
        List<String> summary,
        List<Field> fields,
        List<Rule> rules,
        List<String> appearsOn) {
}
