package de.unibi.agbi.biodwh2.graphql.server;

import de.unibi.agbi.biodwh2.core.model.graph.Edge;
import de.unibi.agbi.biodwh2.core.model.graph.Node;
import graphql.TypeResolutionEnvironment;
import graphql.schema.GraphQLObjectType;
import graphql.schema.TypeResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

class InterfaceTypeResolver implements TypeResolver {
    private static final Logger LOGGER = LoggerFactory.getLogger(InterfaceTypeResolver.class);

    @Override
    public GraphQLObjectType getType(final TypeResolutionEnvironment environment) {
        final Object object = environment.getObject();
        if (object instanceof Node)
            return environment.getSchema().getObjectType(((Node) object).getLabel());
        if (object instanceof Edge)
            return environment.getSchema().getObjectType(((Edge) object).getLabel());
        if (object instanceof Map) {
            final Map<?, ?> map = (Map<?, ?>) object;
            final Object typeName = map.get("__typename");
            if (typeName instanceof String)
                return environment.getSchema().getObjectType((String) typeName);
        }
        if (LOGGER.isWarnEnabled())
            LOGGER.warn("Failed to resolve interface type {}", environment);
        return null;
    }
}
