package io.speclens.report;

import com.fasterxml.jackson.core.type.TypeReference;
import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import freemarker.template.TemplateExceptionHandler;
import io.speclens.model.Json;
import io.speclens.model.Spec;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Renders spec.json as a Software Requirements Specification in HTML (print it to PDF if needed).
 * Image paths are relative, so the HTML must sit in the output directory next to {@code screens/}.
 */
public final class SrsReport {

    private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() { };
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH);

    private final Configuration freemarker;

    public SrsReport() {
        freemarker = new Configuration(Configuration.VERSION_2_3_34);
        freemarker.setClassForTemplateLoading(SrsReport.class, "");
        freemarker.setDefaultEncoding("UTF-8");
        freemarker.setTemplateExceptionHandler(TemplateExceptionHandler.RETHROW_HANDLER);
        freemarker.setLogTemplateExceptions(false);
    }

    public void render(Spec spec, Path htmlFile) {
        Map<String, Object> model = new HashMap<>();
        // Plain maps keep the template independent of how Freemarker wraps records and enums.
        model.put("spec", Json.mapper().convertValue(spec, MAP));
        model.put("date", DATE.format(spec.createdAt().atZone(ZoneId.systemDefault())));
        Map<String, String> screenNames = new LinkedHashMap<>();
        spec.screens().forEach(s -> screenNames.put(s.screenId(), s.name()));
        if (spec.common() != null) {
            screenNames.put("common", spec.common().name());
        }
        model.put("screenNames", screenNames);
        model.put("requirementCount", spec.screens().stream().mapToInt(s -> s.rules().size()).sum()
                + (spec.common() == null ? 0 : spec.common().rules().size()));
        try {
            Files.createDirectories(htmlFile.toAbsolutePath().getParent());
            Template template = freemarker.getTemplate("srs.ftlh");
            try (Writer out = Files.newBufferedWriter(htmlFile, StandardCharsets.UTF_8)) {
                template.process(model, out);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Could not write " + htmlFile, e);
        } catch (TemplateException e) {
            throw new IllegalStateException("SRS template failed: " + e.getMessage(), e);
        }
    }
}
