/**
 * Wiring: turns the annotation-free application services into Spring beans.
 */
@ApplicationModule(allowedDependencies = {"domain", "application"})
package com.example.marketplace.bootstrap;

import org.springframework.modulith.ApplicationModule;
