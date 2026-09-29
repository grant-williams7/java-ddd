package com.example.marketplace.infrastructure.outbox;

/**
 * Forwards an event payload to the outside world (message broker, webhook,
 * ...). Implementations must tolerate the same event more than once: the
 * outbox guarantees at-least-once delivery, not exactly-once.
 */
interface Publisher {

    void publish(String eventName, String payload);
}
