package de.unibi.agbi.biodwh2.graphql.schema;

import de.unibi.agbi.biodwh2.core.collections.LongTrie;
import de.unibi.agbi.biodwh2.core.io.mvstore.MVMapWrapper;
import de.unibi.agbi.biodwh2.core.io.mvstore.MVStoreCollection;
import de.unibi.agbi.biodwh2.core.io.mvstore.MVStoreNonUniqueTrieIndex;
import de.unibi.agbi.biodwh2.core.lang.Type;
import de.unibi.agbi.biodwh2.core.model.graph.Edge;
import de.unibi.agbi.biodwh2.core.model.graph.Graph;
import org.apache.commons.lang3.StringUtils;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;

public class GraphSchema {
    public static class BaseType {
        String label;
        Map<String, Type> propertyKeyTypes = new HashMap<>();

        final String fixedLabel() {
            return fixLabel(label);
        }

        public static String fixLabel(final String label) {
            return StringUtils.replace(StringUtils.replace(label, "-", "_"), ".", "_");
        }
    }

    public static class NodeType extends BaseType {
    }

    public static class EdgeType extends BaseType {
        final Set<String> fromLabels = new HashSet<>();
        final Set<String> toLabels = new HashSet<>();
    }

    private final Map<String, NodeType> nodeTypes = new HashMap<>();
    private final Map<String, EdgeType> edgeTypes = new HashMap<>();

    public GraphSchema(final Graph graph) {
        loadNodeTypes(graph);
        loadEdgeTypes(graph);
    }

    private void loadNodeTypes(final Graph graph) {
        for (final String label : graph.getNodeLabels()) {
            if (graph.getNumberOfNodes(label) == 0)
                continue;
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
            if (graph.getNumberOfEdges(label) == 0)
                continue;
            final EdgeType type = new EdgeType();
            type.label = label;
            edgeTypes.put(label, type);
            final Map<String, Type> propertyKeyTypes = graph.getPropertyKeyTypesForEdgeLabel(label);
            for (final String key : propertyKeyTypes.keySet())
                type.propertyKeyTypes.put(fixKeyNaming(key), propertyKeyTypes.get(key));
        }
        try {
            final Field f = graph.getClass().getSuperclass().getDeclaredField("edgeRepositories");
            f.setAccessible(true);
            final Field f2 = MVStoreNonUniqueTrieIndex.class.getDeclaredField("map");
            f2.setAccessible(true);
            final ConcurrentMap<String, MVStoreCollection<Edge>> edgeRepositories = (ConcurrentMap<String, MVStoreCollection<Edge>>) f.get(
                    graph);
            for (final String label : graph.getEdgeLabels()) {
                final EdgeType type = edgeTypes.get(label);
                final MVStoreCollection<Edge> edgeCollection = edgeRepositories.get(label);
                final MVStoreNonUniqueTrieIndex fromIndex = (MVStoreNonUniqueTrieIndex) edgeCollection.getIndex(
                        Edge.FROM_ID_FIELD);
                final MVStoreNonUniqueTrieIndex toIndex = (MVStoreNonUniqueTrieIndex) edgeCollection.getIndex(
                        Edge.TO_ID_FIELD);
                final MVMapWrapper<Comparable<?>, LongTrie> fromMap = (MVMapWrapper<Comparable<?>, LongTrie>) f2.get(
                        fromIndex);
                final MVMapWrapper<Comparable<?>, LongTrie> toMap = (MVMapWrapper<Comparable<?>, LongTrie>) f2.get(
                        toIndex);
                for (final Comparable<?> id : fromMap.keySet())
                    type.fromLabels.add(graph.getNodeLabel((Long) id));
                for (final Comparable<?> id : toMap.keySet())
                    type.toLabels.add(graph.getNodeLabel((Long) id));
            }
        } catch (Exception e) {
            for (final String label : graph.getEdgeLabels()) {
                final EdgeType type = edgeTypes.get(label);
                for (final Edge edge : graph.getEdges(label)) {
                    type.fromLabels.add(graph.getNodeLabel(edge.getFromId()));
                    type.toLabels.add(graph.getNodeLabel(edge.getToId()));
                }
            }
        }
    }

    public NodeType[] getNodeTypes() {
        return nodeTypes.values().toArray(new NodeType[0]);
    }

    public EdgeType[] getEdgeTypes() {
        return edgeTypes.values().toArray(new EdgeType[0]);
    }
}
