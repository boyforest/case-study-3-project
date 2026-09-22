package com.greenhill.coop.controller;

import com.greenhill.coop.auth.RequireCoordinator;
import com.greenhill.coop.common.PageResult;
import com.greenhill.coop.common.Result;
import com.greenhill.coop.common.enums.ProductStatus;
import com.greenhill.coop.dto.ProductRequest;
import com.greenhill.coop.dto.ProductView;
import com.greenhill.coop.service.ProductService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
@RequireCoordinator
public class ProductController {

    private final ProductService productService;

    @GetMapping
    public Result<PageResult<ProductView>> page(@RequestParam(required = false) String keyword,
                                                @RequestParam(required = false) ProductStatus status,
                                                @RequestParam(defaultValue = "1") long page,
                                                @RequestParam(defaultValue = "10") long size) {
        return Result.success(productService.page(keyword, status, page, size));
    }

    @PostMapping
    public Result<ProductView> create(@Valid @RequestBody ProductRequest request) {
        return Result.success(productService.create(request));
    }

    @PutMapping("/{id}")
    public Result<ProductView> update(@PathVariable Long id, @Valid @RequestBody ProductRequest request) {
        return Result.success(productService.update(id, request));
    }

    @PostMapping("/{id}/withdraw")
    public Result<ProductView> withdraw(@PathVariable Long id) {
        return Result.success(productService.withdraw(id));
    }
}
