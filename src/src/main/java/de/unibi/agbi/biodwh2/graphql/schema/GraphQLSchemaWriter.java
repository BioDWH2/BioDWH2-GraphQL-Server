package de.unibi.agbi.biodwh2.graphql.schema;

import de.unibi.agbi.biodwh2.core.lang.Type;
import de.unibi.agbi.biodwh2.core.text.TextUtils;
import de.unibi.agbi.biodwh2.procedures.Registry;
import org.apache.commons.lang3.StringUtils;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
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
        writeScalars(writer);
        writeDirectives(writer);
        writeInterfaces(writer);
        writeMainSchema(writer);
        writeMutationType(writer);
        writeQueryType(writer);
        writeProcedures(writer);
        writeLine(writer, "# Node type definitions");
        for (final GraphSchema.NodeType type : schema.getNodeTypes())
            writeNodeType(writer, schema, type);
        writer.newLine();
        writeLine(writer, "# Edge type definitions");
        for (final GraphSchema.EdgeType type : schema.getEdgeTypes())
            writeEdgeType(writer, type);
    }

    private void writeScalars(final BufferedWriter writer) throws IOException {
        writeLine(writer, "# Extended scalar definitions");
        writeLine(writer, "scalar JSON");
        writeLine(writer, "scalar Object");
        writeLine(writer, "scalar Long");
        writer.newLine();
    }

    private void writeDirectives(final BufferedWriter writer) throws IOException {
        writeLine(writer, "# Primary directive definitions");
        writeLine(writer, "directive @GraphLabel(value: String!) on OBJECT");
        writeLine(writer, "directive @GraphProperty(value: String!) on FIELD_DEFINITION");
        writeLine(writer, "directive @Procedure(path: String!) on FIELD_DEFINITION");
        writeLine(writer, "directive @Limit(count: Int, skip: Int) on FIELD");
        writer.newLine();
    }

    private void writeInterfaces(final BufferedWriter writer) throws IOException {
        writeLine(writer, "# Primary interface definitions");
        writeLine(writer, "interface Node {");
        writeLine(writer, "  _id: ID!");
        writeLine(writer, "  _label: String!");
        writeLine(writer, "  _edges(_label: String): [Edge!]!");
        writeLine(writer, "}");
        writeLine(writer, "interface Edge {");
        writeLine(writer, "  _id: ID!");
        writeLine(writer, "  _label: String!");
        writeLine(writer, "  _from_id: ID!");
        writeLine(writer, "  _from: Node!");
        writeLine(writer, "  _to_id: ID!");
        writeLine(writer, "  _to: Node!");
        writeLine(writer, "}");
        writeLine(writer, "interface ProcedureContainer {");
        writeLine(writer, "  _id: ID!");
        writeLine(writer, "}");
        writer.newLine();
    }

    private void writeLine(final BufferedWriter writer, final String line) throws IOException {
        writer.write(line);
        writer.newLine();
    }

    private void writeMainSchema(final BufferedWriter writer) throws IOException {
        writeLine(writer, "# Schema");
        writeLine(writer, "schema {");
        writeLine(writer, "  query: QueryType");
        writeLine(writer, "  mutation: MutationType");
        writeLine(writer, "}");
        writer.newLine();
    }

    private void writeMutationType(final BufferedWriter writer) throws IOException {
        writeLine(writer, "type MutationType {");
        writeLine(writer, "  createGraphView(name: String!, nodeLabels: [String!]!, edgeLabels: [String!]!): ID!");
        writeLine(writer, "  createGraphViewIds(name: String!, nodeIds: [ID!]!, edgeIds: [ID!]!): ID!");
        writeLine(writer,
                  "  modifyGraphViewIds(id: ID!, addNodeIds: [ID!], addEdgeIds: [ID!], removeNodeIds: [ID!], removeEdgeIds: [ID!]): Boolean!");
        writeLine(writer, "  deleteGraphView(id: ID!): ID!");
        writeLine(writer, "}");
        writer.newLine();
    }

    private void writeQueryType(final BufferedWriter writer) throws IOException {
        writeLine(writer, "type QueryType {");
        writeLine(writer, "  _node(_id: ID!): Node");
        writeLine(writer, "  _edge(_id: ID!): Edge");
        writeLine(writer, "  _nodes(_label: String): [Node!]!");
        writeLine(writer, "  _edges(_to_id: ID, _from_id: ID, _label: String): [Edge!]!");
        writeLine(writer, "  _procedures: Procedures!");
        writer.newLine();
        writeLine(writer, "  # Node query endpoints");
        for (final GraphSchema.BaseType type : schema.getNodeTypes())
            writeQueryTypeEndpoint(writer, type);
        writer.newLine();
        writeLine(writer, "  # Edge query endpoints");
        for (final GraphSchema.BaseType type : schema.getEdgeTypes())
            writeQueryTypeEndpoint(writer, type);
        writeLine(writer, "}");
        writer.newLine();
    }

    private void writeQueryTypeEndpoint(final BufferedWriter writer,
                                        final GraphSchema.BaseType type) throws IOException {
        final String arguments = buildArgumentsString(type.propertyKeyTypes);
        writeLine(writer, "  " + type.fixedLabel() + "(" + arguments + "): [" + type.fixedLabel() + "!]!");
    }

    private String buildArgumentsString(final Map<String, Type> propertyKeyTypes) {
        return propertyKeyTypes.keySet().stream().filter(key -> !"_label".equals(key)).map(
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

    private void writeProcedures(final BufferedWriter writer) throws IOException {
        final Set<Class<?>> enumTypes = new HashSet<>();
        final Registry.ProcedureDefinition[] definitions = Registry.getInstance().getProcedures();
        final Map<String, ProceduresPath> procedurePathHierarchy = new HashMap<>();
        procedurePathHierarchy.put("", new ProceduresPath(""));
        for (final Registry.ProcedureDefinition definition : definitions) {
            final String[] parts = StringUtils.split(definition.name, '.');
            final StringBuilder parentPath = new StringBuilder();
            for (int i = 0; i < parts.length - 1; i++) {
                if (!procedurePathHierarchy.get(parentPath.toString()).childPaths.contains(parts[i]))
                    procedurePathHierarchy.get(parentPath.toString()).childPaths.add(parts[i]);
                if (i > 0)
                    parentPath.append('.');
                parentPath.append(parts[i]);
                if (!procedurePathHierarchy.containsKey(parentPath.toString())) {
                    final ProceduresPath parent = new ProceduresPath(parentPath.toString());
                    procedurePathHierarchy.put(parentPath.toString(), parent);
                }
            }
            final ProceduresPath parent = procedurePathHierarchy.get(parentPath.toString());
            if (!parent.procedurePaths.contains(parts[parts.length - 1]))
                parent.procedurePaths.add(parts[parts.length - 1]);
        }
        for (final ProceduresPath path : procedurePathHierarchy.values()) {
            if ("".equals(path.path))
                writeLine(writer, "type Procedures implements ProcedureContainer {");
            else
                writeLine(writer, "type " + path.typeName + " implements ProcedureContainer {");
            writeLine(writer, "  _id: ID!");
            for (int i = 0; i < path.childPaths.size(); i++) {
                final String childTypeName = procedurePathHierarchy.get(path.getFullChildPath(i)).typeName;
                writeLine(writer, "  _" + path.childPaths.get(i) + ": " + childTypeName + "!");
            }
            for (int i = 0; i < path.procedurePaths.size(); i++) {
                final String fullProcedurePath = path.getFullProcedurePath(i);
                final Registry.ProcedureDefinition definition = Registry.getInstance().getProcedure(fullProcedurePath);
                final StringBuilder arguments = new StringBuilder();
                arguments.append('(');
                arguments.append("graphViewId: ID");
                for (int j = 0; j < definition.argumentNames.length; j++) {
                    arguments.append(", ").append(definition.argumentNames[j]).append(": ");
                    switch (definition.argumentSimpleTypes[j]) {
                        case Bool:
                            arguments.append("Boolean");
                            break;
                        case Node:
                        case Edge:
                            arguments.append("ID");
                            break;
                        case String:
                            arguments.append("String");
                            break;
                        case Int:
                            arguments.append("Int");
                            break;
                        case Float:
                            arguments.append("Float");
                            break;
                        case Enum:
                            arguments.append(definition.argumentTypes[j].getSimpleName());
                            enumTypes.add(definition.argumentTypes[j]);
                            break;
                        default:
                            arguments.append("JSON");
                            break;
                    }
                    arguments.append("!");
                }
                arguments.append(')');
                writeLine(writer, "  " + path.procedurePaths.get(i) + arguments + ": JSON! @Procedure(path: \"" +
                                  fullProcedurePath + "\")");
            }
            writeLine(writer, "}");
        }
        writer.newLine();
        for (final Class<?> enumType : enumTypes) {
            writeLine(writer, "enum " + enumType.getSimpleName() + " {");
            for (final Object constant : enumType.getEnumConstants()) {
                writeLine(writer, "  " + constant);
            }
            writeLine(writer, "}");
            writer.newLine();
        }
    }

    private void writeNodeType(final BufferedWriter writer, final GraphSchema schema,
                               final GraphSchema.NodeType type) throws IOException {
        final String directive = type.label.equals(type.fixedLabel()) ? "" :
                                 " @GraphLabel(value: \"" + type.label + "\")";
        writeLine(writer, "type " + type.fixedLabel() + " implements Node" + directive + " {");
        writeTypeProperties(writer, type);
        for (final GraphSchema.EdgeType edgeType : schema.getEdgeTypes())
            if (edgeType.fromLabels.contains(type.label)) {
                final String arguments = buildArgumentsString(type.propertyKeyTypes);
                writeLine(writer,
                          "  " + edgeType.fixedLabel() + '(' + arguments + "): [" + edgeType.fixedLabel() + "!]!");
            }
        writeLine(writer, "  _edges(_label: String): [Edge!]!");
        writeLine(writer, "  _edgesIn(_label: String): [Edge!]!");
        writeLine(writer, "  _edgesOut(_label: String): [Edge!]!");
        writeLine(writer, "}");
    }

    private void writeTypeProperties(final BufferedWriter writer, final GraphSchema.BaseType type) throws IOException {
        for (final String key : type.propertyKeyTypes.keySet())
            writeLine(writer, "  " + mapPropertyToKeyTypeDefinition(key, type.propertyKeyTypes.get(key)));
    }

    private void writeEdgeType(final BufferedWriter writer, final GraphSchema.EdgeType type) throws IOException {
        final String directive = type.label.equals(type.fixedLabel()) ? "" :
                                 " @GraphLabel(value: \"" + type.label + "\")";
        writeLine(writer, "type " + type.fixedLabel() + " implements Edge" + directive + " {");
        writeTypeProperties(writer, type);
        writeLine(writer, "  _from: Node!");
        writeLine(writer, "  _to: Node!");
        writeLine(writer, "}");
    }

    private static class ProceduresPath {
        public final String path;
        public final String typeName;
        public final List<String> childPaths = new ArrayList<>();
        public final List<String> procedurePaths = new ArrayList<>();

        ProceduresPath(final String path) {
            this.path = path;
            final StringBuilder typeName = new StringBuilder();
            for (final String part : StringUtils.split(path, '.'))
                typeName.append(TextUtils.toUpperCamelCase(part));
            this.typeName = typeName.toString();
        }

        public String getFullChildPath(final int i) {
            return (path.length() > 0 ? path + '.' : "") + childPaths.get(i);
        }

        public String getFullProcedurePath(final int i) {
            return (path.length() > 0 ? path + '.' : "") + procedurePaths.get(i);
        }
    }
}
