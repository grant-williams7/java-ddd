package com.example.marketplace.infrastructure.json;

import com.example.marketplace.application.services.ResultCodec;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/** Encodes with the application's own {@code JsonMapper}, including {@link MoneyJsonModule}. */
@Component
class JacksonResultCodec implements ResultCodec {

    private final JsonMapper mapper;

    JacksonResultCodec(JsonMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public String encode(Object value) {
        return mapper.writeValueAsString(value);
    }

    @Override
    public <T> T decode(String encoded, Class<T> type) {
        return mapper.readValue(encoded, type);
    }
}
