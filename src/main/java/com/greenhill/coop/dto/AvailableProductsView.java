package com.greenhill.coop.dto;

import java.util.List;

public record AvailableProductsView(RoundView round, List<ProductView> products) {
}
