package com.greenhill.coop.dto;

import com.greenhill.coop.common.enums.MemberRole;
import com.greenhill.coop.common.enums.MemberStatus;
import com.greenhill.coop.entity.Member;

public record MemberView(Long id, String memberNo, String name, String phone, String email,
                         String address, MemberRole role, MemberStatus status) {

    public static MemberView from(Member m) {
        return new MemberView(m.getId(), m.getMemberNo(), m.getName(), m.getPhone(),
            m.getEmail(), m.getAddress(), m.getRole(), m.getStatus());
    }
}
