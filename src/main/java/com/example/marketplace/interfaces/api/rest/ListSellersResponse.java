package com.example.marketplace.interfaces.api.rest;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

record ListSellersResponse(@JsonProperty("sellers") List<SellerResponse> sellers) {
}
