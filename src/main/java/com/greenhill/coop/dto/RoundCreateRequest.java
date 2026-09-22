package com.greenhill.coop.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record RoundCreateRequest(@NotNull Integer roundNo, @NotNull LocalDateTime ordersOpenAt,
                                 @NotNull LocalDateTime ordersCloseAt, @NotNull LocalDate pickupDate) {
}
