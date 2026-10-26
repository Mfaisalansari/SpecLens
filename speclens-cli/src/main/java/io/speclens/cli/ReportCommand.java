package io.speclens.cli;

import io.speclens.model.Json;
import io.speclens.model.Spec;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;

@Command(name = "report", description = "Render spec.json as an SRS document (spec.html), without re-capturing.")
final class ReportCommand implements Callable<Integer> {

    @Option(names = {"-o", "--out"}, defaultValue = "speclens-out",
            description = "Output directory holding spec.json (default: ${DEFAULT-VALUE}).")
    Path outDir;

    @Override
    public Integer call() {
        Path specFile = outDir.resolve(Pipeline.SPEC);
        if (!Files.isRegularFile(specFile)) {
            System.err.println("No " + Pipeline.SPEC + " in " + outDir.toAbsolutePath() + ". Run 'speclens infer' first.");
            return 2;
        }
        Pipeline.report(outDir, Json.read(specFile, Spec.class));
        return 0;
    }
}
