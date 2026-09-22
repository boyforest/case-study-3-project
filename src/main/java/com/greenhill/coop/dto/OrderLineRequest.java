package com.greenhill.coop.dto;

import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record OrderLineRequest(@NotNull Long productId, @NotNull BigDecimal quantity) {
}
