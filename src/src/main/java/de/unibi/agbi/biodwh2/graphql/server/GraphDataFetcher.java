package de.unibi.agbi.biodwh2.graphql.server;

import de.unibi.agbi.biodwh2.core.model.graph.Edge;
import de.unibi.agbi.biodwh2.core.model.graph.Graph;
import de.unibi.agbi.biodwh2.core.model.graph.Node;
import graphql.language.*;
import graphql.schema.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class GraphDataFetcher implements DataFetcher<Object> {
    private final Graph graph;

    public GraphDataFetcher(final Graph graph) {
        this.graph = graph;
    }

    @Override
    public Object get(final DataFetchingEnvironment environment) {
        if (environment.getSource() != null) {
            final Map<String, Object> node = environment.getSource();
            return node.get(environment.getFieldDefinition().getName());
        }
        final String label = environment.getMergedField().getName();
        final GraphQLType type = environment.getGraphQLSchema().getType(label);
        if (type instanceof GraphQLObjectType)
            return getObject(environment, (GraphQLObjectType) type);
        return null;
    }

    private Object getObject(final DataFetchingEnvironment environment, final GraphQLObjectType type) {
        if (type.getInterfaces().stream().anyMatch(i -> i.getName().equals("Node"))) {
            final Field field = environment.getMergedField().getSingleField();
            final Map<String, Comparable<?>> arguments = convertArgumentsForGraph(environment.getArguments());
            final List<Object> result = new ArrayList<>();
            for (final Node node : graph.findNodes(type.getName(), arguments)) {
                final Map<String, Object> nodeResult = new HashMap<>();
                for (final Selection<?> selection : field.getSelectionSet().getSelections()) {
                    if (selection instanceof Field) {
                        final Field selectionField = (Field) selection;
                        final GraphQLFieldDefinition definition = type.getFieldDefinition(selectionField.getName());
                        if (definition.getType() instanceof GraphQLScalarType)
                            nodeResult.put(selectionField.getResultKey(), node.getProperty(selectionField.getName()));
                        else if (definition.getType() instanceof GraphQLList)
                            nodeResult.put(selectionField.getResultKey(), node.getProperty(selectionField.getName()));
                        else
                            nodeResult.put(selectionField.getResultKey(), getEdges(node, selectionField));
                    } else {
                        // TODO: error
                    }
                }
                result.add(nodeResult);
            }
            return result;
        }
        /*
        if (type.getInterfaces().stream().anyMatch(i -> i.getName().equals("Edge"))) {
            final List<Edge> result = new ArrayList<>();
            final String edgeLabel = StringUtils.splitByWholeSeparator(type.getName(), "__")[1];
            graph.findEdges(edgeLabel, arguments).forEach(result::add);
            return result;
        }
        */
        return null;
    }

    private List<Object> getEdges(final Node fromNode, final Field field) {
        final List<Object> result = new ArrayList<>();
        final Map<String, Comparable<?>> arguments = convertArgumentsForGraph(field.getArguments());
        arguments.put(Edge.FROM_ID_FIELD, fromNode.getId());
        for (final Edge edge : graph.findEdges(field.getName(), arguments)) {
            final Map<String, Object> edgeResult = new HashMap<>();
            for (final Selection<?> selection : field.getSelectionSet().getSelections()) {
                if (selection instanceof Field) {
                    final Field selectionField = (Field) selection;
                    final Object value = edge.get(selectionField.getName());
                    if (value != null)
                        edgeResult.put(selectionField.getResultKey(), value);
                } else {
                    // TODO: error
                }
            }
            result.add(edgeResult);
        }
        return result;
    }

    private Map<String, Comparable<?>> convertArgumentsForGraph(final Map<String, Object> arguments) {
        final Map<String, Comparable<?>> result = new HashMap<>();
        for (final String key : arguments.keySet())
            if ("_id".equals(key) || "_label".equals(key) || "_mapped".equals(key))
                result.put('_' + key, (Comparable<?>) arguments.get(key));
            else
                result.put(key, (Comparable<?>) arguments.get(key));
        return result;
    }

    private Map<String, Comparable<?>> convertArgumentsForGraph(final List<Argument> arguments) {
        final Map<String, Comparable<?>> result = new HashMap<>();
        for (final Argument argument : arguments) {
            String key = argument.getName();
            if ("_id".equals(key) || "_label".equals(key) || "_mapped".equals(key))
                key = '_' + key;
            final Value<?> value = argument.getValue();
            if (value instanceof StringValue)
                result.put(key, ((StringValue) value).getValue());
            else if (value instanceof BooleanValue)
                result.put(key, ((BooleanValue) value).isValue());
            else if (value instanceof FloatValue)
                result.put(key, ((FloatValue) value).getValue());
            else if (value instanceof IntValue)
                result.put(key, ((IntValue) value).getValue());
            else {
                // TODO
            }
        }
        return result;
    }
}
