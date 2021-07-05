package de.unibi.agbi.biodwh2.graphql.server;

import de.unibi.agbi.biodwh2.core.io.mvstore.MVStoreModel;
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
        return getNode(environment);
    }

    private Object getNode(final DataFetchingEnvironment environment) {
        return getNode(environment.getGraphQLSchema(), environment.getMergedField().getSingleField());
    }

    private Object getNode(final GraphQLSchema schema, final Field field) {
        final String label = field.getName();
        final GraphQLObjectType type = (GraphQLObjectType) schema.getType(label);
        if (type.getInterfaces().stream().anyMatch(i -> i.getName().equals("Node"))) {
            final Map<String, Comparable<?>> arguments = convertArgumentsForGraph(field.getArguments());
            final List<Object> result = new ArrayList<>();
            for (final Node node : graph.findNodes(type.getName(), arguments))
                result.add(selectResults(schema, field.getSelectionSet(), node));
            return result;
        }
        return null;
    }

    private List<Object> getEdges(final GraphQLSchema schema, final Node fromNode, final Field field) {
        final List<Object> result = new ArrayList<>();
        final Map<String, Comparable<?>> arguments = convertArgumentsForGraph(field.getArguments());
        arguments.put(Edge.FROM_ID_FIELD, fromNode.getId());
        for (final Edge edge : graph.findEdges(field.getName(), arguments)) {
            final Node toNode = graph.getNode(edge.getToId());
            result.add(selectResults(schema, field.getSelectionSet(), toNode));
        }
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

    private Map<String, Object> selectResults(final GraphQLSchema schema, final SelectionSet selectionSet,
                                              final MVStoreModel model) {
        final Map<String, Object> result = new HashMap<>();
        final GraphQLObjectType type = schema.getObjectType(model.getProperty(Node.LABEL_FIELD));
        for (final Selection<?> selection : selectionSet.getSelections()) {
            if (selection instanceof Field) {
                final Field selectionField = (Field) selection;
                if ("__typename".equals(selectionField.getName()))
                    result.put(selectionField.getResultKey(), model.get(Node.LABEL_FIELD));
                else {
                    final GraphQLFieldDefinition definition = type.getFieldDefinition(selectionField.getName());
                    if (definition.getType() instanceof GraphQLScalarType)
                        result.put(selectionField.getResultKey(), model.getProperty(selectionField.getName()));
                    else if (definition.getType() instanceof GraphQLList)
                        result.put(selectionField.getResultKey(), model.getProperty(selectionField.getName()));
                    else
                        result.put(selectionField.getResultKey(), getEdges(schema, (Node) model, selectionField));
                }
            } else {
                // TODO: error
            }
        }
        return result;
    }
}
