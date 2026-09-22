package com.greenhill.coop.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record RoundCreateRequest(@NotNull @Positive Integer roundNo, @NotNull LocalDateTime ordersOpenAt,
                                 @NotNull LocalDateTime ordersCloseAt, @NotNull LocalDate pickupDate) {
}
