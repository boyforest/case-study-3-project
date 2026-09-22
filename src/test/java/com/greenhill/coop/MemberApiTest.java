package com.greenhill.coop;

import com.greenhill.coop.common.enums.MemberRole;
import com.greenhill.coop.common.enums.MemberStatus;
import com.greenhill.coop.entity.Member;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MemberApiTest extends ApiTestBase {

    private String coordinatorToken;

    @BeforeEach
    void setUp() throws Exception {
        createMember("M-001", "Ngaire Fletcher", MemberRole.COORDINATOR, "coop1234");
        createMember("M-094", "Ky Tran", MemberRole.MEMBER, "coop1234");
        coordinatorToken = tokenFor("M-001", "coop1234");
    }

    @Test
    void coordinatorCanCreateAndListMembers() throws Exception {
        mockMvc.perform(post("/api/members")
                .header("Authorization", "Bearer " + coordinatorToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"memberNo":"M-063","name":"Jan Buckley","phone":"0412 000 000","password":"coop1234"}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.memberNo").value("M-063"));

        mockMvc.perform(get("/api/members?keyword=Jan")
                .header("Authorization", "Bearer " + coordinatorToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.total").value(1))
            .andExpect(jsonPath("$.data.records[0].name").value("Jan Buckley"));
    }

    @Test
    void duplicateMemberNumberIsRejected() throws Exception {
        mockMvc.perform(post("/api/members")
                .header("Authorization", "Bearer " + coordinatorToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"memberNo":"M-094","name":"Someone Else","password":"coop1234"}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(409));
    }

    @Test
    void coordinatorCanUpdateMember() throws Exception {
        Member ky = memberMapper.selectOne(
            new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Member>()
                .eq(Member::getMemberNo, "M-094"));
        mockMvc.perform(put("/api/members/" + ky.getId())
                .header("Authorization", "Bearer " + coordinatorToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"name":"Ky Tran","phone":"0438 601 772","email":"ky@example.com","address":"Moorooka"}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.phone").value("0438 601 772"));
    }

    @Test
    void deactivatedMemberCannotLogInAndCanBeReactivated() throws Exception {
        Member ky = memberMapper.selectOne(
            new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Member>()
                .eq(Member::getMemberNo, "M-094"));

        mockMvc.perform(post("/api/members/" + ky.getId() + "/deactivate")
                .header("Authorization", "Bearer " + coordinatorToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("INACTIVE"));

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"memberNo\":\"M-094\",\"password\":\"coop1234\"}"))
            .andExpect(jsonPath("$.code").value(400));

        mockMvc.perform(post("/api/members/" + ky.getId() + "/activate")
                .header("Authorization", "Bearer " + coordinatorToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("ACTIVE"));

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"memberNo\":\"M-094\",\"password\":\"coop1234\"}"))
            .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    void coordinatorCannotDeactivateSelf() throws Exception {
        Member ngaire = memberMapper.selectOne(
            new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Member>()
                .eq(Member::getMemberNo, "M-001"));
        mockMvc.perform(post("/api/members/" + ngaire.getId() + "/deactivate")
                .header("Authorization", "Bearer " + coordinatorToken))
            .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void resetPasswordChangesLogin() throws Exception {
        Member ky = memberMapper.selectOne(
            new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Member>()
                .eq(Member::getMemberNo, "M-094"));
        mockMvc.perform(post("/api/members/" + ky.getId() + "/reset-password")
                .header("Authorization", "Bearer " + coordinatorToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"password\":\"newpass123\"}"))
            .andExpect(status().isOk());

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"memberNo\":\"M-094\",\"password\":\"coop1234\"}"))
            .andExpect(jsonPath("$.code").value(400));

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"memberNo\":\"M-094\",\"password\":\"newpass123\"}"))
            .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    void memberCannotAccessMemberManagement() throws Exception {
        String memberToken = tokenFor("M-094", "coop1234");
        mockMvc.perform(get("/api/members").header("Authorization", "Bearer " + memberToken))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value(403));
        mockMvc.perform(post("/api/members")
                .header("Authorization", "Bearer " + memberToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"memberNo\":\"M-999\",\"name\":\"X\",\"password\":\"coop1234\"}"))
            .andExpect(status().isForbidden());
    }

    @Test
    void statusFilterWorks() throws Exception {
        Member ky = memberMapper.selectOne(
            new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Member>()
                .eq(Member::getMemberNo, "M-094"));
        ky.setStatus(MemberStatus.INACTIVE);
        memberMapper.updateById(ky);

        mockMvc.perform(get("/api/members?status=INACTIVE")
                .header("Authorization", "Bearer " + coordinatorToken))
            .andExpect(jsonPath("$.data.total").value(1))
            .andExpect(jsonPath("$.data.records[0].memberNo").value("M-094"));
    }

    @Test
    void keywordAndStatusFilterCombine() throws Exception {
        createMember("M-050", "Jan Active", MemberRole.MEMBER, "coop1234");
        Member inactive = createMember("M-051", "Jan Inactive", MemberRole.MEMBER, "coop1234");
        inactive.setStatus(MemberStatus.INACTIVE);
        memberMapper.updateById(inactive);

        mockMvc.perform(get("/api/members?keyword=Jan&status=ACTIVE")
                .header("Authorization", "Bearer " + coordinatorToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.total").value(1))
            .andExpect(jsonPath("$.data.records[0].memberNo").value("M-050"));
    }

    @Test
    void updateMissingMemberReturns404() throws Exception {
        mockMvc.perform(put("/api/members/999999")
                .header("Authorization", "Bearer " + coordinatorToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Nobody\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(404));
    }

    @Test
    void resetPasswordTooShortIsRejected() throws Exception {
        Member ky = memberMapper.selectOne(
            new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Member>()
                .eq(Member::getMemberNo, "M-094"));
        mockMvc.perform(post("/api/members/" + ky.getId() + "/reset-password")
                .header("Authorization", "Bearer " + coordinatorToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"password\":\"123\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void overLengthMemberNumberIsRejected() throws Exception {
        mockMvc.perform(post("/api/members")
                .header("Authorization", "Bearer " + coordinatorToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"memberNo":"M-123456789","name":"Too Long","password":"coop1234"}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(400));
    }
}
