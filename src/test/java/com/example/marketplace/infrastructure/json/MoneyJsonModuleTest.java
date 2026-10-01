package com.example.marketplace.infrastructure.json;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.marketplace.domain.entities.Currency;
import com.example.marketplace.domain.entities.Money;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/** Go's {@code Money} JSON tests. The JSON form lives in infrastructure, so do they. */
class MoneyJsonModuleTest {

    private final JsonMapper mapper = JsonMapper.builder().addModule(new MoneyJsonModule()).build();

    @Test
    void jsonRoundTrip() {
        Money original = new Money(1234, Currency.USD);

        String json = mapper.writeValueAsString(original);
        assertThat(json).isEqualTo("{\"minor_units\":1234,\"currency\":\"USD\"}");

        assertThat(mapper.readValue(json, Money.class)).isEqualTo(original);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"minor_units\":-100,\"currency\":\"USD\"}",
            "{\"minor_units\":100,\"currency\":\"GBP\"}",
            "{\"minor_units\":100}",
            "{\"minor_units\":",
    })
    void unmarshal_invalidState(String json) {
        assertThatThrownBy(() -> mapper.readValue(json, Money.class)).isInstanceOf(JacksonException.class);
    }
}
