package com.greenhill.coop.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.greenhill.coop.common.BizException;
import com.greenhill.coop.common.PageResult;
import com.greenhill.coop.common.enums.ProductStatus;
import com.greenhill.coop.dto.ProductRequest;
import com.greenhill.coop.dto.ProductView;
import com.greenhill.coop.entity.Product;
import com.greenhill.coop.mapper.ProductMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductMapper productMapper;

    public PageResult<ProductView> page(String keyword, ProductStatus status, long page, long size) {
        LambdaQueryWrapper<Product> qw = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            qw.like(Product::getName, keyword);
        }
        if (status != null) {
            qw.eq(Product::getStatus, status);
        }
        qw.orderByAsc(Product::getName);
        Page<Product> result = productMapper.selectPage(new Page<>(page, size), qw);
        List<ProductView> views = result.getRecords().stream().map(ProductView::from).toList();
        return PageResult.of(views, result.getTotal(), result.getCurrent(), result.getSize());
    }

    public ProductView create(ProductRequest request) {
        Product product = new Product();
        apply(product, request);
        product.setStatus(ProductStatus.ACTIVE);
        productMapper.insert(product);
        return ProductView.from(product);
    }

    public ProductView update(Long id, ProductRequest request) {
        Product product = find(id);
        apply(product, request);
        productMapper.updateById(product);
        return ProductView.from(product);
    }

    public ProductView withdraw(Long id) {
        Product product = find(id);
        product.setStatus(ProductStatus.WITHDRAWN);
        productMapper.updateById(product);
        return ProductView.from(product);
    }

    private void apply(Product product, ProductRequest request) {
        product.setName(request.name());
        product.setUnitType(request.unitType());
        product.setPrice(request.price());
        product.setBay(request.bay());
    }

    private Product find(Long id) {
        Product product = productMapper.selectById(id);
        if (product == null) {
            throw BizException.notFound("Product not found");
        }
        return product;
    }
}
