package com.greenhill.coop.dto;

import com.greenhill.coop.common.enums.UnitType;

import java.math.BigDecimal;

public record OrderLineView(Long id, Long productId, String productName, UnitType unitType,
                            BigDecimal quantity, BigDecimal unitPrice, BigDecimal lineTotal) {
}
