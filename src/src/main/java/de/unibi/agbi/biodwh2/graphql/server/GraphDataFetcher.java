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
        final Map<String, Comparable<?>> variables = new HashMap<>();
        for (final VariableDefinition definition : environment.getOperationDefinition().getVariableDefinitions()) {
            Comparable<?> value;
            if (environment.getVariables().containsKey(definition.getName()))
                value = (Comparable<?>) environment.getVariables().get(definition.getName());
            else
                value = convertGraphQLValue(null, definition.getDefaultValue(), variables);
            variables.put(definition.getName(), value);
        }
        return getObject(schema, implementingType, field.getArguments(), field.getSelectionSet(), null, null,
                         variables);
    }

    private Object getObject(final GraphQLSchema schema, final GraphQLImplementingType type,
                             final List<Argument> arguments, final SelectionSet selectionSet, final String filterKey,
                             final Comparable<?> filterValue, final Map<String, Comparable<?>> variables) {
        final Map<String, Comparable<?>> argumentsMap = convertArgumentsForGraph(arguments, variables);
        if (filterKey != null && filterValue != null)
            argumentsMap.put(filterKey, filterValue);
        if (typeHasInterface(type, "Node")) {
            final List<Object> result = new ArrayList<>();
            for (final Node node : graph.findNodes(getTypeNameOrGraphLabel(type), argumentsMap))
                result.add(selectResults(schema, selectionSet, node, variables));
            return result;
        } else if (typeHasInterface(type, "Edge")) {
            final List<Object> result = new ArrayList<>();
            for (final Edge edge : graph.findEdges(getTypeNameOrGraphLabel(type), argumentsMap))
                result.add(selectResults(schema, selectionSet, edge, variables));
            return result;
        } else if (type instanceof GraphQLInterfaceType) {
            if ("Node".equals(type.getName())) {
                if (argumentsMap.size() == 1 && argumentsMap.containsKey(Node.ID_FIELD)) {
                    final long id = getLongProperty(argumentsMap, Node.ID_FIELD);
                    return selectResults(schema, selectionSet, graph.getNode(id), variables);
                }
                final List<Object> result = new ArrayList<>();
                for (final Node node : graph.findNodes(argumentsMap))
                    result.add(selectResults(schema, selectionSet, node, variables));
                return result;
            } else if ("Edge".equals(type.getName())) {
                if (argumentsMap.size() == 1 && argumentsMap.containsKey(Edge.ID_FIELD)) {
                    final long id = getLongProperty(argumentsMap, Edge.ID_FIELD);
                    return selectResults(schema, selectionSet, graph.getEdge(id), variables);
                }
                final List<Object> result = new ArrayList<>();
                for (final Edge edge : graph.findEdges(argumentsMap))
                    result.add(selectResults(schema, selectionSet, edge, variables));
                return result;
            }
        }
        return null;
    }

    private Map<String, Comparable<?>> convertArgumentsForGraph(final List<Argument> arguments,
                                                                final Map<String, Comparable<?>> variables) {
        final Map<String, Comparable<?>> result = new HashMap<>();
        for (final Argument argument : arguments) {
            final String key = translatePropertyKey(argument.getName());
            result.put(key, convertGraphQLValue(key, argument.getValue(), variables));
        }
        return result;
    }

    private Comparable<?> convertGraphQLValue(final String key, final Value<?> value,
                                              final Map<String, Comparable<?>> variables) {
        if (value == null)
            return null;
        if (value instanceof StringValue)
            return ((StringValue) value).getValue();
        if (value instanceof BooleanValue)
            return ((BooleanValue) value).isValue();
        if (value instanceof FloatValue)
            return ((FloatValue) value).getValue();
        if (value instanceof IntValue) {
            final BigInteger integer = ((IntValue) value).getValue();
            if (integer.bitCount() > Integer.SIZE || "__id".equals(key) || "__to_id".equals(key) || "__from_id".equals(
                    key)) {
                return integer.longValue();
            }
            return integer.intValue();
        }
        if (value instanceof VariableReference)
            return variables.get(((VariableReference) value).getName());
        if (LOGGER.isErrorEnabled())
            LOGGER.error("Failed to convert value '" + value + "' to graph argument");
        return null;
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
            final GraphQLAppliedDirective directive = objectType.getAppliedDirective("GraphLabel");
            if (directive != null)
                return directive.getArgument("value").getValue();
        }
        return type.getName();
    }

    private Map<String, Object> selectResults(final GraphQLSchema schema, final SelectionSet selectionSet,
                                              final MVStoreModel model, final Map<String, Comparable<?>> variables) {
        if (model == null)
            return null;
        final Map<String, Object> result = new HashMap<>();
        result.put("__typename", getFixedLabel(model));
        for (final Selection<?> selection : selectionSet.getSelections())
            selectResult(schema, selection, model, result, variables);
        return result;
    }

    private String getFixedLabel(final MVStoreModel model) {
        if (model instanceof Node)
            return GraphSchema.BaseType.fixLabel(((Node) model).getLabel());
        if (model instanceof Edge)
            return GraphSchema.BaseType.fixLabel(((Edge) model).getLabel());
        return model.getClass().getName();
    }

    private void selectResult(final GraphQLSchema schema, final Selection<?> selection, final MVStoreModel model,
                              final Map<String, Object> result, final Map<String, Comparable<?>> variables) {
        if (selection instanceof Field)
            selectFieldResult(schema, (Field) selection, model, result, variables);
        else if (selection instanceof InlineFragment)
            selectInlineFragmentResult(schema, (InlineFragment) selection, model, result, variables);
        else if (LOGGER.isErrorEnabled())
            LOGGER.error("Failed to select results for selection '" + selection + "'");
    }

    private void selectFieldResult(final GraphQLSchema schema, final Field field, final MVStoreModel model,
                                   final Map<String, Object> result, final Map<String, Comparable<?>> variables) {
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
                                         Edge.FROM_ID_FIELD, model.getId(), variables));
                } else if (model instanceof Edge) {
                    final Edge edge = (Edge) model;
                    final long targetId = "_to".equals(field.getName()) ? edge.getToId() : edge.getFromId();
                    result.put(field.getResultKey(),
                               getObject(schema, implementingType, field.getArguments(), field.getSelectionSet(),
                                         Node.ID_FIELD, targetId, variables));
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
                                            final MVStoreModel model, final Map<String, Object> result,
                                            final Map<String, Comparable<?>> variables) {
        if (fragment.getTypeCondition().getName().equals(getFixedLabel(model))) {
            final Map<String, Object> fragmentResults = selectResults(schema, fragment.getSelectionSet(), model,
                                                                      variables);
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
