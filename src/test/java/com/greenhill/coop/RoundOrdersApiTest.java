package com.greenhill.coop;

import com.greenhill.coop.common.enums.MemberRole;
import com.greenhill.coop.common.enums.RoundStatus;
import com.greenhill.coop.common.enums.UnitType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RoundOrdersApiTest extends ApiTestBase {

    private String coordinatorToken;
    private Long roundId;
    private Long oatsId;

    @BeforeEach
    void setUp() throws Exception {
        createMember("M-001", "Ngaire Fletcher", MemberRole.COORDINATOR, "coop1234");
        createMember("M-094", "Ky Tran", MemberRole.MEMBER, "coop1234");
        createMember("M-041", "Doug Halvorsen", MemberRole.MEMBER, "coop1234");
        coordinatorToken = tokenFor("M-001", "coop1234");
        roundId = createRound(34, RoundStatus.OPEN).getId();
        oatsId = createProduct("Rolled oats, organic", UnitType.PER_KG, "3.40", "B1").getId();
    }

    private void place(String memberNo, String quantity) throws Exception {
        mockMvc.perform(put("/api/orders/mine")
            .header("Authorization", "Bearer " + tokenFor(memberNo, "coop1234"))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"lines\":[{\"productId\":%d,\"quantity\":%s}]}".formatted(oatsId, quantity)));
    }

    @Test
    void coordinatorSeesAllOrdersWithMemberAndLines() throws Exception {
        place("M-094", "1.5");
        place("M-041", "2");

        mockMvc.perform(get("/api/orders?roundId=" + roundId)
                .header("Authorization", "Bearer " + coordinatorToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.length()").value(2))
            .andExpect(jsonPath("$.data[0].memberNo").value("M-094"))
            .andExpect(jsonPath("$.data[0].lines.length()").value(1))
            .andExpect(jsonPath("$.data[0].total").value(5.10))
            .andExpect(jsonPath("$.data[1].total").value(6.80));
    }

    @Test
    void emptyRoundReturnsEmptyArray() throws Exception {
        mockMvc.perform(get("/api/orders?roundId=" + roundId)
                .header("Authorization", "Bearer " + coordinatorToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data").isArray())
            .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    void closedRoundOrdersAreStillVisible() throws Exception {
        place("M-094", "1.5");
        com.greenhill.coop.entity.Round round = roundMapper.selectById(roundId);
        round.setStatus(RoundStatus.CLOSED);
        roundMapper.updateById(round);

        mockMvc.perform(get("/api/orders?roundId=" + roundId)
                .header("Authorization", "Bearer " + coordinatorToken))
            .andExpect(jsonPath("$.data.length()").value(1));
    }

    @Test
    void memberCannotSeeRoundOrders() throws Exception {
        mockMvc.perform(get("/api/orders?roundId=" + roundId)
                .header("Authorization", "Bearer " + tokenFor("M-094", "coop1234")))
            .andExpect(status().isForbidden());
    }
}
