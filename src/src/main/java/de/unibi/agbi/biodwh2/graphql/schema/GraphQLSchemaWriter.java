package de.unibi.agbi.biodwh2.graphql.schema;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.stream.Collectors;

public final class GraphQLSchemaWriter extends SchemaWriter {
    @SuppressWarnings("SpellCheckingInspection")
    public static final String EXTENSION = "graphqls";

    public GraphQLSchemaWriter(final GraphSchema schema) {
        super(schema);
    }

    @Override
    public void save(final String filePath) throws IOException {
        try (final OutputStream stream = Files.newOutputStream(Paths.get((filePath)));
             final BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(stream, StandardCharsets.UTF_8))) {
            save(writer);
        }
    }

    private void save(final BufferedWriter writer) throws IOException {
        writeMainSchema(writer);
        writeQueryType(writer);
        for (final GraphSchema.NodeType type : schema.getNodeTypes())
            writeNodeType(writer, type);
        for (final GraphSchema.EdgeType type : schema.getEdgeTypes())
            writeEdgeType(writer, type);
    }

    private void writeMainSchema(final BufferedWriter writer) throws IOException {
        writeLine(writer, "schema {");
        writeLine(writer, "  query: QueryType");
        writeLine(writer, "}");
    }

    private void writeLine(final BufferedWriter writer, final String line) throws IOException {
        writer.write(line);
        writer.newLine();
    }

    private void writeQueryType(final BufferedWriter writer) throws IOException {
        writeLine(writer, "type QueryType {");
        writeQueryTypeNodeEndpoints(writer);
        writeLine(writer, "}");
    }

    private void writeQueryTypeNodeEndpoints(final BufferedWriter writer) throws IOException {
        for (final GraphSchema.NodeType nodeType : schema.getNodeTypes()) {
            final String arguments = nodeType.propertyKeyTypes.keySet().stream().map(
                    key -> mapPropertyToKeyTypeDefinition(nodeType, key)).collect(Collectors.joining(", "));
            writeLine(writer, "  " + nodeType.label + "(" + arguments + "): [" + nodeType.label + "]!");
        }
    }

    private String mapPropertyToKeyTypeDefinition(final GraphSchema.Type type, final String key) {
        return key + ": " + getGraphQLTypeName(type.propertyKeyTypes.get(key));
    }

    private String getGraphQLTypeName(final Class<?> type) {
        if (type.isArray())
            return "[" + getGraphQLTypeName(type.getComponentType()) + "]";
        if (type == String.class)
            return "String";
        if (type == Integer.class || type == int.class)
            return "Int";
        if (type == Float.class || type == float.class)
            return "Float";
        if (type == Boolean.class || type == boolean.class)
            return "Boolean";
        return "String";
    }

    private void writeNodeType(final BufferedWriter writer, final GraphSchema.NodeType type) throws IOException {
        writeLine(writer, "type " + type.label + " {");
        writeTypeProperties(writer, type);
        writeLine(writer, "}");
    }

    private void writeTypeProperties(final BufferedWriter writer, final GraphSchema.Type type) throws IOException {
        for (final String key : type.propertyKeyTypes.keySet())
            writeLine(writer, "  " + mapPropertyToKeyTypeDefinition(type, key));
    }

    private void writeEdgeType(final BufferedWriter writer, final GraphSchema.EdgeType type) throws IOException {
        writeLine(writer, "type rel_" + type.label + " {");
        writeTypeProperties(writer, type);
        writeLine(writer, "}");
    }
}
