package com.greenhill.coop.dto;

public record LoginResponse(String token, MemberView member) {
}
