package de.unibi.agbi.biodwh2.graphql.server;

import de.unibi.agbi.biodwh2.core.io.mvstore.MVStoreModel;
import de.unibi.agbi.biodwh2.core.model.graph.Edge;
import de.unibi.agbi.biodwh2.core.model.graph.Graph;
import de.unibi.agbi.biodwh2.core.model.graph.Node;
import de.unibi.agbi.biodwh2.graphql.schema.GraphSchema;
import graphql.language.*;
import graphql.schema.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigInteger;
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
        return getObject(environment);
    }

    private Object getObject(final DataFetchingEnvironment environment) {
        final GraphQLSchema schema = environment.getGraphQLSchema();
        final Field field = environment.getMergedField().getSingleField();
        final GraphQLImplementingType implementingType = (GraphQLImplementingType) unwrapType(
                environment.getFieldType());
        return getObject(schema, implementingType, field.getArguments(), field.getSelectionSet(), null, null);
    }

    private Object getObject(final GraphQLSchema schema, final GraphQLImplementingType type,
                             final List<Argument> arguments, final SelectionSet selectionSet, final String filterKey,
                             final Comparable<?> filterValue) {
        final Map<String, Comparable<?>> argumentsMap = convertArgumentsForGraph(arguments);
        if (filterKey != null && filterValue != null)
            argumentsMap.put(filterKey, filterValue);
        if (typeHasInterface(type, "Node")) {
            final List<Object> result = new ArrayList<>();
            for (final Node node : graph.findNodes(getTypeNameOrGraphLabel(type), argumentsMap))
                result.add(selectResults(schema, selectionSet, node));
            return result;
        } else if (typeHasInterface(type, "Edge")) {
            final List<Object> result = new ArrayList<>();
            for (final Edge edge : graph.findEdges(getTypeNameOrGraphLabel(type), argumentsMap))
                result.add(selectResults(schema, selectionSet, edge));
            return result;
        } else if (type instanceof GraphQLInterfaceType) {
            if ("Node".equals(type.getName())) {
                if (argumentsMap.size() == 1 && argumentsMap.containsKey(Node.ID_FIELD)) {
                    final long id = getLongProperty(argumentsMap, Node.ID_FIELD);
                    return selectResults(schema, selectionSet, graph.getNode(id));
                }
                final List<Object> result = new ArrayList<>();
                for (final Node node : graph.findNodes(argumentsMap))
                    result.add(selectResults(schema, selectionSet, node));
                return result;
            } else if ("Edge".equals(type.getName())) {
                if (argumentsMap.size() == 1 && argumentsMap.containsKey(Edge.ID_FIELD)) {
                    final long id = getLongProperty(argumentsMap, Edge.ID_FIELD);
                    return selectResults(schema, selectionSet, graph.getEdge(id));
                }
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
            else if (value instanceof IntValue) {
                final BigInteger integer = ((IntValue) value).getValue();
                if (integer.bitCount() > Integer.SIZE || "__id".equals(key) || "__to_id".equals(key) ||
                    "__from_id".equals(key))
                    result.put(key, integer.longValue());
                else
                    result.put(key, integer.intValue());
            } else if (LOGGER.isErrorEnabled())
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

    private String getTypeNameOrGraphLabel(final GraphQLImplementingType type) {
        if (type instanceof GraphQLObjectType) {
            final GraphQLObjectType objectType = (GraphQLObjectType) type;
            final GraphQLDirective directive = objectType.getDirective("GraphLabel");
            if (directive != null)
                return directive.getArgument("value").getArgumentValue().getValue().toString();
        }
        return type.getName();
    }

    private Map<String, Object> selectResults(final GraphQLSchema schema, final SelectionSet selectionSet,
                                              final MVStoreModel model) {
        if (model == null)
            return null;
        final Map<String, Object> result = new HashMap<>();
        result.put("__typename", getFixedLabel(model));
        for (final Selection<?> selection : selectionSet.getSelections())
            selectResult(schema, selection, model, result);
        return result;
    }

    private String getFixedLabel(final MVStoreModel model) {
        return GraphSchema.BaseType.fixLabel(model.getProperty(Node.LABEL_FIELD));
    }

    private void selectResult(final GraphQLSchema schema, final Selection<?> selection, final MVStoreModel model,
                              final Map<String, Object> result) {
        if (selection instanceof Field)
            selectFieldResult(schema, (Field) selection, model, result);
        else if (selection instanceof InlineFragment)
            selectInlineFragmentResult(schema, (InlineFragment) selection, model, result);
        else if (LOGGER.isErrorEnabled())
            LOGGER.error("Failed to select results for selection '" + selection + "'");
    }

    private void selectFieldResult(final GraphQLSchema schema, final Field field, final MVStoreModel model,
                                   final Map<String, Object> result) {
        if ("__typename".equals(field.getName()))
            result.put(field.getResultKey(), getFixedLabel(model));
        else {
            final GraphQLObjectType type = schema.getObjectType(getFixedLabel(model));
            final GraphQLFieldDefinition definition = type.getFieldDefinition(field.getName());
            final GraphQLType fieldType = unwrapType(definition.getType());
            if (fieldType instanceof GraphQLScalarType)
                result.put(field.getResultKey(), model.getProperty(translatePropertyKey(field.getName())));
            else if (fieldType instanceof GraphQLImplementingType) {
                final GraphQLImplementingType implementingType = (GraphQLImplementingType) fieldType;
                if (model instanceof Node) {
                    result.put(field.getResultKey(),
                               getObject(schema, implementingType, field.getArguments(), field.getSelectionSet(),
                                         Edge.FROM_ID_FIELD, model.getId()));
                } else if (model instanceof Edge) {
                    final Edge edge = (Edge) model;
                    final long targetId = "_to".equals(field.getName()) ? edge.getToId() : edge.getFromId();
                    result.put(field.getResultKey(),
                               getObject(schema, implementingType, field.getArguments(), field.getSelectionSet(),
                                         Node.ID_FIELD, targetId));
                }
            }
        }
    }

    private GraphQLType unwrapType(GraphQLType fieldType) {
        while (fieldType instanceof GraphQLModifiedType)
            fieldType = ((GraphQLModifiedType) fieldType).getWrappedType();
        return fieldType;
    }

    private void selectInlineFragmentResult(final GraphQLSchema schema, final InlineFragment fragment,
                                            final MVStoreModel model, final Map<String, Object> result) {
        if (fragment.getTypeCondition().getName().equals(getFixedLabel(model))) {
            final Map<String, Object> fragmentResults = selectResults(schema, fragment.getSelectionSet(), model);
            for (final String key : fragmentResults.keySet())
                result.put(key, fragmentResults.get(key));
        }
    }

    private long getLongProperty(final Map<String, Comparable<?>> properties, final String key) {
        final Object object = properties.get(key);
        if (object instanceof BigInteger)
            return ((BigInteger) object).longValue();
        return (long) object;
    }
}
