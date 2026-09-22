package com.greenhill.coop.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record MemberUpdateRequest(@NotBlank @Size(max = 100) String name,
                                  @Size(max = 20) String phone,
                                  @Size(max = 100) String email,
                                  @Size(max = 200) String address) {
}
