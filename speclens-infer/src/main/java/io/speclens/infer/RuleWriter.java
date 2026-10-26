package io.speclens.infer;

import io.speclens.model.Confidence;
import io.speclens.model.Evidence;
import io.speclens.model.Field;
import io.speclens.model.FieldKind;
import io.speclens.model.LabelSource;
import io.speclens.model.Option;
import io.speclens.model.Rule;
import io.speclens.model.RuleType;
import io.speclens.model.Validation;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static io.speclens.infer.Phrases.name;
import static io.speclens.infer.Phrases.quote;

/**
 * Turns the fields of one screen into numbered requirement statements and open questions.
 * Every statement is built from an observed attribute; nothing is guessed beyond what the
 * confidence level says.
 */
final class RuleWriter {

    /** Draft of a question; ids are assigned once all screens are processed. */
    record QuestionDraft(String screenId, String fieldId, String text) {
    }

    private static final Set<String> DECLARATIVE_REQUIRED = Set.of("required attribute", "aria-required");
    private static final Set<String> HANDLED_VALIDATIONS = Set.of("required", "length", "email", "url", "number", "regex", "range", "equalto");
    private static final int MAX_LISTED_OPTIONS = 15;

    private final String screenId;
    private final String code;
    /** Where the field is, for questions: “on “Register”” or “among the shared elements”. */
    private final String where;
    private final List<Field> screenFields;
    private final List<Rule> rules = new ArrayList<>();
    private final List<QuestionDraft> questions = new ArrayList<>();

    RuleWriter(String screenId, String code, String where, List<Field> screenFields) {
        this.screenId = screenId;
        this.code = code;
        this.where = where;
        this.screenFields = screenFields;
    }

    List<Rule> rules() {
        return rules;
    }

    List<QuestionDraft> questions() {
        return questions;
    }

    void write(Field f) {
        if (f.kind() == FieldKind.ACTION) {
            action(f);
            return;
        }
        labelQuestions(f);
        required(f);
        length(f);
        format(f);
        range(f);
        crossField(f);
        allowedValues(f);
        defaultValue(f);
        state(f);
        otherValidations(f);
    }

    private void action(Field f) {
        if (f.disabled()) {
            add(f, RuleType.ENABLEMENT, "The " + name(f) + " action shall be unavailable when the screen first opens.",
                    null, Confidence.HIGH, "disabled");
            ask(f, "What makes the " + name(f) + " action " + where + " available?");
        }
    }

    private void labelQuestions(Field f) {
        if (f.labelSource() == LabelSource.NONE) {
            ask(f, "Field " + f.id() + " " + where + " has no visible label. What is it called and what does it hold?");
        } else if (f.labelConfidence() == Confidence.LOW) {
            ask(f, "The name " + name(f) + " for field " + f.id() + " " + where + " was taken from "
                    + describe(f.labelSource()) + ". Confirm the field's business name.");
        }
    }

    private void required(Field f) {
        if (!f.required()) {
            return;
        }
        Validation declared = validation(f, "required");
        String verb = switch (f.kind()) {
            case CHOICE -> "select a value for ";
            case TOGGLE -> "tick ";
            default -> "enter ";
        };
        String message = declared != null ? declared.message() : null;
        String statement = "The system shall require the user to " + verb + name(f) + "."
                + (message != null ? " If it is left empty, the system shall display " + Phrases.closing(message) : "");
        Confidence confidence = declared != null || DECLARATIVE_REQUIRED.contains(f.requiredSource()) ? Confidence.HIGH : Confidence.MEDIUM;
        add(f, RuleType.REQUIRED, statement, message, confidence, f.requiredSource());
    }

    private void length(Field f) {
        Validation v = validation(f, "length");
        Integer min = f.minLength();
        Integer max = f.maxLength();
        String message = null;
        String source = "maxlength / minlength attributes";
        if (v != null) {
            min = parse(v.params().get("min"), min);
            max = parse(v.params().get("max"), max);
            message = v.message();
            source = "data-val-length";
        }
        if (min == null && max == null) {
            return;
        }
        String bounds;
        if (min != null && max != null) {
            bounds = "between " + min + " and " + max + " characters long";
        } else if (min != null) {
            bounds = "at least " + Phrases.count(min, "character") + " long";
        } else {
            bounds = "no longer than " + Phrases.count(max, "character");
        }
        String statement = name(f) + " shall be " + bounds + "."
                + (message != null ? " Otherwise the system shall display " + Phrases.closing(message) : "");
        add(f, RuleType.LENGTH, statement, message, Confidence.HIGH, source);
    }

    private void format(Field f) {
        String type = f.inputType() == null ? "" : f.inputType();
        Validation email = validation(f, "email");
        Validation url = validation(f, "url");
        Validation number = validation(f, "number");
        Validation regex = validation(f, "regex");
        if (email != null || type.equals("email")) {
            formatRule(f, "a valid email address", email, email != null ? "data-val-email" : "type=\"email\"");
        }
        if (url != null || type.equals("url")) {
            formatRule(f, "a valid web address", url, url != null ? "data-val-url" : "type=\"url\"");
        }
        if (number != null || type.equals("number")) {
            formatRule(f, "a number", number, number != null ? "data-val-number" : "type=\"number\"");
        }
        if (type.equals("date") || "DatePicker".equals(f.controlHint())) {
            formatRule(f, "a date", null, type.equals("date") ? "type=\"date\"" : "data-ctl DatePicker");
        }
        String pattern = regex != null && regex.params().get("pattern") != null ? regex.params().get("pattern") : f.pattern();
        if (pattern != null) {
            String plain = Phrases.describePattern(pattern);
            String statement = name(f) + " shall " + (plain != null ? "consist of " + plain : "match the pattern " + pattern) + "."
                    + (regex != null && regex.message() != null ? " Otherwise the system shall display " + Phrases.closing(regex.message()) : "");
            add(f, RuleType.FORMAT, statement, regex != null ? regex.message() : null, Confidence.HIGH,
                    "pattern=\"" + pattern + "\"");
        }
    }

    private void formatRule(Field f, String what, Validation v, String source) {
        String message = v != null ? v.message() : null;
        String statement = name(f) + " shall be " + what + "."
                + (message != null ? " Otherwise the system shall display " + Phrases.closing(message) : "");
        add(f, RuleType.FORMAT, statement, message, Confidence.HIGH, source);
    }

    private void range(Field f) {
        Validation v = validation(f, "range");
        String min = v != null ? v.params().getOrDefault("min", f.min()) : f.min();
        String max = v != null ? v.params().getOrDefault("max", f.max()) : f.max();
        if (min == null && max == null) {
            return;
        }
        String bounds = min != null && max != null ? "between " + min + " and " + max
                : min != null ? "at least " + min : "at most " + max;
        String message = v != null ? v.message() : null;
        String statement = name(f) + " shall be " + bounds + "."
                + (message != null ? " Otherwise the system shall display " + Phrases.closing(message) : "");
        add(f, RuleType.RANGE, statement, message, Confidence.HIGH, v != null ? "data-val-range" : "min / max attributes");
    }

    private void crossField(Field f) {
        Validation v = validation(f, "equalto");
        if (v == null) {
            return;
        }
        String other = v.params().getOrDefault("other", "");
        String otherName = other.startsWith("*.") ? other.substring(2) : other;
        Field target = screenFields.stream()
                .filter(o -> o != f && o.name() != null && (o.name().equals(otherName) || o.name().endsWith("." + otherName)))
                .findFirst().orElse(null);
        String targetName = target != null ? name(target) : quote(otherName);
        String statement = name(f) + " shall be identical to " + targetName + "."
                + (v.message() != null ? " Otherwise the system shall display " + Phrases.closing(v.message()) : "");
        List<String> ids = target != null ? List.of(f.id(), target.id()) : List.of(f.id());
        rules.add(new Rule(nextId(), screenId, ids, RuleType.CROSS_FIELD, statement, v.message(), null,
                Confidence.HIGH, evidence(f, "data-val-equalto-other=\"" + other + "\"")));
    }

    private void allowedValues(Field f) {
        if (f.kind() != FieldKind.CHOICE) {
            return;
        }
        List<Option> options = f.options() == null ? List.of() : f.options().stream().filter(o -> !Phrases.isPlaceholder(o)).toList();
        if (options.isEmpty()) {
            if (!f.disabled()) {
                ask(f, "The values allowed in " + name(f) + " " + where
                        + " could not be listed without typing. What values are allowed?");
            }
            return;
        }
        String verb = "listbox".equals(f.role()) ? "one or more of" : "one of";
        String statement;
        if (options.size() <= MAX_LISTED_OPTIONS) {
            statement = "The user shall choose " + name(f) + " from " + verb + ": "
                    + Phrases.join(options.stream().map(o -> quote(o.label())).toList()) + ".";
        } else {
            statement = "The user shall choose " + name(f) + " from " + verb + " " + options.size()
                    + " predefined values (listed in the data dictionary).";
        }
        add(f, RuleType.ALLOWED_VALUES, statement, null, Confidence.HIGH, Phrases.count(options.size(), "option") + " observed");
    }

    private void defaultValue(Field f) {
        String value = f.defaultValue();
        if (value == null || "password".equals(f.inputType())) {
            return;
        }
        if (f.kind() == FieldKind.TOGGLE) {
            if (value.equals("checked")) {
                add(f, RuleType.DEFAULT_VALUE, name(f) + " shall be ticked by default.", null, Confidence.HIGH, "checked");
            }
            return;
        }
        if (f.kind() == FieldKind.CHOICE && f.options() != null
                && f.options().stream().anyMatch(o -> o.selected() && Phrases.isPlaceholder(o))) {
            return;
        }
        if (f.role() != null && f.role().equals("combobox") && (f.options() == null || f.options().isEmpty())) {
            return; // Text shown in a custom dropdown without options is usually its prompt.
        }
        add(f, RuleType.DEFAULT_VALUE, name(f) + " shall be pre-filled with " + quote(value) + ".", null,
                Confidence.MEDIUM, "value when the screen opened");
    }

    private void state(Field f) {
        if (f.readOnly()) {
            add(f, RuleType.ENABLEMENT, name(f) + " shall be displayed but not editable.", null, Confidence.HIGH, "readonly");
        }
        if (f.disabled()) {
            add(f, RuleType.ENABLEMENT, name(f) + " shall be disabled when the screen first opens.", null, Confidence.HIGH, "disabled");
            ask(f, "What makes " + name(f) + " " + where + " editable?");
        }
    }

    private void otherValidations(Field f) {
        if (f.validations() == null) {
            return;
        }
        for (Validation v : f.validations()) {
            if (HANDLED_VALIDATIONS.contains(v.rule())) {
                continue;
            }
            String statement = name(f) + " shall pass the " + quote(v.rule()) + " validation"
                    + (v.params().isEmpty() ? "" : " " + v.params())
                    + (v.message() != null ? "; otherwise the system shall display " + quote(v.message()) : "") + ".";
            add(f, RuleType.FORMAT, statement, v.message(), Confidence.HIGH, "data-val-" + v.rule());
        }
    }

    private void add(Field f, RuleType type, String statement, String message, Confidence confidence, String source) {
        rules.add(new Rule(nextId(), screenId, List.of(f.id()), type, capitalise(statement), message, null,
                confidence, evidence(f, source)));
    }

    private void ask(Field f, String text) {
        questions.add(new QuestionDraft(screenId, f.id(), text));
    }

    private String nextId() {
        return "%s-%02d".formatted(code, rules.size() + 1);
    }

    private static List<Evidence> evidence(Field f, String source) {
        List<Evidence> evidence = new ArrayList<>();
        f.evidence().stream().filter(e -> e.kind() == Evidence.Kind.CROP).findFirst().ifPresent(evidence::add);
        if (source != null) {
            evidence.add(Evidence.dom(source));
        }
        return evidence;
    }

    private static Validation validation(Field f, String rule) {
        if (f.validations() == null) {
            return null;
        }
        return f.validations().stream().filter(v -> v.rule().equals(rule)).findFirst().orElse(null);
    }

    private static Integer parse(String value, Integer fallback) {
        try {
            return value == null ? fallback : Integer.valueOf(value);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static String describe(LabelSource source) {
        return switch (source) {
            case NEAREST_TEXT -> "the text next to it";
            case PLACEHOLDER -> "its placeholder text";
            case NEARBY_LABEL -> "a label placed near it";
            case TITLE -> "its tooltip";
            default -> source.name().toLowerCase().replace('_', ' ');
        };
    }

    private static String capitalise(String s) {
        // Statements may start with a quoted name: keep the quote, capitalise the first letter after it.
        int i = s.startsWith("“") ? 1 : 0;
        return s.substring(0, i) + Character.toUpperCase(s.charAt(i)) + s.substring(i + 1);
    }
}
