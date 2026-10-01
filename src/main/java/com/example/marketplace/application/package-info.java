/**
 * The use cases: commands, queries, results, and the services that orchestrate
 * the domain. Depends on the domain only.
 */
@ApplicationModule(type = ApplicationModule.Type.OPEN, allowedDependencies = "domain")
package com.example.marketplace.application;

import org.springframework.modulith.ApplicationModule;
