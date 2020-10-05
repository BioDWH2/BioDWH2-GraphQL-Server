package de.unibi.agbi.biodwh2.graphql.server.model;

import picocli.CommandLine;

public class CmdArgs {
    @CommandLine.Option(names = {"-h", "--help"}, usageHelp = true, description = "print this message")
    public boolean help;
    @CommandLine.Option(names = {
            "-s", "--start"
    }, arity = "1", paramLabel = "<workspacePath>", description = "Start a GraphQL server for the workspace")
    public String start;
}
