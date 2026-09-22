package com.greenhill.coop.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class RoundTotalRow {
    private Long productId;
    private String productName;
    private String unitType;
    private BigDecimal totalQuantity;
    private BigDecimal totalAmount;
}
