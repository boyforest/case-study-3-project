package com.greenhill.coop;

import com.greenhill.coop.common.enums.MemberRole;
import com.greenhill.coop.common.enums.RoundStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RoundApiTest extends ApiTestBase {

    private String coordinatorToken;

    @BeforeEach
    void setUp() throws Exception {
        createMember("M-001", "Ngaire Fletcher", MemberRole.COORDINATOR, "coop1234");
        createMember("M-094", "Ky Tran", MemberRole.MEMBER, "coop1234");
        coordinatorToken = tokenFor("M-001", "coop1234");
    }

    private String roundJson(int roundNo) {
        return """
            {"roundNo":%d,"ordersOpenAt":"2026-09-25T09:00:00","ordersCloseAt":"2026-09-27T20:00:00","pickupDate":"2026-10-01"}
            """.formatted(roundNo);
    }

    @Test
    void coordinatorCreatesOpenRound() throws Exception {
        mockMvc.perform(post("/api/rounds")
                .header("Authorization", "Bearer " + coordinatorToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(roundJson(34)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("OPEN"))
            .andExpect(jsonPath("$.data.roundNo").value(34));
    }

    @Test
    void onlyOneOpenRoundAllowed() throws Exception {
        createRound(33, RoundStatus.OPEN);
        mockMvc.perform(post("/api/rounds")
                .header("Authorization", "Bearer " + coordinatorToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(roundJson(34)))
            .andExpect(jsonPath("$.code").value(409));
    }

    @Test
    void duplicateRoundNumberRejected() throws Exception {
        createRound(33, RoundStatus.PACKED);
        mockMvc.perform(post("/api/rounds")
                .header("Authorization", "Bearer " + coordinatorToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(roundJson(33)))
            .andExpect(jsonPath("$.code").value(409));
    }

    @Test
    void closeThenPackTransitionsStatus() throws Exception {
        Long id = createRound(33, RoundStatus.OPEN).getId();

        mockMvc.perform(post("/api/rounds/" + id + "/close")
                .header("Authorization", "Bearer " + coordinatorToken))
            .andExpect(jsonPath("$.data.status").value("CLOSED"));

        mockMvc.perform(post("/api/rounds/" + id + "/pack")
                .header("Authorization", "Bearer " + coordinatorToken))
            .andExpect(jsonPath("$.data.status").value("PACKED"));
    }

    @Test
    void invalidTransitionsRejected() throws Exception {
        Long openId = createRound(33, RoundStatus.OPEN).getId();
        mockMvc.perform(post("/api/rounds/" + openId + "/pack")
                .header("Authorization", "Bearer " + coordinatorToken))
            .andExpect(jsonPath("$.code").value(409));

        Long packedId = createRound(32, RoundStatus.PACKED).getId();
        mockMvc.perform(post("/api/rounds/" + packedId + "/close")
                .header("Authorization", "Bearer " + coordinatorToken))
            .andExpect(jsonPath("$.code").value(409));
    }

    @Test
    void currentReturnsOpenRoundOrNull() throws Exception {
        String memberToken = tokenFor("M-094", "coop1234");
        mockMvc.perform(get("/api/rounds/current").header("Authorization", "Bearer " + memberToken))
            .andExpect(jsonPath("$.data", org.hamcrest.Matchers.nullValue()));

        createRound(34, RoundStatus.OPEN);
        mockMvc.perform(get("/api/rounds/current").header("Authorization", "Bearer " + memberToken))
            .andExpect(jsonPath("$.data.roundNo").value(34));
    }

    @Test
    void memberCannotManageRounds() throws Exception {
        String memberToken = tokenFor("M-094", "coop1234");
        mockMvc.perform(post("/api/rounds")
                .header("Authorization", "Bearer " + memberToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(roundJson(34)))
            .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/rounds").header("Authorization", "Bearer " + memberToken))
            .andExpect(status().isForbidden());
    }
}
