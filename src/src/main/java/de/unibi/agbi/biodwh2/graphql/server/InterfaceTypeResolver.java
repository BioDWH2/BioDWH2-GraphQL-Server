package de.unibi.agbi.biodwh2.graphql.server;

import graphql.TypeResolutionEnvironment;
import graphql.schema.GraphQLObjectType;
import graphql.schema.TypeResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class InterfaceTypeResolver implements TypeResolver {
    private static final Logger LOGGER = LoggerFactory.getLogger(InterfaceTypeResolver.class);

    @Override
    public GraphQLObjectType getType(TypeResolutionEnvironment environment) {
        Object javaObject = environment.getObject();
        if (LOGGER.isWarnEnabled())
            LOGGER.warn("Failed to resolve interface type {}", environment);
        return null; // TODO: environment.getSchema().getObjectType();
    }
}
