package net.talpidae.base.mapper;

import com.fasterxml.jackson.databind.ObjectMapper;


public interface ObjectMapperConfigurer
{
    /**
     * Use this to customize object mapper configuration.
     *
     * @param objectMapper The newly created and preconfigured ObjectMapper instance.
     */
    void configure(ObjectMapper objectMapper);
}
