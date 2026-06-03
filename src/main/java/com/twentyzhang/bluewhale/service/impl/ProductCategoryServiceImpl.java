package com.twentyzhang.bluewhale.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.twentyzhang.bluewhale.common.Result;
import com.twentyzhang.bluewhale.dto.CategoryNodeResponse;
import com.twentyzhang.bluewhale.dto.CreateCategoryRequest;
import com.twentyzhang.bluewhale.entity.Product;
import com.twentyzhang.bluewhale.entity.ProductCategory;
import com.twentyzhang.bluewhale.exception.BusinessException;
import com.twentyzhang.bluewhale.mapper.ProductCategoryMapper;
import com.twentyzhang.bluewhale.mapper.ProductMapper;
import com.twentyzhang.bluewhale.service.ProductCategoryService;
import com.twentyzhang.bluewhale.util.AuthUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ProductCategoryServiceImpl extends ServiceImpl<ProductCategoryMapper, ProductCategory>
        implements ProductCategoryService {

    private final ProductMapper productMapper;

    // ── 1. getCategoryTree ────────────────────────────────────────────────────
    @Override
    public List<CategoryNodeResponse> getCategoryTree() {
        List<ProductCategory> all = list();

        // 第一步：将所有分类转为 VO，建立 id → node 的索引 Map
        Map<Long, CategoryNodeResponse> nodeMap = new HashMap<>(all.size() * 2);
        for (ProductCategory c : all) {
            nodeMap.put(c.getId(), CategoryNodeResponse.builder()
                    .id(c.getId())
                    .name(c.getName())
                    .parentId(c.getParentId())
                    .children(new ArrayList<>())
                    .build());
        }

        // 第二步：单次遍历，按 parentId 将节点挂到父节点的 children 或加入根列表
        List<CategoryNodeResponse> roots = new ArrayList<>();
        for (ProductCategory c : all) {
            CategoryNodeResponse node = nodeMap.get(c.getId());
            if (c.getParentId() == null) {
                roots.add(node);
            } else {
                CategoryNodeResponse parent = nodeMap.get(c.getParentId());
                if (parent != null) {
                    parent.getChildren().add(node);
                } else {
                    roots.add(node); // 父节点已被删除时降级为根节点
                }
            }
        }
        return roots;
    }

    // ── 2. createCategory ─────────────────────────────────────────────────────
    @Override
    public Long createCategory(CreateCategoryRequest request) {
        AuthUtil.requireRole(AuthUtil.ROLE_STAFF, AuthUtil.ROLE_ADMIN);

        // 验证父分类是否存在
        if (request.getParentId() != null && getById(request.getParentId()) == null) {
            throw new BusinessException(Result.CODE_NOT_FOUND, "父分类不存在");
        }

        // 同一父级下不允许存在同名分类
        LambdaQueryWrapper<ProductCategory> dupCheck = new LambdaQueryWrapper<ProductCategory>()
                .eq(ProductCategory::getName, request.getName());
        if (request.getParentId() == null) {
            dupCheck.isNull(ProductCategory::getParentId);
        } else {
            dupCheck.eq(ProductCategory::getParentId, request.getParentId());
        }
        if (count(dupCheck) > 0) {
            throw new BusinessException("该父级下已存在同名分类");
        }

        ProductCategory category = ProductCategory.builder()
                .name(request.getName())
                .parentId(request.getParentId())
                .build();
        save(category);
        return category.getId();
    }

    // ── 3. deleteCategory ─────────────────────────────────────────────────────
    @Override
    public void deleteCategory(Long categoryId) {
        AuthUtil.requireRole(AuthUtil.ROLE_ADMIN);

        if (getById(categoryId) == null) {
            throw new BusinessException(Result.CODE_NOT_FOUND, "分类不存在");
        }

        // 禁止删除有子分类的节点
        long childCount = count(new LambdaQueryWrapper<ProductCategory>()
                .eq(ProductCategory::getParentId, categoryId));
        if (childCount > 0) {
            throw new BusinessException("该分类下存在 " + childCount + " 个子分类，请先删除子分类");
        }

        // 禁止删除有商品的分类
        long productCount = productMapper.selectCount(
                new LambdaQueryWrapper<Product>().eq(Product::getCategoryId, categoryId));
        if (productCount > 0) {
            throw new BusinessException("该分类下存在 " + productCount + " 件商品，请先移除或更换商品分类");
        }

        removeById(categoryId);
    }
}
