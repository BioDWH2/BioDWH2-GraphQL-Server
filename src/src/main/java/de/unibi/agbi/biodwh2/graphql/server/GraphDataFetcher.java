package de.unibi.agbi.biodwh2.graphql.server;

import de.unibi.agbi.biodwh2.core.io.mvstore.MVStoreModel;
import de.unibi.agbi.biodwh2.core.model.graph.Node;
import de.unibi.agbi.biodwh2.core.model.graph.*;
import de.unibi.agbi.biodwh2.graphql.schema.GraphSchema;
import de.unibi.agbi.biodwh2.graphql.server.model.GraphViewIds;
import de.unibi.agbi.biodwh2.procedures.Registry;
import de.unibi.agbi.biodwh2.procedures.ResultSet;
import graphql.language.*;
import graphql.schema.*;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigInteger;
import java.util.*;
import java.util.stream.Collectors;

final class GraphDataFetcher implements DataFetcher<Object> {
    private static final Logger LOGGER = LoggerFactory.getLogger(GraphDataFetcher.class);

    private final Graph graph;
    private static final Map<String, BaseGraph> views = new HashMap<>();

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
        final Map<String, Object> variables = new HashMap<>();
        for (final VariableDefinition definition : environment.getOperationDefinition().getVariableDefinitions()) {
            Object value;
            if (environment.getVariables().containsKey(definition.getName()))
                value = environment.getVariables().get(definition.getName());
            else
                value = convertGraphQLValue(null, definition.getDefaultValue(), variables);
            variables.put(definition.getName(), value);
        }
        if (environment.getOperationDefinition().getOperation() == OperationDefinition.Operation.MUTATION)
            return executeMutation(field, variables);
        final GraphQLImplementingType implementingType = (GraphQLImplementingType) unwrapType(
                environment.getFieldType());
        return getObject(schema, implementingType, field, null, null, variables);
    }

    private Object executeMutation(final Field field, final Map<String, Object> variables) {
        final Map<String, Object> argumentsMap = convertArguments(field.getArguments(), variables);
        if ("createGraphView".equals(field.getName())) {
            final String[] nodeLabels = Arrays.stream(((Object[]) argumentsMap.get("nodeLabels"))).map(
                    (x) -> (String) x).toArray(String[]::new);
            final String[] edgeLabels = Arrays.stream(((Object[]) argumentsMap.get("edgeLabels"))).map(
                    (x) -> (String) x).toArray(String[]::new);
            final GraphView view = new GraphView(graph, nodeLabels, edgeLabels);
            final String name = ((String) argumentsMap.get("name")) + '-' + java.util.UUID.randomUUID();
            views.put(name, view);
            return name;
        } else if ("createGraphViewIds".equals(field.getName())) {
            final Long[] nodeIds = Arrays.stream(((Object[]) argumentsMap.get("nodeIds"))).map((x) -> (Long) x).toArray(
                    Long[]::new);
            final Long[] edgeIds = Arrays.stream(((Object[]) argumentsMap.get("edgeIds"))).map((x) -> (Long) x).toArray(
                    Long[]::new);
            final GraphViewIds view = new GraphViewIds(graph, nodeIds, edgeIds);
            final String name = ((String) argumentsMap.get("name")) + '-' + java.util.UUID.randomUUID();
            views.put(name, view);
            return name;
        } else if ("deleteGraphView".equals(field.getName())) {
            final String id = (String) argumentsMap.get("id");
            views.remove(id);
            return id;
        }
        return null;
    }

    private Object getObject(final GraphQLSchema schema, final GraphQLImplementingType type, final Field field,
                             final String filterKey, final Comparable<?> filterValue,
                             final Map<String, Object> variables) {
        final List<Directive> directives = field.getDirectives();
        final SelectionSet selectionSet = field.getSelectionSet();
        final Map<String, Comparable<?>> argumentsMap = convertArgumentsForGraph(
                convertArguments(field.getArguments(), variables));
        if (filterKey != null && filterValue != null)
            argumentsMap.put(filterKey, filterValue);
        if (typeHasInterface(type, "Node")) {
            final List<Object> result = new ArrayList<>();
            for (final Node node : graph.findNodes(getTypeNameOrGraphLabel(type), argumentsMap))
                result.add(selectResults(schema, selectionSet, node, variables));
            return applyLimitDirective(variables, directives, result);
        } else if (typeHasInterface(type, "Edge")) {
            final List<Object> result = new ArrayList<>();
            for (final Edge edge : graph.findEdges(getTypeNameOrGraphLabel(type), argumentsMap))
                result.add(selectResults(schema, selectionSet, edge, variables));
            return applyLimitDirective(variables, directives, result);
        } else if (type instanceof GraphQLInterfaceType) {
            if ("Node".equals(type.getName())) {
                if (argumentsMap.size() == 1 && argumentsMap.containsKey(Node.ID_FIELD)) {
                    final long id = getLongProperty(argumentsMap, Node.ID_FIELD);
                    return selectResults(schema, selectionSet, graph.getNode(id), variables);
                }
                final List<Object> result = new ArrayList<>();
                for (final Node node : graph.findNodes(argumentsMap))
                    result.add(selectResults(schema, selectionSet, node, variables));
                return applyLimitDirective(variables, directives, result);
            } else if ("Edge".equals(type.getName())) {
                if (argumentsMap.size() == 1 && argumentsMap.containsKey(Edge.ID_FIELD)) {
                    final long id = getLongProperty(argumentsMap, Edge.ID_FIELD);
                    return selectResults(schema, selectionSet, graph.getEdge(id), variables);
                }
                final List<Object> result = new ArrayList<>();
                for (final Edge edge : graph.findEdges(argumentsMap))
                    result.add(selectResults(schema, selectionSet, edge, variables));
                return applyLimitDirective(variables, directives, result);
            }
        } else if (typeHasInterface(type, "ProcedureContainer")) {
            return selectProcedureResults(schema, selectionSet, type, variables);
        }
        return null;
    }

    private List<Object> applyLimitDirective(final Map<String, Object> variables, final List<Directive> directives,
                                             List<Object> values) {
        for (final Directive directive : directives) {
            if ("Limit".equals(directive.getName())) {
                final Argument skipArg = directive.getArgument("skip");
                final Argument countArg = directive.getArgument("count");
                final int skip = skipArg != null ? (Integer) convertGraphQLValue("skip", skipArg.getValue(),
                                                                                 variables) : 0;
                final int count = countArg != null ? (Integer) convertGraphQLValue("count", countArg.getValue(),
                                                                                   variables) : values.size();
                return values.stream().skip(skip).limit(count).collect(Collectors.toList());
            }
        }
        return values;
    }

    private Map<String, Object> convertArguments(final List<Argument> arguments, final Map<String, Object> variables) {
        final Map<String, Object> result = new HashMap<>();
        for (final Argument argument : arguments) {
            final String key = translatePropertyKey(argument.getName());
            result.put(key, convertGraphQLValue(key, argument.getValue(), variables));
        }
        return result;
    }

    private Object convertGraphQLValue(final String key, final Value<?> value, final Map<String, Object> variables) {
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
            if (integer.longValue() > Integer.MAX_VALUE || integer.longValue() < Integer.MIN_VALUE || "__id".equals(
                    key) || "__to_id".equals(key) || "__from_id".equals(key)) {
                return integer.longValue();
            }
            return integer.intValue();
        }
        if (value instanceof VariableReference) {
            final Object variableValue = variables.get(((VariableReference) value).getName());
            if (MVStoreModel.ID_FIELD.equals(key)) {
                if (variableValue instanceof Integer)
                    return (long) (Integer) variableValue;
                if (variableValue instanceof String && StringUtils.isNumeric((String) variableValue))
                    return Long.parseLong((String) variableValue);
            }
            return variableValue;
        }
        if (value instanceof EnumValue)
            return ((EnumValue) value).getName();
        if (value instanceof ArrayValue) {
            //noinspection rawtypes
            final List<Value> values = ((ArrayValue) value).getValues();
            final Object[] result = new Object[values.size()];
            for (int i = 0; i < result.length; i++)
                result[i] = convertGraphQLValue(key, values.get(i), variables);
            return result;
        }
        if (LOGGER.isErrorEnabled())
            LOGGER.error("Failed to convert value '" + value + "' to graph argument");
        return null;
    }

    private Map<String, Comparable<?>> convertArgumentsForGraph(final Map<String, Object> arguments) {
        final Map<String, Comparable<?>> result = new HashMap<>();
        for (final String key : arguments.keySet())
            result.put(key, (Comparable<?>) arguments.get(key));
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
            final GraphQLAppliedDirective directive = objectType.getAppliedDirective("GraphLabel");
            if (directive != null)
                return directive.getArgument("value").getValue();
        }
        return type.getName();
    }

    private Map<String, Object> selectResults(final GraphQLSchema schema, final SelectionSet selectionSet,
                                              final MVStoreModel model, final Map<String, Object> variables) {
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
                              final Map<String, Object> result, final Map<String, Object> variables) {
        if (selection instanceof Field)
            selectFieldResult(schema, (Field) selection, model, result, variables);
        else if (selection instanceof InlineFragment)
            selectInlineFragmentResult(schema, (InlineFragment) selection, model, result, variables);
        else if (LOGGER.isErrorEnabled())
            LOGGER.error("Failed to select results for selection '" + selection + "'");
    }

    private void selectFieldResult(final GraphQLSchema schema, final Field field, final MVStoreModel model,
                                   final Map<String, Object> result, final Map<String, Object> variables) {
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
                    if ("_edges".equals(field.getName())) {
                        //noinspection unchecked
                        final List<Object> inEdges = (List<Object>) getObject(schema, implementingType, field,
                                                                              Edge.TO_ID_FIELD, model.getId(),
                                                                              variables);
                        //noinspection unchecked
                        final List<Object> outEdges = (List<Object>) getObject(schema, implementingType, field,
                                                                               Edge.FROM_ID_FIELD, model.getId(),
                                                                               variables);
                        //noinspection ConstantConditions
                        inEdges.addAll(outEdges);
                        result.put(field.getResultKey(), inEdges);
                    } else if ("_edgesIn".equals(field.getName())) {
                        result.put(field.getResultKey(),
                                   getObject(schema, implementingType, field, Edge.TO_ID_FIELD, model.getId(),
                                             variables));
                    } else if ("_edgesOut".equals(field.getName())) {
                        result.put(field.getResultKey(),
                                   getObject(schema, implementingType, field, Edge.FROM_ID_FIELD, model.getId(),
                                             variables));
                    } else {
                        result.put(field.getResultKey(),
                                   getObject(schema, implementingType, field, Edge.FROM_ID_FIELD, model.getId(),
                                             variables));
                    }
                } else if (model instanceof Edge) {
                    final Edge edge = (Edge) model;
                    final long targetId = "_to".equals(field.getName()) ? edge.getToId() : edge.getFromId();
                    result.put(field.getResultKey(),
                               getObject(schema, implementingType, field, Node.ID_FIELD, targetId, variables));
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
                                            final Map<String, Object> variables) {
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

    private Object selectProcedureResults(final GraphQLSchema schema, final SelectionSet selectionSet,
                                          final GraphQLImplementingType type, final Map<String, Object> variables) {
        final Map<String, Object> result = new HashMap<>();
        for (final Selection<?> selection : selectionSet.getSelections()) {
            if (selection instanceof Field) {
                final Field selectionField = (Field) selection;
                if ("__typename".equals(selectionField.getName()) || "_id".equals(selectionField.getName()))
                    result.put(selectionField.getName(), type.getName());
                else {
                    final GraphQLFieldDefinition definition = type.getFieldDefinition(selectionField.getName());
                    final GraphQLType fieldType = unwrapType(definition.getType());
                    if (fieldType instanceof GraphQLImplementingType) {
                        final GraphQLImplementingType implementingType = (GraphQLImplementingType) fieldType;
                        if (typeHasInterface(implementingType, "ProcedureContainer")) {
                            result.put(selectionField.getName(),
                                       selectProcedureResults(schema, selectionField.getSelectionSet(),
                                                              implementingType, variables));
                        }
                    } else {
                        result.put(selectionField.getName(),
                                   selectProcedureCallResults(schema, selectionField, definition, variables));
                    }
                }
            } else if (selection instanceof InlineFragment) {
                final InlineFragment selectionFragment = (InlineFragment) selection;
                System.out.println(selectionFragment);
            } else if (LOGGER.isErrorEnabled())
                LOGGER.error("Failed to select results for selection '" + selection + "'");
        }
        return result;
    }

    private Object selectProcedureCallResults(final GraphQLSchema schema, final Field selectionField,
                                              final GraphQLFieldDefinition definition,
                                              final Map<String, Object> variables) {
        final GraphQLDirective directive = definition.getDirectivesByName().get("Procedure");
        if (directive != null) {
            final String path = directive.getArgument("path").toAppliedArgument().getValue();
            final Registry.ProcedureDefinition procedure = Registry.getInstance().getProcedure(path);
            final Map<String, Object> argumentsMap = getArgumentsMapForProcedure(selectionField.getArguments(),
                                                                                 variables, procedure);
            final Object[] parameters = convertArgumentsMapToProcedureArguments(procedure, argumentsMap);
            BaseGraph usedGraph = graph;
            if (argumentsMap.containsKey("graphViewId")) {
                final BaseGraph view = views.get((String) argumentsMap.get("graphViewId"));
                if (view != null)
                    usedGraph = view;
            }
            final ResultSet results = Registry.getInstance().callProcedure(path, usedGraph, parameters);
            final Map<String, Object> result = new HashMap<>();
            result.put("columns", results.getColumns());
            final Object[][] rows = new Object[results.getRowCount()][];
            result.put("rows", rows);
            for (int row = 0; row < rows.length; row++) {
                rows[row] = new Object[results.getColumnCount()];
                for (int col = 0; col < rows[row].length; col++)
                    rows[row][col] = results.getRow(row).getValue(col);
            }
            return result;
        }
        return null;
    }

    private Map<String, Object> getArgumentsMapForProcedure(final List<Argument> arguments,
                                                            final Map<String, Object> variables,
                                                            final Registry.ProcedureDefinition procedure) {
        final Map<String, Object> result = new HashMap<>();
        for (int i = 0; i < arguments.size(); i++) {
            final String name = arguments.get(i).getName();
            final Object value = convertGraphQLValue(name, arguments.get(i).getValue(), variables);
            final int definitionIndex = ArrayUtils.indexOf(procedure.argumentNames, name);
            if (definitionIndex == -1) {
                result.put(name, value);
            } else {
                final Class<?> type = procedure.argumentTypes[definitionIndex];
                switch (procedure.argumentSimpleTypes[definitionIndex]) {
                    case Node:
                        result.put(name, graph.getNode((long) value));
                        break;
                    case Edge:
                        result.put(name, graph.getEdgeLabel((long) value));
                        break;
                    case Enum:
                        for (final Object constant : type.getEnumConstants()) {
                            if (value.toString().equals(constant.toString())) {
                                result.put(name, constant);
                                break;
                            }
                        }
                        break;
                    case Object:
                        if (type.isArray() && type.getComponentType() == String.class) {
                            result.put(name, Arrays.stream((Object[]) value).map(Object::toString)
                                                   .toArray(String[]::new));
                        } else {
                            result.put(name, value);
                        }
                        break;
                    default:
                        result.put(name, value);
                        break;
                }
            }
        }
        return result;
    }

    private Object[] convertArgumentsMapToProcedureArguments(final Registry.ProcedureDefinition procedure,
                                                             final Map<String, Object> argumentsMap) {
        // As the arguments from graphQL may be in any order, resolve the arguments in order of procedure definition
        final Object[] result = new Object[procedure.argumentNames.length];
        for (int i = 0; i < result.length; i++)
            result[i] = argumentsMap.get(procedure.argumentNames[i]);
        return result;
    }
}
