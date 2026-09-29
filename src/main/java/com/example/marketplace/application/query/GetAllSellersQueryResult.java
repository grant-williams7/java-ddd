package com.example.marketplace.application.query;

import com.example.marketplace.application.common.SellerResult;
import java.util.List;

public record GetAllSellersQueryResult(List<SellerResult> result) {
}
