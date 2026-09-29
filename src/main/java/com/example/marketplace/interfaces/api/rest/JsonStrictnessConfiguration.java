package com.example.marketplace.interfaces.api.rest;

import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.cfg.CoercionAction;
import tools.jackson.databind.cfg.CoercionInputShape;
import tools.jackson.databind.type.LogicalType;

/**
 * The few places the API spec is stricter than Jackson's defaults. Everything
 * else is Spring Boot's standard configuration.
 */
@Configuration(proxyBeanMethods = false)
class JsonStrictnessConfiguration {

    @Bean
    JsonMapperBuilderCustomizer strictJsonTypes() {
        return builder -> builder
                // Money is never a float: 49.99 must not silently become 49.
                .disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT)
                // Responses list their fields in declaration order, as the docs show them.
                .disable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
                // Integers must be JSON numbers, not "4999".
                .withCoercionConfig(LogicalType.Integer, config -> config
                        .setCoercion(CoercionInputShape.String, CoercionAction.Fail)
                        .setCoercion(CoercionInputShape.EmptyString, CoercionAction.Fail))
                // Strings must be JSON strings, not 5 or true.
                .withCoercionConfig(LogicalType.Textual, config -> config
                        .setCoercion(CoercionInputShape.Integer, CoercionAction.Fail)
                        .setCoercion(CoercionInputShape.Float, CoercionAction.Fail)
                        .setCoercion(CoercionInputShape.Boolean, CoercionAction.Fail));
    }
}
