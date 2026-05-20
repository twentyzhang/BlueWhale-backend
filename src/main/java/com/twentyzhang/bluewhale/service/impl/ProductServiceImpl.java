package com.twentyzhang.bluewhale.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.twentyzhang.bluewhale.dto.CreateProductRequest;
import com.twentyzhang.bluewhale.dto.ProductDetailResponse;
import com.twentyzhang.bluewhale.dto.ProductListItemResponse;
import com.twentyzhang.bluewhale.dto.UpdateProductRequest;
import com.twentyzhang.bluewhale.dto.UpdateStockRequest;
import com.twentyzhang.bluewhale.dto.UpdateStockResponse;
import com.twentyzhang.bluewhale.entity.Product;
import com.twentyzhang.bluewhale.mapper.ProductMapper;
import com.twentyzhang.bluewhale.service.ProductService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
public class ProductServiceImpl extends ServiceImpl<ProductMapper, Product> implements ProductService {

    @Override
    public IPage<ProductListItemResponse> getProductsByStore(Long storeId, String keyword, Long categoryId,
                                                             BigDecimal minPrice, BigDecimal maxPrice,
                                                             int page, int size) {
        throw new UnsupportedOperationException("not implemented yet");
    }

    @Override
    public ProductDetailResponse getProductById(Long productId) {
        throw new UnsupportedOperationException("not implemented yet");
    }

    @Override
    public IPage<ProductListItemResponse> searchProducts(String keyword, Long categoryId,
                                                         BigDecimal minPrice, BigDecimal maxPrice,
                                                         int page, int size) {
        throw new UnsupportedOperationException("not implemented yet");
    }

    @Override
    public Long createProduct(Long storeId, CreateProductRequest request) {
        throw new UnsupportedOperationException("not implemented yet");
    }

    @Override
    public void updateProduct(Long storeId, Long productId, UpdateProductRequest request) {
        throw new UnsupportedOperationException("not implemented yet");
    }

    @Override
    public void deleteProduct(Long storeId, Long productId) {
        throw new UnsupportedOperationException("not implemented yet");
    }

    @Override
    public UpdateStockResponse updateStock(Long storeId, Long productId, UpdateStockRequest request) {
        throw new UnsupportedOperationException("not implemented yet");
    }
}
