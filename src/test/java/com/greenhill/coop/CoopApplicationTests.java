package com.greenhill.coop;

import com.greenhill.coop.common.enums.MemberRole;
import com.greenhill.coop.common.enums.MemberStatus;
import com.greenhill.coop.entity.Member;
import com.greenhill.coop.mapper.MemberMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class CoopApplicationTests {

    @Autowired
    private MemberMapper memberMapper;

    @Autowired
    private MockMvc mockMvc;

    @Test
    void contextLoadsAndSchemaIsCreated() {
        assertThat(memberMapper.selectCount(null)).isZero();
    }

    @Test
    void entityRoundTripWithEnumsAndAutoFillTimestamps() {
        Member member = new Member();
        member.setMemberNo("M-900");
        member.setName("Test Member");
        member.setRole(MemberRole.MEMBER);
        member.setStatus(MemberStatus.ACTIVE);
        member.setPasswordHash("x");
        memberMapper.insert(member);

        Member loaded = memberMapper.selectById(member.getId());
        assertThat(loaded.getRole()).isEqualTo(MemberRole.MEMBER);
        assertThat(loaded.getStatus()).isEqualTo(MemberStatus.ACTIVE);
        assertThat(loaded.getCreatedAt()).isNotNull();
        assertThat(loaded.getUpdatedAt()).isNotNull();
    }

    @Test
    void unknownPathReturns404() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/no-such-page"))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isNotFound());
    }
}
