package com.greenhill.coop.dto;

import jakarta.validation.constraints.NotBlank;

public record MemberUpdateRequest(@NotBlank String name, String phone, String email, String address) {
}
