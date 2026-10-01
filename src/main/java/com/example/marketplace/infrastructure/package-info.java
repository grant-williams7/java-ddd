/**
 * The details: JDBC repositories, the outbox relay, JSON encoding, and database
 * configuration. Has no public types; Spring finds its components by scanning.
 */
@ApplicationModule(allowedDependencies = {"domain", "application"})
package com.example.marketplace.infrastructure;

import org.springframework.modulith.ApplicationModule;
