package com.twentyzhang.bluewhale.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.twentyzhang.bluewhale.dto.CreateProductRequest;
import com.twentyzhang.bluewhale.dto.ProductDetailResponse;
import com.twentyzhang.bluewhale.dto.ProductListItemResponse;
import com.twentyzhang.bluewhale.dto.UpdateProductRequest;
import com.twentyzhang.bluewhale.dto.UpdateStockRequest;
import com.twentyzhang.bluewhale.dto.UpdateStockResponse;
import com.twentyzhang.bluewhale.entity.Product;

import java.math.BigDecimal;

public interface ProductService extends IService<Product> {

    /**
     * 分页查询指定商店的商品列表，支持按关键词/分类/价格区间过滤。
     * 对应 GET /api/stores/{storeId}/products
     */
    IPage<ProductListItemResponse> getProductsByStore(Long storeId, String keyword, Long categoryId,
                                                      BigDecimal minPrice, BigDecimal maxPrice,
                                                      int page, int size);

    /**
     * 获取指定商品详情（含分类信息、所属商店、平均评分、评论数）。
     * 对应 GET /api/products/{productId}
     */
    ProductDetailResponse getProductById(Long productId);

    /**
     * 全平台商品搜索，支持关键词/分类/价格区间过滤和分页。
     * 对应 GET /api/products
     */
    IPage<ProductListItemResponse> searchProducts(String keyword, Long categoryId,
                                                  BigDecimal minPrice, BigDecimal maxPrice,
                                                  int page, int size);

    /**
     * 在指定商店新建商品。校验 storeId 与当前 Staff 归属商店一致。
     * 仅 Staff 可调用。
     * 对应 POST /api/stores/{storeId}/products
     *
     * @return 新建商品 ID
     */
    Long createProduct(Long storeId, CreateProductRequest request);

    /**
     * 修改商品信息（仅传入需要变更的字段）。校验商品归属商店权限。
     * 仅 Staff 可调用。
     * 对应 PUT /api/stores/{storeId}/products/{productId}
     */
    void updateProduct(Long storeId, Long productId, UpdateProductRequest request);

    /**
     * 逻辑删除指定商品。校验商品归属商店权限。
     * 仅 Staff 可调用。
     * 对应 DELETE /api/stores/{storeId}/products/{productId}
     */
    void deleteProduct(Long storeId, Long productId);

    /**
     * 更新商品库存。type=IN 表示入库（增加），type=OUT 表示出库（减少）。
     * 出库时需校验库存充足。仅 Staff 可调用。
     * 对应 PUT /api/stores/{storeId}/products/{productId}/stock
     */
    UpdateStockResponse updateStock(Long storeId, Long productId, UpdateStockRequest request);
}
