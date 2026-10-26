package io.speclens.cli;

import picocli.CommandLine;
import picocli.CommandLine.Command;

@Command(
        name = "speclens",
        mixinStandardHelpOptions = true,
        version = "SpecLens 0.1.0",
        description = "Infer a functional specification from a running web app, through its UI.",
        subcommands = {LoginCommand.class, CaptureCommand.class, InferCommand.class, ReportCommand.class})
public final class SpecLens {

    public static void main(String[] args) {
        System.exit(new CommandLine(new SpecLens()).execute(args));
    }
}
