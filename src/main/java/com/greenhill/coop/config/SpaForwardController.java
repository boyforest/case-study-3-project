package com.greenhill.coop.config;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class SpaForwardController {

    @GetMapping(value = {"/", "/login", "/shop", "/my-order", "/admin/members", "/admin/products",
        "/admin/rounds", "/admin/orders", "/admin/totals"})
    public String index() {
        return "forward:/index.html";
    }
}
