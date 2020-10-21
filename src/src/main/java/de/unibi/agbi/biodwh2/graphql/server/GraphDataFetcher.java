package de.unibi.agbi.biodwh2.graphql.server;

import de.unibi.agbi.biodwh2.core.model.graph.Graph;
import de.unibi.agbi.biodwh2.core.model.graph.Node;
import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

class GraphDataFetcher implements DataFetcher {
    protected final Graph graph;

    public GraphDataFetcher(final Graph graph) {
        this.graph = graph;
    }

    @Override
    public Object get(DataFetchingEnvironment dataFetchingEnvironment) {
        if (dataFetchingEnvironment.getSource() != null) {
            final Node node = dataFetchingEnvironment.getSource();
            return node.getProperty(dataFetchingEnvironment.getFieldDefinition().getName());
        }
        Map<String, Object> arguments = dataFetchingEnvironment.getArguments();
        final String label = dataFetchingEnvironment.getMergedField().getName();
        List<Node> result = new ArrayList<>();
        graph.findNodes(label, arguments).forEach(result::add);
        return result;
    }
}
