package com.greenhill.coop;

import com.greenhill.coop.common.enums.MemberRole;
import com.greenhill.coop.common.enums.RoundStatus;
import com.greenhill.coop.common.enums.UnitType;
import com.greenhill.coop.entity.Product;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class OrderApiTest extends ApiTestBase {

    private String memberToken;
    private Long oatsId;
    private Long eggsId;
    private Long roundId;

    @BeforeEach
    void setUp() throws Exception {
        createMember("M-001", "Ngaire Fletcher", MemberRole.COORDINATOR, "coop1234");
        createMember("M-094", "Ky Tran", MemberRole.MEMBER, "coop1234");
        createMember("M-041", "Doug Halvorsen", MemberRole.MEMBER, "coop1234");
        memberToken = tokenFor("M-094", "coop1234");
        roundId = createRound(34, RoundStatus.OPEN).getId();
        oatsId = createProduct("Rolled oats, organic", UnitType.PER_KG, "3.40", "B1").getId();
        eggsId = createProduct("Eggs, free range, dozen", UnitType.PER_UNIT, "7.50", "COOL").getId();
    }

    private String linesJson() {
        return """
            {"lines":[{"productId":%d,"quantity":1.5},{"productId":%d,"quantity":2}]}
            """.formatted(oatsId, eggsId);
    }

    @Test
    void memberPlacesOrderWithPerKgAndPerUnitLines() throws Exception {
        mockMvc.perform(put("/api/orders/mine")
                .header("Authorization", "Bearer " + memberToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(linesJson()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.roundNo").value(34))
            .andExpect(jsonPath("$.data.total").value(20.10))
            .andExpect(jsonPath("$.data.lines.length()").value(2));
    }

    @Test
    void orderIsSavedAndRetrievable() throws Exception {
        mockMvc.perform(put("/api/orders/mine")
            .header("Authorization", "Bearer " + memberToken)
            .contentType(MediaType.APPLICATION_JSON)
            .content(linesJson()));

        mockMvc.perform(get("/api/orders/mine").header("Authorization", "Bearer " + memberToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.length()").value(1))
            .andExpect(jsonPath("$.data[0].lines.length()").value(2));
    }

    @Test
    void placingAgainReplacesLines() throws Exception {
        mockMvc.perform(put("/api/orders/mine")
            .header("Authorization", "Bearer " + memberToken)
            .contentType(MediaType.APPLICATION_JSON)
            .content(linesJson()));

        mockMvc.perform(put("/api/orders/mine")
                .header("Authorization", "Bearer " + memberToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"lines\":[{\"productId\":%d,\"quantity\":3}]}".formatted(eggsId)))
            .andExpect(jsonPath("$.data.total").value(22.50))
            .andExpect(jsonPath("$.data.lines.length()").value(1));
    }

    @Test
    void perUnitFractionalQuantityRejected() throws Exception {
        mockMvc.perform(put("/api/orders/mine")
                .header("Authorization", "Bearer " + memberToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"lines\":[{\"productId\":%d,\"quantity\":1.5}]}".formatted(eggsId)))
            .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void duplicateProductRejected() throws Exception {
        mockMvc.perform(put("/api/orders/mine")
                .header("Authorization", "Bearer " + memberToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"lines\":[{\"productId\":%d,\"quantity\":1},{\"productId\":%d,\"quantity\":2}]}"
                    .formatted(oatsId, oatsId)))
            .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void emptyOrderRejected() throws Exception {
        mockMvc.perform(put("/api/orders/mine")
                .header("Authorization", "Bearer " + memberToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"lines\":[]}"))
            .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void cannotOrderWhenNoRoundIsOpen() throws Exception {
        mockMvc.perform(put("/api/orders/mine")
                .header("Authorization", "Bearer " + tokenFor("M-041", "coop1234"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(linesJson()));

        roundMapper.deleteById(roundId);

        mockMvc.perform(put("/api/orders/mine")
                .header("Authorization", "Bearer " + memberToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(linesJson()))
            .andExpect(jsonPath("$.code").value(409));
    }

    @Test
    void priceIsSnapshottedAtOrderTime() throws Exception {
        mockMvc.perform(put("/api/orders/mine")
            .header("Authorization", "Bearer " + memberToken)
            .contentType(MediaType.APPLICATION_JSON)
            .content(linesJson()));

        Product oats = productMapper.selectById(oatsId);
        oats.setPrice(new java.math.BigDecimal("4.00"));
        productMapper.updateById(oats);

        mockMvc.perform(get("/api/orders/mine").header("Authorization", "Bearer " + memberToken))
            .andExpect(jsonPath("$.data[0].total").value(20.10))
            .andExpect(jsonPath("$.data[0].lines[0].unitPrice").value(3.40));
    }

    @Test
    void withdrawnProductCannotBeOrdered() throws Exception {
        Product oats = productMapper.selectById(oatsId);
        oats.setStatus(com.greenhill.coop.common.enums.ProductStatus.WITHDRAWN);
        productMapper.updateById(oats);

        mockMvc.perform(put("/api/orders/mine")
                .header("Authorization", "Bearer " + memberToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(linesJson()))
            .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void membersOnlySeeTheirOwnOrders() throws Exception {
        mockMvc.perform(put("/api/orders/mine")
            .header("Authorization", "Bearer " + memberToken)
            .contentType(MediaType.APPLICATION_JSON)
            .content(linesJson()));

        mockMvc.perform(get("/api/orders/mine")
                .header("Authorization", "Bearer " + tokenFor("M-041", "coop1234")))
            .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    void memberCanCancelOrderWhileRoundIsOpen() throws Exception {
        mockMvc.perform(put("/api/orders/mine")
            .header("Authorization", "Bearer " + memberToken)
            .contentType(MediaType.APPLICATION_JSON)
            .content(linesJson()));

        mockMvc.perform(delete("/api/orders/mine").header("Authorization", "Bearer " + memberToken))
            .andExpect(status().isOk());

        mockMvc.perform(get("/api/orders/mine").header("Authorization", "Bearer " + memberToken))
            .andExpect(jsonPath("$.data[0].status").value("CANCELLED"))
            .andExpect(jsonPath("$.data[0].lines.length()").value(2))
            .andExpect(jsonPath("$.data[0].total").value(20.10));
    }

    @Test
    void memberCanPlaceANewOrderAfterCancelling() throws Exception {
        mockMvc.perform(put("/api/orders/mine")
            .header("Authorization", "Bearer " + memberToken)
            .contentType(MediaType.APPLICATION_JSON)
            .content(linesJson()));
        mockMvc.perform(delete("/api/orders/mine").header("Authorization", "Bearer " + memberToken));

        mockMvc.perform(put("/api/orders/mine")
                .header("Authorization", "Bearer " + memberToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"lines\":[{\"productId\":%d,\"quantity\":2}]}".formatted(eggsId)))
            .andExpect(jsonPath("$.data.status").value("ACTIVE"))
            .andExpect(jsonPath("$.data.total").value(15.00));
    }

    @Test
    void cancelWithoutOrderIsRejected() throws Exception {
        mockMvc.perform(delete("/api/orders/mine").header("Authorization", "Bearer " + memberToken))
            .andExpect(jsonPath("$.code").value(404));
    }

    @Test
    void closedRoundBlocksChangesButNotReading() throws Exception {
        mockMvc.perform(put("/api/orders/mine")
            .header("Authorization", "Bearer " + memberToken)
            .contentType(MediaType.APPLICATION_JSON)
            .content(linesJson()));

        com.greenhill.coop.entity.Round round = roundMapper.selectById(roundId);
        round.setStatus(RoundStatus.CLOSED);
        roundMapper.updateById(round);

        mockMvc.perform(put("/api/orders/mine")
                .header("Authorization", "Bearer " + memberToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(linesJson()))
            .andExpect(jsonPath("$.code").value(409));

        mockMvc.perform(delete("/api/orders/mine").header("Authorization", "Bearer " + memberToken))
            .andExpect(jsonPath("$.code").value(409));

        mockMvc.perform(get("/api/orders/mine").header("Authorization", "Bearer " + memberToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].total").value(20.10));
    }

    @Test
    void changingOrderDoesNotAffectOtherMembers() throws Exception {
        mockMvc.perform(put("/api/orders/mine")
            .header("Authorization", "Bearer " + memberToken)
            .contentType(MediaType.APPLICATION_JSON)
            .content(linesJson()));

        String otherToken = tokenFor("M-041", "coop1234");
        mockMvc.perform(put("/api/orders/mine")
            .header("Authorization", "Bearer " + otherToken)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"lines\":[{\"productId\":%d,\"quantity\":1}]}".formatted(eggsId)));

        mockMvc.perform(delete("/api/orders/mine").header("Authorization", "Bearer " + memberToken));

        mockMvc.perform(get("/api/orders/mine").header("Authorization", "Bearer " + otherToken))
            .andExpect(jsonPath("$.data[0].status").value("ACTIVE"))
            .andExpect(jsonPath("$.data[0].total").value(7.50));
    }

    @Test
    void cancellingTwiceReturnsNotFound() throws Exception {
        mockMvc.perform(put("/api/orders/mine")
            .header("Authorization", "Bearer " + memberToken)
            .contentType(MediaType.APPLICATION_JSON)
            .content(linesJson()));

        mockMvc.perform(delete("/api/orders/mine").header("Authorization", "Bearer " + memberToken))
            .andExpect(status().isOk());

        mockMvc.perform(delete("/api/orders/mine").header("Authorization", "Bearer " + memberToken))
            .andExpect(jsonPath("$.code").value(404));
    }

    @Test
    void cancelOnlyAffectsTheCurrentRound() throws Exception {
        mockMvc.perform(put("/api/orders/mine")
            .header("Authorization", "Bearer " + memberToken)
            .contentType(MediaType.APPLICATION_JSON)
            .content(linesJson()));

        com.greenhill.coop.entity.Round first = roundMapper.selectById(roundId);
        first.setStatus(RoundStatus.CLOSED);
        roundMapper.updateById(first);
        createRound(35, RoundStatus.OPEN);

        mockMvc.perform(put("/api/orders/mine")
            .header("Authorization", "Bearer " + memberToken)
            .contentType(MediaType.APPLICATION_JSON)
            .content(linesJson()));

        mockMvc.perform(delete("/api/orders/mine").header("Authorization", "Bearer " + memberToken))
            .andExpect(status().isOk());

        mockMvc.perform(get("/api/orders/mine").header("Authorization", "Bearer " + memberToken))
            .andExpect(jsonPath("$.data.length()").value(2))
            .andExpect(jsonPath("$.data[0].roundNo").value(35))
            .andExpect(jsonPath("$.data[0].status").value("CANCELLED"))
            .andExpect(jsonPath("$.data[1].roundNo").value(34))
            .andExpect(jsonPath("$.data[1].status").value("ACTIVE"));
    }
}
