package io.speclens.capture;

import io.speclens.model.Field;
import io.speclens.model.FieldKind;
import io.speclens.model.LabelSource;
import io.speclens.model.Option;
import io.speclens.model.Screen;
import io.speclens.model.Validation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Captures a Pega-like fixture (case inside an iframe, generated ids, label-side required markers). */
class ScreenCapturerTest {

    @TempDir
    static Path outDir;

    static Screen screen;
    static Map<String, Field> byLabel;

    @BeforeAll
    static void capture() throws URISyntaxException {
        String url = ScreenCapturerTest.class.getResource("/fixtures/pega-like.html").toURI().toString();
        CaptureOptions options = CaptureOptions.defaults();
        try (BrowserSession session = BrowserSession.open(options)) {
            session.page().navigate(url);
            screen = new ScreenCapturer(options, msg -> { }).capture(session, outDir, null);
        }
        byLabel = screen.fields().stream().filter(f -> f.label() != null)
                .collect(Collectors.toMap(Field::label, Function.identity()));
    }

    @Test
    void namesTheScreenFromTheHeadingInsideTheCaseFrame() {
        assertEquals("Submission Details", screen.heading());
        assertEquals("submission-details", screen.id());
        assertTrue(Files.isRegularFile(outDir.resolve(screen.screenshot())));
    }

    @Test
    void findsEveryFieldAndActionInDocumentOrder() {
        List<String> labels = screen.fields().stream().map(Field::label).toList();
        assertEquals(Arrays.asList("Policy number", "Effective date", "Line of business", "Annual premium", "Broker",
                "Region and Office", "Select Office", "Contact email", "Gender", "Password", "Reference", null, "Payment frequency", "Paperless billing", "Notes", "Cancel", "Save", "Submit"), labels);
        assertEquals(3, screen.fields().stream().filter(f -> f.kind() == FieldKind.ACTION).count());
    }

    @Test
    void readsDeclaredConstraintsAndPegaMetadata() {
        Field policy = byLabel.get("Policy number");
        assertEquals(LabelSource.LABEL_FOR, policy.labelSource());
        assertTrue(policy.required());
        assertEquals(10, policy.maxLength());
        assertEquals("[0-9]{10}", policy.pattern());
        assertEquals("pyWorkPage.PolicyNumber", policy.property());
        assertEquals("[data-test-id=\"202401011200PolicyNo\"]", policy.locator());
        assertEquals("PegaGadget0Ifr", policy.frame());

        assertEquals("DatePicker", byLabel.get("Effective date").controlHint());
        assertEquals("01/11/2026", byLabel.get("Effective date").defaultValue());

        Field premium = byLabel.get("Annual premium");
        assertEquals(LabelSource.NEAREST_TEXT, premium.labelSource());
        assertEquals("asterisk in label", premium.requiredSource());
        assertEquals("0", premium.min());
        assertEquals("1000000", premium.max());

        Field notes = byLabel.get("Notes");
        assertTrue(notes.readOnly());
        assertEquals(500, notes.maxLength());
        assertFalse(notes.required());
        assertNull(notes.requiredSource());

        assertTrue(byLabel.get("Save").disabled());
    }

    @Test
    void enumeratesNativeCustomAndRadioChoices() {
        Field lob = byLabel.get("Line of business");
        assertEquals(LabelSource.WRAPPING_LABEL, lob.labelSource());
        assertEquals("required attribute", lob.requiredSource());
        assertEquals(List.of("Select...", "Auto", "Home", "Life"), labels(lob.options()));
        assertEquals("Home", lob.defaultValue());

        Field broker = byLabel.get("Broker");
        assertEquals(LabelSource.ARIA_LABELLEDBY, broker.labelSource());
        assertEquals(List.of("Acme Brokers", "Northwind Insurance", "Contoso Risk"), labels(broker.options()));

        Field frequency = byLabel.get("Payment frequency");
        assertEquals("radiogroup", frequency.role());
        assertEquals(LabelSource.FIELDSET_LEGEND, frequency.labelSource());
        assertEquals(List.of("Monthly", "Annual"), labels(frequency.options()));
        assertEquals("Monthly", frequency.defaultValue());
    }

    @Test
    void cropsEveryFieldIncludingVisuallyHiddenCheckboxes() {
        for (Field field : screen.fields()) {
            assertTrue(field.evidence().stream().anyMatch(e -> e.path() != null && Files.isRegularFile(outDir.resolve(e.path()))),
                    "no crop for " + field.label());
        }
        assertEquals("unchecked", byLabel.get("Paperless billing").defaultValue());
    }

    @Test
    void handlesReactSelectStyleWidgets() {
        Field region = byLabel.get("Region and Office");
        assertEquals("combobox", region.role());
        assertEquals(LabelSource.NEARBY_LABEL, region.labelSource());
        assertNull(region.locator(), "generated react-select ids must not become locators");

        // Its row label belongs to the first widget, so the disabled one falls back to its placeholder.
        Field office = byLabel.get("Select Office");
        assertEquals(LabelSource.PLACEHOLDER, office.labelSource());
        assertTrue(office.disabled());
        assertTrue(office.evidence().stream().anyMatch(e -> e.path() != null), "hidden input should crop its visible box");

        Field email = byLabel.get("Contact email");
        assertEquals("email", email.inputType());
        assertEquals("#contactEmail", email.locator());
    }

    @Test
    void readsAspNetValidationAndNearbyMarkers() {
        assertEquals(List.of("Male", "Female"), labels(byLabel.get("Gender").options()));

        Field password = byLabel.get("Password");
        assertEquals("data-val-required: \"Password is required.\"", password.requiredSource());
        Validation length = password.validations().stream().filter(v -> v.rule().equals("length")).findFirst().orElseThrow();
        assertEquals("Must be 6 to 20 characters.", length.message());
        assertEquals(Map.of("min", "6", "max", "20"), length.params());
        assertNull(password.defaultValue());

        assertEquals("required marker next to field", byLabel.get("Reference").requiredSource());

        Field search = screen.fields().stream().filter(f -> "q".equals(f.name())).findFirst().orElseThrow();
        assertEquals(LabelSource.NONE, search.labelSource(), "navigation links must not become a label");
        assertNull(byLabel.get("Submit").defaultValue());
    }

    private static List<String> labels(List<Option> options) {
        return options.stream().map(Option::label).toList();
    }
}
