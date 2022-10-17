package de.unibi.agbi.biodwh2.graphql.server.model;

import de.unibi.agbi.biodwh2.core.model.graph.BaseGraph;
import de.unibi.agbi.biodwh2.core.model.graph.Edge;
import de.unibi.agbi.biodwh2.core.model.graph.Node;

import java.util.Arrays;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

public class GraphViewIds extends BaseGraph {
    private final BaseGraph graph;
    private final Set<Long> nodeIds;
    private final Set<Long> edgeIds;

    public GraphViewIds(final BaseGraph graph, final Long[] nodeIds, final Long[] edgeIds) {
        this.graph = graph;
        this.nodeIds = Arrays.stream(nodeIds).collect(Collectors.toSet());
        this.edgeIds = Arrays.stream(edgeIds).collect(Collectors.toSet());
    }

    @Override
    public Iterable<Node> getNodes() {
        return filterNodesIterable(graph.getNodes());
    }

    private Iterable<Node> filterNodesIterable(final Iterable<Node> nodes) {
        return () -> new GraphViewIds.FilterNodesIterator(nodes.iterator());
    }

    @Override
    public Iterable<Edge> getEdges() {
        return filterEdgesIterable(graph.getEdges());
    }

    private Iterable<Edge> filterEdgesIterable(final Iterable<Edge> edges) {
        return () -> new GraphViewIds.FilterEdgesIterator(edges.iterator());
    }

    @Override
    public long getNumberOfNodes() {
        return nodeIds.size();
    }

    @Override
    public long getNumberOfNodes(final String label) {
        return StreamSupport.stream(filterNodesIterable(graph.findNodes(label)).spliterator(), false).count();
    }

    @Override
    public long getNumberOfEdges() {
        return edgeIds.size();
    }

    @Override
    public long getNumberOfEdges(final String label) {
        return StreamSupport.stream(filterEdgesIterable(graph.findEdges(label)).spliterator(), false).count();
    }

    @Override
    public String[] getNodeLabels() {
        return graph.getNodeLabels();
    }

    @Override
    public String[] getEdgeLabels() {
        return graph.getEdgeLabels();
    }

    @Override
    public Node getNode(final long nodeId) {
        return nodeIds.contains(nodeId) ? graph.getNode(nodeId) : null;
    }

    @Override
    public String getNodeLabel(long nodeId) {
        return nodeIds.contains(nodeId) ? graph.getNodeLabel(nodeId) : null;
    }

    @Override
    public Edge getEdge(final long edgeId) {
        return edgeIds.contains(edgeId) ? graph.getEdge(edgeId) : null;
    }

    @Override
    public String getEdgeLabel(long edgeId) {
        return edgeIds.contains(edgeId) ? graph.getEdgeLabel(edgeId) : null;
    }

    @Override
    public Iterable<Node> findNodes(final String label) {
        return filterNodesIterable(graph.findNodes(label));
    }

    @Override
    public Iterable<Node> findNodes(final String label, final String propertyKey, final Comparable<?> value) {
        return filterNodesIterable(graph.findNodes(label, propertyKey, value));
    }

    @Override
    public Iterable<Node> findNodes(final String label, final String propertyKey1, final Comparable<?> value1,
                                    final String propertyKey2, final Comparable<?> value2) {
        return filterNodesIterable(graph.findNodes(label, propertyKey1, value1, propertyKey2, value2));
    }

    @Override
    public Iterable<Node> findNodes(final String label, final String propertyKey1, final Comparable<?> value1,
                                    final String propertyKey2, final Comparable<?> value2, final String propertyKey3,
                                    final Comparable<?> value3) {
        return filterNodesIterable(
                graph.findNodes(label, propertyKey1, value1, propertyKey2, value2, propertyKey3, value3));
    }

    @Override
    public Iterable<Node> findNodes(final String label, final Map<String, Comparable<?>> properties) {
        return filterNodesIterable(graph.findNodes(label, properties));
    }

    @Override
    public Iterable<Node> findNodes(final String propertyKey, final Comparable<?> value) {
        return filterNodesIterable(graph.findNodes(propertyKey, value));
    }

    @Override
    public Iterable<Node> findNodes(final String propertyKey1, final Comparable<?> value1, final String propertyKey2,
                                    final Comparable<?> value2) {
        return filterNodesIterable(graph.findNodes(propertyKey1, value1, propertyKey2, value2));
    }

    @Override
    public Iterable<Node> findNodes(final String propertyKey1, final Comparable<?> value1, final String propertyKey2,
                                    final Comparable<?> value2, final String propertyKey3, final Comparable<?> value3) {
        return filterNodesIterable(graph.findNodes(propertyKey1, value1, propertyKey2, value2, propertyKey3, value3));
    }

    @Override
    public Iterable<Node> findNodes(final String propertyKey1, final Comparable<?> value1, final String propertyKey2,
                                    final Comparable<?> value2, final String propertyKey3, final Comparable<?> value3,
                                    final String propertyKey4, final Comparable<?> value4) {
        return filterNodesIterable(
                graph.findNodes(propertyKey1, value1, propertyKey2, value2, propertyKey3, value3, propertyKey4,
                                value4));
    }

    @Override
    public Iterable<Node> findNodes(final Map<String, Comparable<?>> properties) {
        return filterNodesIterable(graph.findNodes(properties));
    }

    @Override
    public Iterable<Edge> findEdges(final String label) {
        return filterEdgesIterable(graph.findEdges(label));
    }

    @Override
    public Iterable<Edge> findEdges(final String label, final String propertyKey, final Comparable<?> value) {
        return filterEdgesIterable(graph.findEdges(label, propertyKey, value));
    }

    @Override
    public Iterable<Edge> findEdges(final String label, final String propertyKey1, final Comparable<?> value1,
                                    final String propertyKey2, final Comparable<?> value2) {
        return filterEdgesIterable(graph.findEdges(label, propertyKey1, value1, propertyKey2, value2));
    }

    @Override
    public Iterable<Edge> findEdges(final String label, final String propertyKey1, final Comparable<?> value1,
                                    final String propertyKey2, final Comparable<?> value2, final String propertyKey3,
                                    final Comparable<?> value3) {
        return filterEdgesIterable(
                graph.findEdges(label, propertyKey1, value1, propertyKey2, value2, propertyKey3, value3));
    }

    @Override
    public Iterable<Edge> findEdges(final String label, final Map<String, Comparable<?>> properties) {
        return filterEdgesIterable(graph.findEdges(label, properties));
    }

    @Override
    public Iterable<Edge> findEdges(final String propertyKey, final Comparable<?> value) {
        return filterEdgesIterable(graph.findEdges(propertyKey, value));
    }

    @Override
    public Iterable<Edge> findEdges(final String propertyKey1, final Comparable<?> value1, final String propertyKey2,
                                    final Comparable<?> value2) {
        return filterEdgesIterable(graph.findEdges(propertyKey1, value1, propertyKey2, value2));
    }

    @Override
    public Iterable<Edge> findEdges(final String propertyKey1, final Comparable<?> value1, final String propertyKey2,
                                    final Comparable<?> value2, final String propertyKey3, final Comparable<?> value3) {
        return filterEdgesIterable(graph.findEdges(propertyKey1, value1, propertyKey2, value2, propertyKey3, value3));
    }

    @Override
    public Iterable<Edge> findEdges(final String propertyKey1, final Comparable<?> value1, final String propertyKey2,
                                    final Comparable<?> value2, final String propertyKey3, final Comparable<?> value3,
                                    final String propertyKey4, final Comparable<?> value4) {
        return filterEdgesIterable(
                graph.findEdges(propertyKey1, value1, propertyKey2, value2, propertyKey3, value3, propertyKey4,
                                value4));
    }

    @Override
    public Iterable<Edge> findEdges(final Map<String, Comparable<?>> properties) {
        return filterEdgesIterable(graph.findEdges(properties));
    }

    private class FilterNodesIterator implements Iterator<Node> {
        private final Iterator<Node> parent;
        private Node nextNode = null;

        FilterNodesIterator(final Iterator<Node> parent) {
            this.parent = parent;
        }

        @Override
        public boolean hasNext() {
            nextNode = null;
            while (parent.hasNext()) {
                nextNode = parent.next();
                if (nodeIds.contains(nextNode.getId())) {
                    break;
                } else {
                    nextNode = null;
                }
            }
            return nextNode != null;
        }

        @Override
        public Node next() {
            return nextNode;
        }
    }

    private class FilterEdgesIterator implements Iterator<Edge> {
        private final Iterator<Edge> parent;
        private Edge nextEdge = null;

        FilterEdgesIterator(final Iterator<Edge> parent) {
            this.parent = parent;
        }

        @Override
        public boolean hasNext() {
            nextEdge = null;
            while (parent.hasNext()) {
                nextEdge = parent.next();
                if (edgeIds.contains(nextEdge.getId())) {
                    break;
                } else {
                    nextEdge = null;
                }
            }
            return nextEdge != null;
        }

        @Override
        public Edge next() {
            return nextEdge;
        }
    }
}