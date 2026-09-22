package com.greenhill.coop;

import com.greenhill.coop.auth.RequireCoordinator;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequireCoordinator
public class TestCoordinatorController {

    @GetMapping("/api/test/coordinator-only")
    public String coordinatorOnly() {
        return "ok";
    }
}
