package com.greenhill.coop.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record MemberCreateRequest(@NotBlank String memberNo, @NotBlank String name, String phone,
                                  String email, String address,
                                  @NotBlank @Size(min = 6, message = "password must be at least 6 characters") String password) {
}
