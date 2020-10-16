package de.unibi.agbi.biodwh2.graphql.schema;

import de.unibi.agbi.biodwh2.core.model.graph.Edge;
import de.unibi.agbi.biodwh2.core.model.graph.Graph;
import de.unibi.agbi.biodwh2.core.model.graph.Node;

import java.util.*;

public class GraphSchema {
    public static class Type {
        String label;
        final Map<String, Class<?>> propertyKeyTypes = new HashMap<>();
    }

    public static class NodeType extends Type {
    }

    public static class EdgeType extends Type {
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
        for (final Node node : graph.getNodes())
            loadNodeType(node);
    }

    private void loadNodeType(final Node node) {
        NodeType type = nodeTypes.get(node.getLabel());
        if (type == null) {
            type = new NodeType();
            type.label = node.getLabel();
            nodeTypes.put(type.label, type);
        }
        final Map<String, Class<?>> propertyKeyTypes = node.getPropertyKeyTypes();
        for (final String propertyKey : propertyKeyTypes.keySet())
            if (!"_modified".equalsIgnoreCase(propertyKey) && !"_revision".equalsIgnoreCase(propertyKey))
                type.propertyKeyTypes.put(propertyKey, propertyKeyTypes.get(propertyKey));
    }

    private void loadEdgeTypes(final Graph graph) {
        for (final Edge edge : graph.getEdges())
            loadEdgeType(graph, edge);
    }

    private void loadEdgeType(final Graph graph, final Edge edge) {
        EdgeType type = edgeTypes.get(edge.getLabel());
        if (type == null) {
            type = new EdgeType();
            type.label = edge.getLabel();
            edgeTypes.put(type.label, type);
        }
        final Map<String, Class<?>> propertyKeyTypes = edge.getPropertyKeyTypes();
        for (final String propertyKey : propertyKeyTypes.keySet())
            if (!"_modified".equalsIgnoreCase(propertyKey) && !"_revision".equalsIgnoreCase(propertyKey))
                type.propertyKeyTypes.put(propertyKey, propertyKeyTypes.get(propertyKey));
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
