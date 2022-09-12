package de.unibi.agbi.biodwh2.graphql.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.unibi.agbi.biodwh2.core.model.graph.Graph;
import de.unibi.agbi.biodwh2.core.net.BioDWH2Updater;
import de.unibi.agbi.biodwh2.graphql.schema.GraphQLSchemaWriter;
import de.unibi.agbi.biodwh2.graphql.schema.GraphSchema;
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
import java.io.FileWriter;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.stream.Collectors;

public class GraphQLServer {
    private static final Logger LOGGER = LoggerFactory.getLogger(GraphQLServer.class);
    private static final String DATABASE_FILE_NAME = "mapped." + Graph.EXTENSION;
    private static final String SCHEMA_FILE_NAME = "mapped." + GraphQLSchemaWriter.EXTENSION;

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
        BioDWH2Updater.checkForUpdate("BioDWH2-GraphQL-Server",
                                      "https://api.github.com/repos/BioDWH2/BioDWH2-GraphQL-Server/releases");
        if (commandLine.start != null)
            startWorkspaceServer(commandLine);
        else
            printHelp(commandLine);
    }

    private void startWorkspaceServer(final CmdArgs commandLine) {
        final String workspacePath = commandLine.start;
        if (!verifyWorkspaceExists(workspacePath)) {
            printHelp(commandLine);
            return;
        }
        final Path graphqlPath = Paths.get(workspacePath, "graphql");
        final int port = commandLine.port != null ? commandLine.port : 8090;
        LOGGER.info("Load database...");
        final String workspaceGraphHash = getWorkspaceGraphHash(workspacePath);
        final Graph graph = new Graph(Paths.get(workspacePath, "sources", DATABASE_FILE_NAME), true, true);
        updateSchemaIfNecessary(graphqlPath, graph, workspaceGraphHash);
        LOGGER.info("Setup GraphQL...");
        final SchemaParser schemaParser = new SchemaParser();
        final File schemaFile = Paths.get(graphqlPath.toString(), SCHEMA_FILE_NAME).toFile();
        final TypeDefinitionRegistry typeRegistry = schemaParser.parse(schemaFile);
        final SchemaGenerator schemaGenerator = new SchemaGenerator();
        final RuntimeWiring wiring = buildRuntimeWiring(graph);
        final GraphQLSchema schema = schemaGenerator.makeExecutableSchema(typeRegistry, wiring);
        graphQL = GraphQL.newGraphQL(schema).build();
        LOGGER.info("Start server...");
        final Javalin app = Javalin.create(this::configureJavalin).start(port);
        app.post("/", GraphQLServer::handleRootPost);
        openBrowser(port);
    }

    private boolean verifyWorkspaceExists(final String workspacePath) {
        if (StringUtils.isEmpty(workspacePath) || !Paths.get(workspacePath).toFile().exists()) {
            if (LOGGER.isErrorEnabled())
                LOGGER.error("Workspace path '" + workspacePath + "' was not found");
            return false;
        }
        if (LOGGER.isInfoEnabled())
            LOGGER.info("Using workspace directory '" + workspacePath + "'");
        return true;
    }

    private String getWorkspaceGraphHash(final String workspacePath) {
        try {
            return HashUtils.getMd5HashFromFile(Paths.get(workspacePath, "sources", DATABASE_FILE_NAME).toString());
        } catch (IOException e) {
            if (LOGGER.isWarnEnabled())
                LOGGER.warn("Failed to check hash of workspace mapped graph", e);
        }
        return "";
    }

    private void updateSchemaIfNecessary(final Path graphqlPath, final Graph graph, final String workspaceGraphHash) {
        try {
            if (Files.notExists(graphqlPath))
                Files.createDirectory(graphqlPath);
            final Path hashFilePath = Paths.get(graphqlPath.toString(), "checksum.txt");
            final Path schemaFilePath = Paths.get(graphqlPath.toString(), SCHEMA_FILE_NAME);
            boolean upToDate = false;
            if (Files.exists(hashFilePath) && Files.exists(schemaFilePath)) {
                final String storedHash = new String(Files.readAllBytes(hashFilePath)).trim();
                upToDate = workspaceGraphHash.equals(storedHash);
            }
            if (!upToDate) {
                if (LOGGER.isInfoEnabled())
                    LOGGER.info("Regenerating GraphQL schema...");
                final GraphSchema schema = new GraphSchema(graph);
                new GraphQLSchemaWriter(schema).save(schemaFilePath.toString());
                final FileWriter writer = new FileWriter(hashFilePath.toFile());
                writer.write(workspaceGraphHash);
                writer.close();
            }
        } catch (IOException e) {
            if (LOGGER.isWarnEnabled())
                LOGGER.warn("Failed to check hash of workspace mapped graph", e);
        }
    }

    private static RuntimeWiring buildRuntimeWiring(final Graph graph) {
        return RuntimeWiring.newRuntimeWiring().wiringFactory(new GraphWiringFactory(graph)).build();
    }

    private void configureJavalin(final JavalinConfig config) {
        config.defaultContentType = "application/json";
        config.enableCorsForAllOrigins();
        config.showJavalinBanner = false;
    }

    private static void handleRootPost(final Context ctx) throws IOException {
        final RequestBody body;
        try {
            body = ctx.bodyValidator(RequestBody.class).get();
        } catch (RuntimeException ex) {
            ctx.result("");
            return;
        }
        if (StringUtils.isAllBlank(body.query)) {
            ctx.result("");
            return;
        }
        final String query = Arrays.stream(body.query.split("\n")).filter(l -> !isLineEmptyOrComment(l)).collect(
                Collectors.joining("\n"));
        final ExecutionInput.Builder builder = ExecutionInput.newExecutionInput();
        builder.query(query);
        if (body.operationName != null)
            builder.operationName(body.operationName);
        if (body.variables != null)
            builder.variables(body.variables);
        final ExecutionInput executionInput = builder.build();
        final ExecutionResult executionResult = graphQL.execute(executionInput);
        final ObjectMapper objectMapper = new ObjectMapper();
        ctx.result(objectMapper.writeValueAsString(executionResult.toSpecification()));
    }

    private static boolean isLineEmptyOrComment(final String l) {
        return l.length() == 0 || l.trim().startsWith("#");
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
