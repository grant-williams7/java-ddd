package com.example.marketplace.interfaces.api.rest;

import com.fasterxml.jackson.annotation.JsonProperty;

record ErrorResponse(@JsonProperty("error") String error) {
}
