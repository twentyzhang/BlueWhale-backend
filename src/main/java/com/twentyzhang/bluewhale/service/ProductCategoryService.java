package com.twentyzhang.bluewhale.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.twentyzhang.bluewhale.dto.CategoryNodeResponse;
import com.twentyzhang.bluewhale.dto.CreateCategoryRequest;
import com.twentyzhang.bluewhale.entity.ProductCategory;

import java.util.List;

public interface ProductCategoryService extends IService<ProductCategory> {

    /**
     * 查询所有分类并在内存中递归组装为树形结构。
     * 对应 GET /api/categories
     */
    List<CategoryNodeResponse> getCategoryTree();

    /**
     * 新建商品分类。parentId 为 null 时创建顶级分类，否则作为指定分类的子分类。
     * Staff 或 Admin 可调用。
     * 对应 POST /api/categories
     *
     * @return 新建分类 ID
     */
    Long createCategory(CreateCategoryRequest request);

    /**
     * 逻辑删除指定分类。若分类下仍有商品或子分类，应抛出业务异常。
     * 仅 Admin 可调用。
     * 对应 DELETE /api/categories/{categoryId}
     */
    void deleteCategory(Long categoryId);
}
