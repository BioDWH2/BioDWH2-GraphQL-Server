package de.unibi.agbi.biodwh2.graphql.server;

import de.unibi.agbi.biodwh2.core.model.graph.Graph;
import graphql.Scalars;
import graphql.schema.DataFetcher;
import graphql.schema.DataFetcherFactory;
import graphql.schema.GraphQLScalarType;
import graphql.schema.TypeResolver;
import graphql.schema.idl.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class GraphWiringFactory implements WiringFactory {
    private static final Logger LOGGER = LoggerFactory.getLogger(GraphWiringFactory.class);

    private final Graph graph;

    GraphWiringFactory(final Graph graph) {
        this.graph = graph;
    }

    @Override
    public boolean providesScalar(ScalarWiringEnvironment environment) {
        return getScalar(environment) != null;
    }

    @Override
    public GraphQLScalarType getScalar(ScalarWiringEnvironment environment) {
        if (environment.getScalarTypeDefinition().getName().equals(Scalars.GraphQLInt.getName()))
            return Scalars.GraphQLInt;
        if (environment.getScalarTypeDefinition().getName().equals(Scalars.GraphQLFloat.getName()))
            return Scalars.GraphQLFloat;
        if (environment.getScalarTypeDefinition().getName().equals(Scalars.GraphQLString.getName()))
            return Scalars.GraphQLString;
        if (environment.getScalarTypeDefinition().getName().equals(Scalars.GraphQLBoolean.getName()))
            return Scalars.GraphQLBoolean;
        if (environment.getScalarTypeDefinition().getName().equals(Scalars.GraphQLID.getName()))
            return Scalars.GraphQLID;
        if (LOGGER.isWarnEnabled())
            LOGGER.warn("Failed to get scalar {}", environment.getScalarTypeDefinition());
        return null;
    }

    @Override
    public boolean providesTypeResolver(InterfaceWiringEnvironment environment) {
        return getTypeResolver(environment) != null;
    }

    @Override
    public TypeResolver getTypeResolver(InterfaceWiringEnvironment environment) {
        return new InterfaceTypeResolver();
    }

    @Override
    public boolean providesTypeResolver(UnionWiringEnvironment environment) {
        return getTypeResolver(environment) != null;
    }

    @Override
    public TypeResolver getTypeResolver(UnionWiringEnvironment environment) {
        if (LOGGER.isWarnEnabled())
            LOGGER.warn("Failed to get union {}", environment.getUnionTypeDefinition());
        return null;
    }

    @Override
    public boolean providesDataFetcherFactory(FieldWiringEnvironment environment) {
        return false;
    }

    @Override
    public <T> DataFetcherFactory<T> getDataFetcherFactory(FieldWiringEnvironment environment) {
        return null;
    }

    @Override
    public boolean providesSchemaDirectiveWiring(SchemaDirectiveWiringEnvironment environment) {
        return getSchemaDirectiveWiring(environment) != null;
    }

    @Override
    public SchemaDirectiveWiring getSchemaDirectiveWiring(SchemaDirectiveWiringEnvironment environment) {
        return null;
    }

    @Override
    public boolean providesDataFetcher(FieldWiringEnvironment environment) {
        return getDataFetcher(environment) != null;
    }

    @Override
    public DataFetcher getDataFetcher(FieldWiringEnvironment environment) {
        return null;
    }

    @Override
    public DataFetcher getDefaultDataFetcher(FieldWiringEnvironment environment) {
        return new GraphDataFetcher(graph);
    }
}
