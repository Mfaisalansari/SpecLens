package io.speclens.infer;

import io.speclens.model.Field;
import io.speclens.model.FieldKind;
import io.speclens.model.Observations;
import io.speclens.model.Question;
import io.speclens.model.Rule;
import io.speclens.model.Screen;
import io.speclens.model.ScreenSpec;
import io.speclens.model.Spec;

import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static io.speclens.infer.Phrases.count;
import static io.speclens.infer.Phrases.isAre;
import static io.speclens.infer.Phrases.join;
import static io.speclens.infer.Phrases.quote;

/** observations.json → spec.json: requirements, descriptions and open questions per screen. */
public final class SpecInferrer {

    private static final Pattern LOGIN = Pattern.compile("\\b(log ?in|sign ?in)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern REGISTER = Pattern.compile("\\b(regist\\w*|sign ?up|create (an )?account)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern SEARCH = Pattern.compile("\\bsearch\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern CONTACT = Pattern.compile("\\b(contact|enquiry|inquiry|feedback)\\b", Pattern.CASE_INSENSITIVE);

    public Spec infer(Observations observations, String applicationName) {
        List<Screen> screens = observations.screens();
        Map<Screen, List<String>> keys = new LinkedHashMap<>();
        screens.forEach(s -> keys.put(s, keys(s)));
        Set<String> commonKeys = commonKeys(keys);
        Set<String> usedCodes = new HashSet<>();
        List<RuleWriter.QuestionDraft> drafts = new ArrayList<>();

        ScreenSpec common = commonSpec(keys, screens.size(), commonKeys, usedCodes, drafts);
        List<ScreenSpec> specs = new ArrayList<>();
        for (Screen screen : screens) {
            List<String> screenKeys = keys.get(screen);
            List<Field> own = new ArrayList<>();
            for (int i = 0; i < screen.fields().size(); i++) {
                if (!commonKeys.contains(screenKeys.get(i))) {
                    own.add(screen.fields().get(i));
                }
            }
            String name = screen.heading() != null ? screen.heading() : screen.title() != null ? screen.title() : screen.id();
            String code = uniqueCode(Phrases.code(name), usedCodes);
            RuleWriter writer = new RuleWriter(screen.id(), code, "on " + quote(name), own);
            own.forEach(writer::write);
            drafts.addAll(writer.questions());
            specs.add(new ScreenSpec(screen.id(), code, name, screen.url(), screen.screenshot(),
                    summary(name, screen, own, !commonKeys.isEmpty()), own, writer.rules(), null));
        }

        List<Question> questions = new ArrayList<>();
        for (RuleWriter.QuestionDraft d : drafts) {
            questions.add(new Question("Q-%02d".formatted(questions.size() + 1), d.screenId(), d.fieldId(), d.text()));
        }
        String application = applicationName != null ? applicationName : applicationName(screens);
        return new Spec(Spec.SCHEMA_VERSION, Instant.now(), application, specs, common, questions);
    }

    /**
     * Elements that appear on most screens (site header, footer, newsletter box) are layout,
     * not part of any one screen's function: describe them once.
     */
    private static Set<String> commonKeys(Map<Screen, List<String>> keys) {
        if (keys.size() < 2) {
            return Set.of();
        }
        int threshold = Math.max(2, (int) Math.ceil(keys.size() * 0.6));
        Map<String, Integer> seen = new LinkedHashMap<>();
        for (List<String> screenKeys : keys.values()) {
            screenKeys.forEach(k -> seen.merge(k, 1, Integer::sum));
        }
        return seen.entrySet().stream().filter(e -> e.getValue() >= threshold).map(Map.Entry::getKey)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private static ScreenSpec commonSpec(Map<Screen, List<String>> keys, int screenCount, Set<String> commonKeys,
                                         Set<String> usedCodes, List<RuleWriter.QuestionDraft> drafts) {
        if (commonKeys.isEmpty()) {
            return null;
        }
        Map<String, Field> representatives = new LinkedHashMap<>();
        Set<String> appearsOn = new LinkedHashSet<>();
        keys.forEach((screen, screenKeys) -> {
            for (int i = 0; i < screenKeys.size(); i++) {
                if (commonKeys.contains(screenKeys.get(i))) {
                    representatives.putIfAbsent(screenKeys.get(i), screen.fields().get(i));
                    appearsOn.add(screen.id());
                }
            }
        });
        // Renumber so ids are unique within the shared section.
        List<Field> fields = new ArrayList<>();
        for (Field f : representatives.values()) {
            fields.add(withId(f, "c%02d".formatted(fields.size() + 1)));
        }
        String code = uniqueCode("COM", usedCodes);
        RuleWriter writer = new RuleWriter("common", code, "among the shared elements", fields);
        fields.forEach(writer::write);
        drafts.addAll(writer.questions());
        List<String> summary = List.of("These elements appear on " + appearsOn.size() + " of the " + screenCount
                + " captured screens, typically as part of the page header, footer or side bar. They are described once here and left out of the individual screens.");
        return new ScreenSpec("common", code, "Shared elements", null, null, summary, fields, writer.rules(), List.copyOf(appearsOn));
    }

    private static List<String> summary(String name, Screen screen, List<Field> own, boolean hasCommon) {
        List<Field> inputs = own.stream().filter(f -> f.kind() != FieldKind.ACTION).toList();
        List<Field> actions = own.stream().filter(f -> f.kind() == FieldKind.ACTION).toList();
        List<String> paragraphs = new ArrayList<>();

        StringBuilder intro = new StringBuilder("The " + quote(name) + " screen is reached at " + screen.url() + ".");
        String purpose = purpose(name, inputs, actions);
        if (purpose != null) {
            intro.append(' ').append(purpose);
        }
        intro.append(" It contains ").append(count(inputs.size(), "input field")).append(" and ")
                .append(count(distinctLabels(actions).size(), "action")).append('.');
        String frame = own.stream().map(Field::frame).filter(Objects::nonNull).findFirst().orElse(null);
        if (frame != null) {
            intro.append(" The form is rendered inside the embedded frame ").append(quote(frame)).append('.');
        }
        paragraphs.add(intro.toString());

        if (!inputs.isEmpty()) {
            StringBuilder data = new StringBuilder();
            List<String> required = inputs.stream().filter(Field::required).map(Phrases::name).toList();
            if (required.isEmpty()) {
                data.append("No field is marked as mandatory.");
            } else if (required.size() == inputs.size()) {
                data.append(required.size() == 1 ? "The only field, " + required.getFirst() + ", is mandatory." : "All fields are mandatory.");
            } else {
                data.append(count(required.size(), "field")).append(' ').append(isAre(required.size()))
                        .append(" mandatory: ").append(join(required)).append('.');
            }
            List<String> choices = inputs.stream().filter(f -> f.kind() == FieldKind.CHOICE).map(Phrases::name).toList();
            if (!choices.isEmpty()) {
                data.append(' ').append(capitalise(join(choices))).append(' ').append(isAre(choices.size()))
                        .append(" chosen from predefined values.");
            }
            List<String> locked = inputs.stream().filter(f -> f.readOnly() || f.disabled()).map(Phrases::name).toList();
            if (!locked.isEmpty()) {
                data.append(' ').append(capitalise(join(locked))).append(' ').append(isAre(locked.size()))
                        .append(" not editable when the screen opens.");
            }
            paragraphs.add(data.toString());
        }

        if (!actions.isEmpty()) {
            Map<String, Long> labels = distinctLabels(actions);
            List<String> listed = labels.entrySet().stream()
                    .map(e -> quote(e.getKey()) + (e.getValue() > 1 ? " (" + e.getValue() + " times)" : "")).toList();
            StringBuilder act = new StringBuilder("The user can act on the screen through ").append(join(listed)).append('.');
            List<String> disabled = actions.stream().filter(Field::disabled).map(Phrases::name).distinct().toList();
            if (!disabled.isEmpty()) {
                act.append(' ').append(capitalise(join(disabled))).append(' ').append(isAre(disabled.size()))
                        .append(" unavailable when the screen opens.");
            }
            paragraphs.add(act.toString());
        }
        if (hasCommon) {
            paragraphs.add("Elements shared with other screens are described in section 2.2.");
        }
        return paragraphs;
    }

    /** A cautious one-line purpose, only when the evidence is clear. */
    private static String purpose(String name, List<Field> inputs, List<Field> actions) {
        boolean hasPassword = inputs.stream().anyMatch(f -> "password".equals(f.inputType()));
        String actionText = actions.stream().map(f -> Objects.toString(f.label(), "")).collect(Collectors.joining(" "));
        if (LOGIN.matcher(name).find() || (hasPassword && LOGIN.matcher(actionText).find() && !REGISTER.matcher(name).find())) {
            return "It appears to let an existing user sign in.";
        }
        if (REGISTER.matcher(name).find() || (hasPassword && REGISTER.matcher(actionText).find())) {
            return "It appears to let a new user create an account.";
        }
        if (SEARCH.matcher(name).find()) {
            return "It appears to let the user search.";
        }
        if (CONTACT.matcher(name).find()) {
            return "It appears to let the user send an enquiry.";
        }
        if (inputs.size() >= 3) {
            return "It appears to be a data-entry form.";
        }
        if (inputs.isEmpty() && !actions.isEmpty()) {
            return "It presents information and actions but takes no data entry.";
        }
        return null;
    }

    private static Map<String, Long> distinctLabels(List<Field> actions) {
        return actions.stream().collect(Collectors.groupingBy(f -> f.label() != null ? f.label() : f.id(),
                LinkedHashMap::new, Collectors.counting()));
    }

    /**
     * One key per field: same kind, label and locator (or name) means the same element on another
     * screen. The occurrence number keeps a page's own second "Search" button apart from the header's.
     */
    private static List<String> keys(Screen screen) {
        Map<String, Integer> occurrences = new LinkedHashMap<>();
        List<String> keys = new ArrayList<>();
        for (Field f : screen.fields()) {
            String base = f.kind() + "|" + f.label() + "|" + (f.locator() != null ? f.locator() : f.name());
            keys.add(base + "#" + occurrences.merge(base, 1, Integer::sum));
        }
        return keys;
    }

    private static String uniqueCode(String base, Set<String> used) {
        String code = base;
        for (int i = 2; !used.add(code); i++) {
            code = base + i;
        }
        return code;
    }

    private static String applicationName(List<Screen> screens) {
        if (screens.isEmpty()) {
            return "Application";
        }
        try {
            String host = URI.create(screens.getFirst().url()).getHost();
            return host != null ? host : "Application";
        } catch (IllegalArgumentException e) {
            return "Application";
        }
    }

    private static Field withId(Field f, String id) {
        return new Field(id, f.kind(), f.role(), f.tag(), f.inputType(), f.name(), f.property(), f.testId(),
                f.controlHint(), f.label(), f.labelSource(), f.labelConfidence(), f.required(), f.requiredSource(),
                f.validations(), f.maxLength(), f.minLength(), f.pattern(), f.min(), f.max(), f.readOnly(),
                f.disabled(), f.defaultValue(), f.placeholder(), f.options(), f.locator(), f.frame(), f.box(), f.evidence());
    }

    private static String capitalise(String s) {
        int i = s.startsWith("“") ? 1 : 0;
        return s.isEmpty() ? s : s.substring(0, i) + Character.toUpperCase(s.charAt(i)) + s.substring(i + 1);
    }
}
