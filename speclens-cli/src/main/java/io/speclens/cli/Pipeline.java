package io.speclens.cli;

import io.speclens.infer.SpecInferrer;
import io.speclens.model.Json;
import io.speclens.model.Observations;
import io.speclens.model.Spec;
import io.speclens.report.SrsReport;

import java.nio.file.Path;

/** The file names each stage reads and writes inside an output directory. */
final class Pipeline {

    static final String OBSERVATIONS = "observations.json";
    static final String SPEC = "spec.json";
    static final String REPORT = "spec.html";

    private Pipeline() {
    }

    static Spec infer(Path outDir, String applicationName) {
        Observations observations = Json.read(outDir.resolve(OBSERVATIONS), Observations.class);
        Spec spec = new SpecInferrer().infer(observations, applicationName);
        Json.write(outDir.resolve(SPEC), spec);
        System.out.println("Wrote " + outDir.resolve(SPEC) + " (" + spec.questions().size() + " open questions)");
        return spec;
    }

    static void report(Path outDir, Spec spec) {
        Path html = outDir.resolve(REPORT);
        new SrsReport().render(spec, html);
        System.out.println("Wrote " + html);
    }
}
