package de.unibi.agbi.biodwh2.graphql.server;

import de.unibi.agbi.biodwh2.core.model.graph.Edge;
import de.unibi.agbi.biodwh2.core.model.graph.Graph;
import de.unibi.agbi.biodwh2.core.model.graph.Node;
import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLType;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

class GraphDataFetcher implements DataFetcher {
    protected final Graph graph;

    public GraphDataFetcher(final Graph graph) {
        this.graph = graph;
    }

    @Override
    public Object get(final DataFetchingEnvironment environment) {
        if (environment.getSource() != null) {
            final Node node = environment.getSource();
            return node.getProperty(environment.getFieldDefinition().getName());
        }
        final String label = environment.getMergedField().getName();
        final GraphQLType type = environment.getGraphQLSchema().getType(label);
        if (type instanceof GraphQLObjectType)
            return getObject(environment, (GraphQLObjectType) type);
        return null;
    }

    private Object getObject(final DataFetchingEnvironment environment, final GraphQLObjectType type) {
        final Map<String, Comparable<?>> arguments = convertArgumentsForGraph(environment.getArguments());
        if (type.getInterfaces().stream().anyMatch(i -> i.getName().equals("Node"))) {
            final List<Node> result = new ArrayList<>();
            graph.findNodes(type.getName(), arguments).forEach(result::add);
            return result;
        }
        if (type.getInterfaces().stream().anyMatch(i -> i.getName().equals("Edge"))) {
            final List<Edge> result = new ArrayList<>();
            final String edgeLabel = StringUtils.splitByWholeSeparator(type.getName(), "__")[1];
            graph.findEdges(edgeLabel, arguments).forEach(result::add);
            return result;
        }
        return null;
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
}
