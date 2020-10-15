package de.unibi.agbi.biodwh2.graphql.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.unibi.agbi.biodwh2.core.model.graph.Graph;
import de.unibi.agbi.biodwh2.core.schema.GraphSchema;
import de.unibi.agbi.biodwh2.graphql.server.model.CmdArgs;
import de.unibi.agbi.biodwh2.graphql.server.model.RequestBody;
import graphql.ExecutionInput;
import graphql.ExecutionResult;
import graphql.GraphQL;
import graphql.schema.GraphQLSchema;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.SchemaGenerator;
import graphql.schema.idl.SchemaParser;
import graphql.schema.idl.TypeDefinitionRegistry;
import io.javalin.Javalin;
import io.javalin.core.JavalinConfig;
import io.javalin.http.Context;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.stream.Collectors;

public class GraphQLServer {
    private static final Logger LOGGER = LoggerFactory.getLogger(GraphQLServer.class);

    private static GraphSchema schema;
    private static GraphQL graphQL;

    private GraphQLServer() {
    }

    public static void main(final String... args) {
        final CmdArgs commandLine = parseCommandLine(args);
        new GraphQLServer().run(commandLine);
    }

    private static CmdArgs parseCommandLine(final String... args) {
        final CmdArgs result = new CmdArgs();
        final CommandLine cmd = new CommandLine(result);
        cmd.parseArgs(args);
        return result;
    }

    private void run(final CmdArgs commandLine) {
        if (commandLine.start != null)
            startWorkspaceServer(commandLine);
        else
            printHelp(commandLine);
    }

    private void startWorkspaceServer(final CmdArgs commandLine) {
        final String workspacePath = commandLine.start;
        final int port = commandLine.port != null ? commandLine.port : 8090;
        if (StringUtils.isEmpty(workspacePath) || !Paths.get(workspacePath).toFile().exists()) {
            LOGGER.error("Workspace path '" + workspacePath + "' was not found");
            printHelp(commandLine);
            return;
        }
        LOGGER.info("Load database...");
        final Graph graph = new Graph(Paths.get(workspacePath, "sources", "mapped.db").toString(), true);
        LOGGER.info("Setup GraphQL...");
        SchemaParser schemaParser = new SchemaParser();
        File schemaFile = Paths.get(workspacePath, "sources", "mapped.graphqls").toFile();
        TypeDefinitionRegistry typeRegistry = schemaParser.parse(schemaFile);
        SchemaGenerator schemaGenerator = new SchemaGenerator();
        RuntimeWiring wiring = buildRuntimeWiring(graph);
        GraphQLSchema schema = schemaGenerator.makeExecutableSchema(typeRegistry, wiring);
        graphQL = GraphQL.newGraphQL(schema).build();
        LOGGER.info("Start server...");
        Javalin app = Javalin.create(this::configureJavalin).start(port);
        app.post("/", GraphQLServer::handleRootPost);
        openBrowser(port);
    }

    private static RuntimeWiring buildRuntimeWiring(final Graph graph) {
        return RuntimeWiring.newRuntimeWiring().type("QueryType", typeWiring -> typeWiring
                .defaultDataFetcher(new GraphDataFetcher(graph))).build();
    }

    private void configureJavalin(final JavalinConfig config) {
        config.defaultContentType = "application/json";
        config.enableCorsForAllOrigins();
        config.showJavalinBanner = false;
    }

    private static void handleRootPost(Context ctx) throws IOException {
        RequestBody body = ctx.bodyValidator(RequestBody.class).getOrNull();
        body.query = Arrays.stream(body.query.split("\n")).filter(l -> l.length() > 0 && !l.trim().startsWith("#"))
                           .collect(Collectors.joining("\n"));
        ExecutionInput executionInput = ExecutionInput.newExecutionInput().query(body.query).build();
        ExecutionResult executionResult = graphQL.execute(executionInput);
        ObjectMapper objectMapper = new ObjectMapper();
        ctx.result(objectMapper.writeValueAsString(executionResult.toSpecification()));
    }

    private void openBrowser(final int port) {
        if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
            try {
                Desktop.getDesktop().browse(
                        new URI("https://biodwh2.github.io/graphql/?endpoint=http://localhost:" + port + "/"));
            } catch (IOException | URISyntaxException e) {
                if (LOGGER.isErrorEnabled())
                    LOGGER.error("Failed to open Browser", e);
            }
        }
    }

    private void printHelp(final CmdArgs commandLine) {
        CommandLine.usage(commandLine, System.out);
    }
}
