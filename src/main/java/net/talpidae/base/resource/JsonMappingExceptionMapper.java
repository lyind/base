package net.talpidae.base.resource;


import com.fasterxml.jackson.databind.JsonMappingException;
import lombok.extern.slf4j.Slf4j;
import net.talpidae.base.server.ServerConfig;

import javax.inject.Inject;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;


@Slf4j
@Provider
public class JsonMappingExceptionMapper implements ExceptionMapper<JsonMappingException>
{
    private final boolean doLog;

    @Inject
    public JsonMappingExceptionMapper(ServerConfig serverConfig)
    {
        doLog = serverConfig.isLoggingFeatureEnabled();
    }

    @Override
    public Response toResponse(JsonMappingException exception)
    {
        if (doLog)
        {
            log.warn("failed to map Json object: {}", exception.getMessage());
        }

        return Response.status(Response.Status.BAD_REQUEST).build();
    }
}