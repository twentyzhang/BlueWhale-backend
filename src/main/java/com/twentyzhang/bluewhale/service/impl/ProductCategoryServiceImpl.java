package com.twentyzhang.bluewhale.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.twentyzhang.bluewhale.dto.CategoryNodeResponse;
import com.twentyzhang.bluewhale.dto.CreateCategoryRequest;
import com.twentyzhang.bluewhale.entity.ProductCategory;
import com.twentyzhang.bluewhale.mapper.ProductCategoryMapper;
import com.twentyzhang.bluewhale.service.ProductCategoryService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ProductCategoryServiceImpl extends ServiceImpl<ProductCategoryMapper, ProductCategory>
        implements ProductCategoryService {

    @Override
    public List<CategoryNodeResponse> getCategoryTree() {
        throw new UnsupportedOperationException("not implemented yet");
    }

    @Override
    public Long createCategory(CreateCategoryRequest request) {
        throw new UnsupportedOperationException("not implemented yet");
    }

    @Override
    public void deleteCategory(Long categoryId) {
        throw new UnsupportedOperationException("not implemented yet");
    }
}
