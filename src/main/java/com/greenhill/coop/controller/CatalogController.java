package com.greenhill.coop.controller;

import com.greenhill.coop.common.Result;
import com.greenhill.coop.dto.AvailableProductsView;
import com.greenhill.coop.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class CatalogController {

    private final ProductService productService;

    @GetMapping("/available")
    public Result<AvailableProductsView> available() {
        return Result.success(productService.available());
    }
}
