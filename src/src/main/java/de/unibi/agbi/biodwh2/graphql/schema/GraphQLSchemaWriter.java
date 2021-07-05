package de.unibi.agbi.biodwh2.graphql.schema;

import de.unibi.agbi.biodwh2.core.lang.Type;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Map;
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
             final OutputStreamWriter streamWriter = new OutputStreamWriter(stream, StandardCharsets.UTF_8);
             final BufferedWriter writer = new BufferedWriter(streamWriter)) {
            save(writer);
        }
    }

    private void save(final BufferedWriter writer) throws IOException {
        writeMainSchema(writer);
        writeInterfaces(writer);
        writeQueryType(writer);
        for (final GraphSchema.NodeType type : schema.getNodeTypes())
            writeNodeType(writer, schema, type);
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

    private void writeInterfaces(final BufferedWriter writer) throws IOException {
        writeLine(writer, "interface Node {");
        writeLine(writer, "  _id: ID!");
        writeLine(writer, "  _label: String!");
        writeLine(writer, "  _edges: [Edge!]!");
        writeLine(writer, "}");
        writeLine(writer, "interface Edge {");
        writeLine(writer, "  _id: ID!");
        writeLine(writer, "  _from_id: ID!");
        writeLine(writer, "  _to_id: ID!");
        writeLine(writer, "  _label: String!");
        writeLine(writer, "}");
    }

    private void writeQueryType(final BufferedWriter writer) throws IOException {
        writeLine(writer, "type QueryType {");
        for (final GraphSchema.BaseType type : schema.getNodeTypes())
            writeQueryTypeEndpoint(writer, type);
        for (final GraphSchema.BaseType type : schema.getEdgeTypes())
            writeQueryTypeEndpoint(writer, type);
        writeLine(writer, "}");
    }

    private void writeQueryTypeEndpoint(final BufferedWriter writer,
                                        final GraphSchema.BaseType type) throws IOException {
        final String arguments = buildArgumentsString(type.propertyKeyTypes);
        writeLine(writer, "  " + type.label + "(" + arguments + "): [" + type.label + "!]!");
    }

    private String buildArgumentsString(final Map<String, Type> propertyKeyTypes) {
        return propertyKeyTypes.keySet().stream().map(
                key -> mapPropertyToKeyTypeDefinition(key, propertyKeyTypes.get(key)).replace("!", "")).collect(
                Collectors.joining(", "));
    }

    private String mapPropertyToKeyTypeDefinition(final String key, final Type type) {
        return key + ": " + getGraphQLTypeName(key, type);
    }

    private String getGraphQLTypeName(final String key, final Type type) {
        if (type.isList())
            return "[" + getGraphQLTypeName(key, type.getComponentType()) + "]";
        return getGraphQLTypeName(key, type.getType());
    }

    private String getGraphQLTypeName(final String key, final Class<?> type) {
        if ("_id".equals(key) || "_to_id".equals(key) || "_from_id".equals(key))
            return "ID!";
        if (type == String.class)
            return "_label".equals(key) ? "String!" : "String";
        if (type == Integer.class || type == int.class)
            return "Int";
        if (type == Float.class || type == float.class)
            return "Float";
        if (type == Boolean.class || type == boolean.class)
            return "Boolean";
        return "String";
    }

    private void writeNodeType(final BufferedWriter writer, final GraphSchema schema,
                               final GraphSchema.NodeType type) throws IOException {
        writeLine(writer, "type " + type.label + " implements Node {");
        writeTypeProperties(writer, type);
        for (final GraphSchema.EdgeType edgeType : schema.getEdgeTypes())
            if (edgeType.fromLabels.contains(type.label)) {
                final String arguments = buildArgumentsString(type.propertyKeyTypes);
                writeLine(writer, "  " + edgeType.label + '(' + arguments + "): [" + edgeType.label + "!]!");
            }
        writeLine(writer, "  _edges: [Edge!]!");
        writeLine(writer, "}");
    }

    private void writeTypeProperties(final BufferedWriter writer, final GraphSchema.BaseType type) throws IOException {
        for (final String key : type.propertyKeyTypes.keySet())
            writeLine(writer, "  " + mapPropertyToKeyTypeDefinition(key, type.propertyKeyTypes.get(key)));
    }

    private void writeEdgeType(final BufferedWriter writer, final GraphSchema.EdgeType type) throws IOException {
        writeLine(writer, "type " + type.label + " implements Edge {");
        writeTypeProperties(writer, type);
        writeLine(writer, "  _from: Node!");
        writeLine(writer, "  _to: Node!");
        writeLine(writer, "}");
    }
}
