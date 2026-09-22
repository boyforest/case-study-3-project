package com.greenhill.coop.dto;

import com.greenhill.coop.common.enums.ProductStatus;
import com.greenhill.coop.common.enums.UnitType;
import com.greenhill.coop.entity.Product;

import java.math.BigDecimal;

public record ProductView(Long id, String name, UnitType unitType, BigDecimal price, String bay, ProductStatus status) {

    public static ProductView from(Product p) {
        return new ProductView(p.getId(), p.getName(), p.getUnitType(), p.getPrice(), p.getBay(), p.getStatus());
    }
}
