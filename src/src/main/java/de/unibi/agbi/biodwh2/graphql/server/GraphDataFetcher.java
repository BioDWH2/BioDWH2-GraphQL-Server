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
        final Map<String, Object> arguments = environment.getArguments();
        if (type.getInterfaces().stream().anyMatch(i -> i.getName().equals("Node"))) {
            final List<Node> result = new ArrayList<>();
            graph.findNodes(type.getName(), arguments).forEach(result::add);
            return result;
        }
        if (type.getInterfaces().stream().anyMatch(i -> i.getName().equals("Edge"))) {
            final List<Edge> result = new ArrayList<>();
            // TODO: all arguments
            final String key = arguments.keySet().toArray(new String[0])[0];
            final String edgeLabel = StringUtils.splitByWholeSeparator(type.getName(), "__")[1];
            graph.findEdges(edgeLabel, key, arguments.get(key)).forEach(result::add);
            return result;
        }
        return null;
    }
}
