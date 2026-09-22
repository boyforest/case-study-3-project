package com.greenhill.coop.dto;

import com.greenhill.coop.common.enums.UnitType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record ProductRequest(@NotBlank @Size(max = 100) String name, @NotNull UnitType unitType,
                             @NotNull @DecimalMin(value = "0.01") @Digits(integer = 8, fraction = 2) BigDecimal price,
                             @Size(max = 10) String bay) {
}
