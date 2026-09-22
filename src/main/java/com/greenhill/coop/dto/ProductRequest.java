package com.greenhill.coop.dto;

import com.greenhill.coop.common.enums.UnitType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record ProductRequest(@NotBlank String name, @NotNull UnitType unitType,
                             @NotNull @DecimalMin(value = "0.01") BigDecimal price, String bay) {
}
