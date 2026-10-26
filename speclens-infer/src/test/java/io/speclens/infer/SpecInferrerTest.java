package io.speclens.infer;

import io.speclens.model.Json;
import io.speclens.model.Observations;
import io.speclens.model.Question;
import io.speclens.model.Rule;
import io.speclens.model.RuleType;
import io.speclens.model.ScreenSpec;
import io.speclens.model.Spec;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpecInferrerTest {

    static Spec spec;
    static ScreenSpec register;

    @BeforeAll
    static void infer() throws URISyntaxException {
        Path file = Path.of(SpecInferrerTest.class.getResource("/observations.json").toURI());
        spec = new SpecInferrer().infer(Json.read(file, Observations.class), null);
        register = spec.screens().getFirst();
    }

    @Test
    void namesTheDocumentAndScreens() {
        assertEquals("shop.example", spec.application());
        assertEquals("REG", register.code());
        assertEquals("SIG", spec.screens().get(1).code());
    }

    @Test
    void movesElementsOnEveryScreenToTheSharedSection() {
        assertEquals(List.of("Search"), spec.common().fields().stream().map(f -> f.label()).toList());
        assertTrue(register.fields().stream().noneMatch(f -> "Search".equals(f.label())));
    }

    @Test
    void writesRequirementSentencesWithTheAppsOwnMessages() {
        assertEquals("The system shall require the user to enter “Email”. If it is left empty, the system shall display “Email is required.”",
                rule(RuleType.REQUIRED, "f002").statement());
        assertEquals("“Email” shall be a valid email address. Otherwise the system shall display “Wrong email”.",
                rule(RuleType.FORMAT, "f002").statement());
        assertEquals("“Password” shall be between 6 and 20 characters long. Otherwise the system shall display “At least 6 characters.”",
                rule(RuleType.LENGTH, "f003").statement());
        Rule match = rule(RuleType.CROSS_FIELD, "f004");
        assertEquals("“Confirm password” shall be identical to “Password”. Otherwise the system shall display “Passwords do not match.”",
                match.statement());
        assertEquals(List.of("f004", "f003"), match.fieldIds());
    }

    @Test
    void leavesPromptOptionsOutOfAllowedValuesAndDefaults() {
        assertEquals("The user shall choose “Country” from one of: “India” and “United Kingdom”.",
                rule(RuleType.ALLOWED_VALUES, "f005").statement());
        assertTrue(register.rules().stream().noneMatch(r -> r.type() == RuleType.DEFAULT_VALUE));
    }

    @Test
    void describesTheScreenInProse() {
        assertEquals("The “Register” screen is reached at https://shop.example/register. It appears to let a new user create an account."
                + " It contains 4 input fields and 1 action.", register.summary().getFirst());
        assertTrue(register.summary().get(2).contains("“Register” is unavailable when the screen opens."));
        assertTrue(spec.screens().get(1).summary().getFirst().contains("It appears to let an existing user sign in."));
    }

    @Test
    void asksAboutGuessedLabelsAndDisabledActions() {
        List<String> questions = spec.questions().stream().map(Question::text).toList();
        assertEquals(2, questions.size());
        assertTrue(questions.get(0).contains("“Country”") && questions.get(0).contains("the text next to it"));
        assertEquals("What makes the “Register” action on “Register” available?", questions.get(1));
        assertEquals("Q-01", spec.questions().getFirst().id());
    }

    private static Rule rule(RuleType type, String fieldId) {
        return register.rules().stream().filter(r -> r.type() == type && r.fieldIds().getFirst().equals(fieldId))
                .findFirst().orElseThrow(() -> new AssertionError("no " + type + " rule for " + fieldId));
    }
}
