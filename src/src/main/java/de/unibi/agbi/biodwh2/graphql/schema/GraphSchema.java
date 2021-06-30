package de.unibi.agbi.biodwh2.graphql.schema;

import de.unibi.agbi.biodwh2.core.lang.Type;
import de.unibi.agbi.biodwh2.core.model.graph.Edge;
import de.unibi.agbi.biodwh2.core.model.graph.Graph;
import de.unibi.agbi.biodwh2.core.model.graph.Node;

import java.util.*;

public class GraphSchema {
    public static class BaseType {
        String label;
        Map<String, Type> propertyKeyTypes;
    }

    public static class NodeType extends BaseType {
    }

    public static class EdgeType extends BaseType {
        final Set<String> fromLabels = new HashSet<>();
        final Set<String> toLabels = new HashSet<>();
    }

    private final Map<String, NodeType> nodeTypes;
    private final Map<String, EdgeType> edgeTypes;

    public GraphSchema(final Graph graph) {
        nodeTypes = new HashMap<>();
        edgeTypes = new HashMap<>();
        loadNodeTypes(graph);
        loadEdgeTypes(graph);
    }

    private void loadNodeTypes(final Graph graph) {
        for (final String label : graph.getNodeLabels()) {
            final NodeType type = new NodeType();
            type.label = label;
            nodeTypes.put(label, type);
            final Map<String, Type> propertyKeyTypes = graph.getPropertyKeyTypesForNodeLabel(label);
            for (final String key : propertyKeyTypes.keySet())
                type.propertyKeyTypes.put(fixKeyNaming(key), propertyKeyTypes.get(key));
        }
    }

    private String fixKeyNaming(final String key) {
        if (Character.isDigit(key.charAt(0)))
            return '_' + key;
        if (key.charAt(0) == '_' && key.charAt(1) == '_')
            return key.substring(1);
        return key;
    }

    private void loadEdgeTypes(final Graph graph) {
        for (final String label : graph.getEdgeLabels()) {
            final EdgeType type = new EdgeType();
            type.label = label;
            edgeTypes.put(label, type);
            final Map<String, Type> propertyKeyTypes = graph.getPropertyKeyTypesForEdgeLabel(label);
            for (final String key : propertyKeyTypes.keySet())
                type.propertyKeyTypes.put(fixKeyNaming(key), propertyKeyTypes.get(key));
        }
        for (final Edge edge : graph.getEdges())
            loadEdgeType(graph, edge);
    }

    private void loadEdgeType(final Graph graph, final Edge edge) {
        final EdgeType type = edgeTypes.get(edge.getLabel());
        final Node fromNode = graph.getNode(edge.getFromId());
        final Node toNode = graph.getNode(edge.getToId());
        type.fromLabels.add(fromNode.getLabel());
        type.toLabels.add(toNode.getLabel());
    }

    public NodeType[] getNodeTypes() {
        return nodeTypes.values().toArray(new NodeType[0]);
    }

    public EdgeType[] getEdgeTypes() {
        return edgeTypes.values().toArray(new EdgeType[0]);
    }
}
