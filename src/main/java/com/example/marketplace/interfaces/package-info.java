/**
 * The edge: REST controllers, request and response DTOs, and HTTP concerns.
 * Has no public types; Spring finds its components by scanning.
 */
@ApplicationModule(allowedDependencies = {"domain", "application"})
package com.example.marketplace.interfaces;

import org.springframework.modulith.ApplicationModule;
