package com.greenhill.coop.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record MemberCreateRequest(@NotBlank @Size(max = 10) String memberNo,
                                  @NotBlank @Size(max = 100) String name,
                                  @Size(max = 20) String phone,
                                  @Size(max = 100) String email,
                                  @Size(max = 200) String address,
                                  @NotBlank @Size(min = 6, max = 100, message = "password must be between 6 and 100 characters") String password) {
}
