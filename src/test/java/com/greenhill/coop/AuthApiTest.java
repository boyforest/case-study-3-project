package com.greenhill.coop;

import com.greenhill.coop.common.enums.MemberRole;
import com.greenhill.coop.entity.Member;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthApiTest extends ApiTestBase {

    @BeforeEach
    void setUp() {
        createMember("M-001", "Ngaire Fletcher", MemberRole.COORDINATOR, "coop1234");
        createMember("M-094", "Ky Tran", MemberRole.MEMBER, "coop1234");
    }

    @Test
    void loginWithCorrectCredentialsReturnsToken() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"memberNo\":\"M-094\",\"password\":\"coop1234\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200))
            .andExpect(jsonPath("$.data.token").isNotEmpty())
            .andExpect(jsonPath("$.data.member.memberNo").value("M-094"));
    }

    @Test
    void loginWithWrongPasswordIsRejected() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"memberNo\":\"M-094\",\"password\":\"wrong\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void inactiveMemberCannotLogIn() throws Exception {
        Member m = createMember("M-077", "Ruth Callaghan", MemberRole.MEMBER, "coop1234");
        m.setStatus(com.greenhill.coop.common.enums.MemberStatus.INACTIVE);
        memberMapper.updateById(m);

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"memberNo\":\"M-077\",\"password\":\"coop1234\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void meWithoutTokenIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    void meWithTokenReturnsCurrentMember() throws Exception {
        String token = tokenFor("M-094", "coop1234");
        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.memberNo").value("M-094"));
    }

    @Test
    void coordinatorEndpointRejectsMembersAndClearsContext() throws Exception {
        mockMvc.perform(get("/api/test/coordinator-only")
                .header("Authorization", "Bearer " + tokenFor("M-094", "coop1234")))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value(403));

        org.assertj.core.api.Assertions.assertThat(com.greenhill.coop.auth.UserContext.get()).isNull();
    }
}
