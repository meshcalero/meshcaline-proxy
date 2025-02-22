package org.meshcaline.proxy.configuration.repository;

import org.meshcaline.proxy.configuration.model.ProxyConfiguration;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.convert.ReadingConverter;
import org.springframework.data.convert.WritingConverter;
import org.springframework.lang.NonNull;

import java.time.Instant;
import java.util.UUID;

public record ProxyConfigurationEntity (
    @Id String id,
    ProxyConfiguration configuration,
    @CreatedDate Instant createdAt,
    @LastModifiedDate Instant lastModifiedAt
) {

    public ProxyConfigurationEntity(ProxyConfiguration configuration) {
        this(UUID.randomUUID().toString(), configuration, null, null);
    }

    @ReadingConverter
    public static class JsonToProxyConfigurationConverter implements Converter<String, ProxyConfiguration> {
        private static final ObjectMapper objectMapper = new ObjectMapper();

        @Override
        public ProxyConfiguration convert(@NonNull String source) {
            try {
                return objectMapper.readValue(source, ProxyConfiguration.class);
            } catch (JsonProcessingException e) {
                throw new RuntimeException("Error deserializing JSON to ProxyConfiguration", e);
            }
        }
    }

    @WritingConverter
    public static class ProxyConfigurationToJsonConverter implements Converter<ProxyConfiguration, String> {
        private static final ObjectMapper objectMapper = new ObjectMapper();

        @Override
        public String convert(@NonNull ProxyConfiguration source) {
            try {
                return objectMapper.writeValueAsString(source);
            } catch (JsonProcessingException e) {
                throw new RuntimeException("Error serializing ProxyConfiguration", e);
            }
        }
    }

}
