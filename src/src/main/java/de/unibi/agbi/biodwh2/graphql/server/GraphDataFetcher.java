package de.unibi.agbi.biodwh2.graphql.server;

import de.unibi.agbi.biodwh2.core.io.mvstore.MVStoreModel;
import de.unibi.agbi.biodwh2.core.model.graph.Edge;
import de.unibi.agbi.biodwh2.core.model.graph.Graph;
import de.unibi.agbi.biodwh2.core.model.graph.Node;
import graphql.language.*;
import graphql.schema.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class GraphDataFetcher implements DataFetcher<Object> {
    private static final Logger LOGGER = LoggerFactory.getLogger(GraphDataFetcher.class);

    private final Graph graph;

    public GraphDataFetcher(final Graph graph) {
        this.graph = graph;
    }

    @Override
    public Object get(final DataFetchingEnvironment environment) {
        if (environment.getSource() != null) {
            final Map<String, Object> properties = environment.getSource();
            return properties.get(environment.getFieldDefinition().getName());
        }
        return getObject(environment.getGraphQLSchema(), environment.getMergedField().getSingleField());
    }

    private Object getObject(final GraphQLSchema schema, final Field field) {
        return getObject(schema, schema.getObjectType(field.getName()), field.getArguments(), field.getSelectionSet(),
                         null, null);
    }

    private Object getObject(final GraphQLSchema schema, final GraphQLImplementingType type,
                             final List<Argument> arguments, final SelectionSet selectionSet, final String filterKey,
                             final Comparable<?> filterValue) {
        final Map<String, Comparable<?>> argumentsMap = convertArgumentsForGraph(arguments);
        if (filterKey != null && filterValue != null)
            argumentsMap.put(filterKey, filterValue);
        if (typeHasInterface(type, "Node")) {
            final List<Object> result = new ArrayList<>();
            for (final Node node : graph.findNodes(type.getName(), argumentsMap))
                result.add(selectResults(schema, selectionSet, node));
            return result;
        } else if (typeHasInterface(type, "Edge")) {
            final List<Object> result = new ArrayList<>();
            for (final Edge edge : graph.findEdges(type.getName(), argumentsMap))
                result.add(selectResults(schema, selectionSet, edge));
            return result;
        } else if (type instanceof GraphQLInterfaceType) {
            if ("Node".equals(type.getName())) {
                if (argumentsMap.size() == 1 && argumentsMap.containsKey(Node.ID_FIELD))
                    return selectResults(schema, selectionSet, graph.getNode((Long) argumentsMap.get(Node.ID_FIELD)));
                final List<Object> result = new ArrayList<>();
                for (final Node node : graph.findNodes(argumentsMap))
                    result.add(selectResults(schema, selectionSet, node));
                return result;
            } else if ("Edge".equals(type.getName())) {
                if (argumentsMap.size() == 1 && argumentsMap.containsKey(Edge.ID_FIELD))
                    return selectResults(schema, selectionSet, graph.getEdge((Long) argumentsMap.get(Edge.ID_FIELD)));
                final List<Object> result = new ArrayList<>();
                for (final Edge edge : graph.findEdges(argumentsMap))
                    result.add(selectResults(schema, selectionSet, edge));
                return result;
            }
        }
        return null;
    }

    private Map<String, Comparable<?>> convertArgumentsForGraph(final List<Argument> arguments) {
        final Map<String, Comparable<?>> result = new HashMap<>();
        for (final Argument argument : arguments) {
            final String key = translatePropertyKey(argument.getName());
            final Value<?> value = argument.getValue();
            if (value instanceof StringValue)
                result.put(key, ((StringValue) value).getValue());
            else if (value instanceof BooleanValue)
                result.put(key, ((BooleanValue) value).isValue());
            else if (value instanceof FloatValue)
                result.put(key, ((FloatValue) value).getValue());
            else if (value instanceof IntValue)
                result.put(key, ((IntValue) value).getValue());
            else if (LOGGER.isErrorEnabled())
                LOGGER.error("Failed to convert argument '" + argument + "' to graph argument");
        }
        return result;
    }

    private String translatePropertyKey(final String key) {
        if ("_id".equals(key) || "_to_id".equals(key) || "_from_id".equals(key) || "_label".equals(key) ||
            "_mapped".equals(key))
            return '_' + key;
        return key;
    }

    private boolean typeHasInterface(final GraphQLImplementingType type, final String interfaceName) {
        return type.getInterfaces().stream().anyMatch(i -> interfaceName.equals(i.getName()));
    }

    private Map<String, Object> selectResults(final GraphQLSchema schema, final SelectionSet selectionSet,
                                              final MVStoreModel model) {
        final Map<String, Object> result = new HashMap<>();
        result.put("__typename", model.get(Node.LABEL_FIELD));
        final GraphQLObjectType type = schema.getObjectType(model.getProperty(Node.LABEL_FIELD));
        for (final Selection<?> selection : selectionSet.getSelections()) {
            if (selection instanceof Field) {
                final Field selectionField = (Field) selection;
                if ("__typename".equals(selectionField.getName()))
                    result.put(selectionField.getResultKey(), model.get(Node.LABEL_FIELD));
                else {
                    final GraphQLFieldDefinition definition = type.getFieldDefinition(selectionField.getName());
                    GraphQLType fieldType = definition.getType();
                    while (fieldType instanceof GraphQLModifiedType)
                        fieldType = ((GraphQLModifiedType) fieldType).getWrappedType();
                    if (fieldType instanceof GraphQLScalarType)
                        result.put(selectionField.getResultKey(),
                                   model.getProperty(translatePropertyKey(selectionField.getName())));
                    else if (model instanceof Node) {
                        result.put(selectionField.getResultKey(),
                                   getObject(schema, (GraphQLImplementingType) fieldType, selectionField.getArguments(),
                                             selectionField.getSelectionSet(), Edge.FROM_ID_FIELD, model.getId()));
                    } else if (model instanceof Edge) {
                        result.put(selectionField.getResultKey(),
                                   getObject(schema, (GraphQLImplementingType) fieldType, selectionField.getArguments(),
                                             selectionField.getSelectionSet(), Node.ID_FIELD,
                                             ((Edge) model).getToId()));
                    }
                }
            } else if (selection instanceof InlineFragment) {
                final InlineFragment fragment = (InlineFragment) selection;
                if (fragment.getTypeCondition().getName().equals(model.getProperty(Node.LABEL_FIELD))) {
                    final Map<String, Object> fragmentResults = selectResults(schema, fragment.getSelectionSet(),
                                                                              model);
                    for (final String key : fragmentResults.keySet())
                        result.put(key, fragmentResults.get(key));
                }
            } else if (LOGGER.isErrorEnabled())
                LOGGER.error("Failed to select results for selection '" + selection + "'");
        }
        return result;
    }
}
