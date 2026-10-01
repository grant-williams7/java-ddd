package com.example.marketplace.application.services;

import tools.jackson.databind.json.JsonMapper;

/** A plain Jackson codec, standing in for the infrastructure one. */
class TestJsonCodec implements ResultCodec {

    private final JsonMapper mapper = JsonMapper.builder().build();

    @Override
    public String encode(Object value) {
        return mapper.writeValueAsString(value);
    }

    @Override
    public <T> T decode(String encoded, Class<T> type) {
        return mapper.readValue(encoded, type);
    }
}
