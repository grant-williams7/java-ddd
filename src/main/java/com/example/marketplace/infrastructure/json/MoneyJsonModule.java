package com.example.marketplace.infrastructure.json;

import com.example.marketplace.domain.entities.Currency;
import com.example.marketplace.domain.entities.Money;
import com.example.marketplace.domain.entities.ValidationException;
import org.springframework.stereotype.Component;
import tools.jackson.core.JsonGenerator;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.deser.std.StdDeserializer;
import tools.jackson.databind.module.SimpleModule;
import tools.jackson.databind.ser.std.StdSerializer;

/**
 * Gives {@link Money} its JSON form, {@code {"minor_units":1234,"currency":"USD"}},
 * without putting Jackson annotations on the domain. Decoding goes through the
 * {@code Money} constructor, so invalid JSON can't produce an invalid value.
 * Spring Boot registers every module bean with its {@code JsonMapper}.
 */
@Component
class MoneyJsonModule extends SimpleModule {

    MoneyJsonModule() {
        super("MoneyJsonModule");
        addSerializer(Money.class, new MoneySerializer());
        addDeserializer(Money.class, new MoneyDeserializer());
    }

    private static final class MoneySerializer extends StdSerializer<Money> {

        MoneySerializer() {
            super(Money.class);
        }

        @Override
        public void serialize(Money money, JsonGenerator generator, SerializationContext context) {
            generator.writeStartObject();
            generator.writeNumberProperty("minor_units", money.minorUnits());
            generator.writeStringProperty("currency", money.currency().code());
            generator.writeEndObject();
        }
    }

    private static final class MoneyDeserializer extends StdDeserializer<Money> {

        MoneyDeserializer() {
            super(Money.class);
        }

        @Override
        public Money deserialize(JsonParser parser, DeserializationContext context) {
            JsonNode node = context.readTree(parser);
            JsonNode minorUnits = node.get("minor_units");
            JsonNode currency = node.get("currency");

            if (minorUnits == null || !minorUnits.isIntegralNumber() || !minorUnits.canConvertToLong()) {
                return context.reportInputMismatch(Money.class, "minor_units must be an integer");
            }
            if (currency != null && !currency.isString()) {
                return context.reportInputMismatch(Money.class, "currency must be a string");
            }

            try {
                return new Money(minorUnits.longValue(), new Currency(currency == null ? "" : currency.stringValue()));
            } catch (ValidationException e) {
                return context.reportInputMismatch(Money.class, "%s", e.getMessage());
            }
        }
    }
}
