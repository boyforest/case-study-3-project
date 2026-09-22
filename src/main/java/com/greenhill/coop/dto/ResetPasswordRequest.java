package com.greenhill.coop.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ResetPasswordRequest(
    @NotBlank @Size(min = 6, max = 100, message = "password must be between 6 and 100 characters") String password) {
}
