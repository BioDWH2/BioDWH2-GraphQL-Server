package de.unibi.agbi.biodwh2.graphql.server;

import graphql.TypeResolutionEnvironment;
import graphql.schema.GraphQLObjectType;
import graphql.schema.TypeResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UnionTypeResolver implements TypeResolver {
    private static final Logger LOGGER = LoggerFactory.getLogger(UnionTypeResolver.class);

    @Override
    public GraphQLObjectType getType(final TypeResolutionEnvironment environment) {
        if (LOGGER.isWarnEnabled())
            LOGGER.warn("Failed to resolve union type {}", environment);
        return null;
    }
}
