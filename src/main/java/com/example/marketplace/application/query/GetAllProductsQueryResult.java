package com.example.marketplace.application.query;

import com.example.marketplace.application.common.ProductResult;
import java.util.List;

public record GetAllProductsQueryResult(List<ProductResult> result) {
}
