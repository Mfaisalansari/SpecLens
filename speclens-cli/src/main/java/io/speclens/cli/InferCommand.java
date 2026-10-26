package io.speclens.cli;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;

@Command(name = "infer", description = "Turn observations.json into spec.json (requirements, descriptions, open questions).")
final class InferCommand implements Callable<Integer> {

    @Option(names = {"-o", "--out"}, defaultValue = "speclens-out",
            description = "Output directory holding observations.json (default: ${DEFAULT-VALUE}).")
    Path outDir;

    @Option(names = {"-a", "--app-name"}, description = "Application name for the document. Defaults to the host name.")
    String applicationName;

    @Override
    public Integer call() {
        if (!Files.isRegularFile(outDir.resolve(Pipeline.OBSERVATIONS))) {
            System.err.println("No " + Pipeline.OBSERVATIONS + " in " + outDir.toAbsolutePath() + ". Run 'speclens capture' first.");
            return 2;
        }
        Pipeline.infer(outDir, applicationName);
        return 0;
    }
}
