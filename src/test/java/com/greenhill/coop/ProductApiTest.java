package com.greenhill.coop;

import com.greenhill.coop.common.enums.MemberRole;
import com.greenhill.coop.common.enums.UnitType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ProductApiTest extends ApiTestBase {

    private String coordinatorToken;

    @BeforeEach
    void setUp() throws Exception {
        createMember("M-001", "Ngaire Fletcher", MemberRole.COORDINATOR, "coop1234");
        createMember("M-094", "Ky Tran", MemberRole.MEMBER, "coop1234");
        coordinatorToken = tokenFor("M-001", "coop1234");
    }

    @Test
    void coordinatorCanCreateBothUnitTypes() throws Exception {
        mockMvc.perform(post("/api/products")
                .header("Authorization", "Bearer " + coordinatorToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"name":"Rolled oats, organic","unitType":"PER_KG","price":3.40,"bay":"B1"}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.unitType").value("PER_KG"))
            .andExpect(jsonPath("$.data.price").value(3.40));

        mockMvc.perform(post("/api/products")
                .header("Authorization", "Bearer " + coordinatorToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"name":"Tahini, 375g jar","unitType":"PER_UNIT","price":9.80,"bay":"A2"}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.unitType").value("PER_UNIT"));

        mockMvc.perform(get("/api/products?keyword=oats")
                .header("Authorization", "Bearer " + coordinatorToken))
            .andExpect(jsonPath("$.data.total").value(1))
            .andExpect(jsonPath("$.data.records[0].bay").value("B1"));
    }

    @Test
    void priceAndNameCanBeUpdated() throws Exception {
        Long id = createProduct("Coffee beans, whole", UnitType.PER_KG, "32.00", "C2").getId();
        mockMvc.perform(put("/api/products/" + id)
                .header("Authorization", "Bearer " + coordinatorToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"name":"Coffee beans, ground","unitType":"PER_KG","price":34.00,"bay":"C2"}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.name").value("Coffee beans, ground"))
            .andExpect(jsonPath("$.data.price").value(34.00));
    }

    @Test
    void withdrawHidesProductFromActiveList() throws Exception {
        Long id = createProduct("Raw almonds", UnitType.PER_KG, "18.90", "C1").getId();
        mockMvc.perform(post("/api/products/" + id + "/withdraw")
                .header("Authorization", "Bearer " + coordinatorToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("WITHDRAWN"));

        mockMvc.perform(get("/api/products?status=ACTIVE")
                .header("Authorization", "Bearer " + coordinatorToken))
            .andExpect(jsonPath("$.data.total").value(0));
    }

    @Test
    void updateMissingProductReturns404() throws Exception {
        mockMvc.perform(put("/api/products/99999")
                .header("Authorization", "Bearer " + coordinatorToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"name":"Ghost","unitType":"PER_UNIT","price":1.00,"bay":"Z9"}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(404));
    }

    @Test
    void withdrawMissingProductReturns404() throws Exception {
        mockMvc.perform(post("/api/products/99999/withdraw")
                .header("Authorization", "Bearer " + coordinatorToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(404));
    }

    @Test
    void memberCannotManageProducts() throws Exception {
        String memberToken = tokenFor("M-094", "coop1234");
        mockMvc.perform(get("/api/products").header("Authorization", "Bearer " + memberToken))
            .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/products")
                .header("Authorization", "Bearer " + memberToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"name":"X","unitType":"PER_UNIT","price":1.00}
                    """))
            .andExpect(status().isForbidden());
    }

    @Test
    void memberCannotUpdateOrWithdrawProducts() throws Exception {
        Long id = createProduct("Honey", UnitType.PER_UNIT, "12.00", "A1").getId();
        String memberToken = tokenFor("M-094", "coop1234");
        mockMvc.perform(put("/api/products/" + id)
                .header("Authorization", "Bearer " + memberToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"name":"Honey","unitType":"PER_UNIT","price":13.00,"bay":"A1"}
                    """))
            .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/products/" + id + "/withdraw")
                .header("Authorization", "Bearer " + memberToken))
            .andExpect(status().isForbidden());
    }

    @Test
    void invalidPriceIsRejected() throws Exception {
        mockMvc.perform(post("/api/products")
                .header("Authorization", "Bearer " + coordinatorToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"name":"Bad","unitType":"PER_UNIT","price":0}
                    """))
            .andExpect(jsonPath("$.code").value(400));
    }
}
