package com.greenhill.coop.dto;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(@NotBlank String memberNo, @NotBlank String password) {
}
