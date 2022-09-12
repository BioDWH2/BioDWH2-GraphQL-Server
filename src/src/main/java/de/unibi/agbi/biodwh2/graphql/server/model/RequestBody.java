package de.unibi.agbi.biodwh2.graphql.server.model;

import java.util.Map;

public class RequestBody {
    public String query;
    public String operationName;
    public Map<String, Object> variables;
}
