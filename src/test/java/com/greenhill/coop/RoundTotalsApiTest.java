package com.greenhill.coop;

import com.greenhill.coop.common.enums.MemberRole;
import com.greenhill.coop.common.enums.RoundStatus;
import com.greenhill.coop.common.enums.UnitType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RoundTotalsApiTest extends ApiTestBase {

    private String coordinatorToken;
    private Long roundId;
    private Long oatsId;
    private Long eggsId;

    @BeforeEach
    void setUp() throws Exception {
        createMember("M-001", "Ngaire Fletcher", MemberRole.COORDINATOR, "coop1234");
        createMember("M-094", "Ky Tran", MemberRole.MEMBER, "coop1234");
        createMember("M-041", "Doug Halvorsen", MemberRole.MEMBER, "coop1234");
        coordinatorToken = tokenFor("M-001", "coop1234");
        roundId = createRound(34, RoundStatus.OPEN).getId();
        oatsId = createProduct("Rolled oats, organic", UnitType.PER_KG, "3.40", "B1").getId();
        eggsId = createProduct("Eggs, free range, dozen", UnitType.PER_UNIT, "7.50", "COOL").getId();
    }

    private void order(String memberNo, String oats, String eggs) throws Exception {
        mockMvc.perform(put("/api/orders/mine")
            .header("Authorization", "Bearer " + tokenFor(memberNo, "coop1234"))
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"lines":[{"productId":%d,"quantity":%s},{"productId":%d,"quantity":%s}]}
                """.formatted(oatsId, oats, eggsId, eggs)));
    }

    @Test
    void totalsAggregateQuantityAndAmountPerProduct() throws Exception {
        order("M-094", "1.5", "2");
        order("M-041", "2", "1");

        mockMvc.perform(get("/api/rounds/" + roundId + "/totals")
                .header("Authorization", "Bearer " + coordinatorToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.rows.length()").value(2))
            .andExpect(jsonPath("$.data.totalAmount").value(34.40))
            .andExpect(jsonPath("$.data.rows[0].productName").value("Eggs, free range, dozen"))
            .andExpect(jsonPath("$.data.rows[0].totalQuantity").value(3))
            .andExpect(jsonPath("$.data.rows[0].totalAmount").value(22.50))
            .andExpect(jsonPath("$.data.rows[1].totalQuantity").value(3.5))
            .andExpect(jsonPath("$.data.rows[1].totalAmount").value(11.90));
    }

    @Test
    void cancelledOrdersAreExcludedFromTotals() throws Exception {
        order("M-094", "1.5", "2");
        order("M-041", "2", "1");

        mockMvc.perform(delete("/api/orders/mine")
            .header("Authorization", "Bearer " + tokenFor("M-041", "coop1234")));

        mockMvc.perform(get("/api/rounds/" + roundId + "/totals")
                .header("Authorization", "Bearer " + coordinatorToken))
            .andExpect(jsonPath("$.data.totalAmount").value(20.10))
            .andExpect(jsonPath("$.data.rows[1].totalQuantity").value(1.5));
    }

    @Test
    void emptyRoundHasZeroTotals() throws Exception {
        mockMvc.perform(get("/api/rounds/" + roundId + "/totals")
                .header("Authorization", "Bearer " + coordinatorToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.rows").isEmpty())
            .andExpect(jsonPath("$.data.totalAmount").value(0.0));
    }

    @Test
    void unknownRoundReturnsNotFound() throws Exception {
        mockMvc.perform(get("/api/rounds/99999/totals")
                .header("Authorization", "Bearer " + coordinatorToken))
            .andExpect(jsonPath("$.code").value(404));
    }

    @Test
    void memberCannotSeeTotals() throws Exception {
        mockMvc.perform(get("/api/rounds/" + roundId + "/totals")
                .header("Authorization", "Bearer " + tokenFor("M-094", "coop1234")))
            .andExpect(status().isForbidden());
    }
}
