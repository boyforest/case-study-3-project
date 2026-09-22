package com.greenhill.coop.auth;

import com.greenhill.coop.common.enums.MemberRole;

public record MemberContext(Long id, String memberNo, String name, MemberRole role) {
}
