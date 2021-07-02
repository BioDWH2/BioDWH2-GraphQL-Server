package de.unibi.agbi.biodwh2.graphql.schema;

import de.unibi.agbi.biodwh2.core.lang.Type;

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
        writeInterfaces(writer);
        writeQueryType(writer);
        for (final GraphSchema.NodeType type : schema.getNodeTypes())
            writeNodeType(writer, schema, type);
        //for (final GraphSchema.EdgeType type : schema.getEdgeTypes())
        //    writeEdgeType(writer, type);
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
        writeLine(writer, "}");
    }

    private void writeQueryType(final BufferedWriter writer) throws IOException {
        writeLine(writer, "type QueryType {");
        writeQueryTypeNodeEndpoints(writer);
        //writeQueryTypeEdgeEndpoints(writer);
        writeLine(writer, "}");
    }

    private void writeQueryTypeNodeEndpoints(final BufferedWriter writer) throws IOException {
        for (final GraphSchema.NodeType nodeType : schema.getNodeTypes()) {
            final String arguments = nodeType.propertyKeyTypes.keySet().stream().map(
                    key -> mapPropertyToKeyTypeDefinition(nodeType, key).replace("!", "")).collect(
                    Collectors.joining(", "));
            writeLine(writer, "  " + nodeType.label + "(" + arguments + "): [" + nodeType.label + "!]!");
        }
    }

    private String mapPropertyToKeyTypeDefinition(final GraphSchema.BaseType type, final String key) {
        return key + ": " + getGraphQLTypeName(key, type.propertyKeyTypes.get(key));
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

    /*
    private void writeQueryTypeEdgeEndpoints(final BufferedWriter writer) throws IOException {
        for (final GraphSchema.EdgeType type : schema.getEdgeTypes())
            writeQueryTypeEdgeEndpoint(writer, type);
    }

    private void writeQueryTypeEdgeEndpoint(final BufferedWriter writer,
                                            final GraphSchema.EdgeType type) throws IOException {
        for (final String fromLabel : type.fromLabels) {
            for (final String toLabel : type.toLabels) {
                final String edgeTypeName = getEdgeTypeName(fromLabel, type.label, toLabel);
                final String arguments = type.propertyKeyTypes.keySet().stream().map(
                        key -> mapPropertyToKeyTypeDefinition(type, key).replace("!", "")).collect(
                        Collectors.joining(", "));
                writeLine(writer, "  " + edgeTypeName + "(" + arguments + "): [" + edgeTypeName + "]!");
            }
        }
    }

    private String getEdgeTypeName(final String fromLabel, final String label, final String toLabel) {
        return fromLabel + "__" + label + "__" + toLabel + "__Edge";
    }
    */

    private void writeNodeType(final BufferedWriter writer, final GraphSchema schema,
                               final GraphSchema.NodeType type) throws IOException {
        writeLine(writer, "type " + type.label + " implements Node {");
        writeTypeProperties(writer, type);
        for (final GraphSchema.EdgeType edgeType : schema.getEdgeTypes())
            if (edgeType.fromLabels.contains(type.label)) {
                final String arguments = edgeType.propertyKeyTypes.keySet().stream().map(
                        key -> mapPropertyToKeyTypeDefinition(edgeType, key).replace("!", "")).collect(
                        Collectors.joining(", "));
                writeLine(writer, "  " + edgeType.label + '(' + arguments + "): [Node!]!");
            }
        writeLine(writer, "}");
    }

    private void writeTypeProperties(final BufferedWriter writer, final GraphSchema.BaseType type) throws IOException {
        for (final String key : type.propertyKeyTypes.keySet())
            writeLine(writer, "  " + mapPropertyToKeyTypeDefinition(type, key));
    }

    /*
    private void writeEdgeType(final BufferedWriter writer, final GraphSchema.EdgeType type) throws IOException {
        for (final String fromLabel : type.fromLabels) {
            for (final String toLabel : type.toLabels) {
                writeLine(writer, "type " + getEdgeTypeName(fromLabel, type.label, toLabel) + " implements Edge {");
                writeTypeProperties(writer, type);
                writeLine(writer, "  _source: " + fromLabel + "!");
                writeLine(writer, "  _target: " + toLabel + "!");
                writeLine(writer, "}");
            }
        }
    }
    */
}
