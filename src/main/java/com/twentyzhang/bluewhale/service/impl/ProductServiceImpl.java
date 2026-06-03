package com.twentyzhang.bluewhale.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.twentyzhang.bluewhale.common.Result;
import com.twentyzhang.bluewhale.dto.CreateProductRequest;
import com.twentyzhang.bluewhale.dto.ProductCategoryInfo;
import com.twentyzhang.bluewhale.dto.ProductDetailResponse;
import com.twentyzhang.bluewhale.dto.ProductListItemResponse;
import com.twentyzhang.bluewhale.dto.UpdateProductRequest;
import com.twentyzhang.bluewhale.dto.UpdateStockRequest;
import com.twentyzhang.bluewhale.dto.UpdateStockResponse;
import com.twentyzhang.bluewhale.entity.Order;
import com.twentyzhang.bluewhale.entity.OrderItem;
import com.twentyzhang.bluewhale.entity.Product;
import com.twentyzhang.bluewhale.entity.ProductCategory;
import com.twentyzhang.bluewhale.entity.Review;
import com.twentyzhang.bluewhale.entity.Store;
import com.twentyzhang.bluewhale.exception.BusinessException;
import com.twentyzhang.bluewhale.mapper.OrderItemMapper;
import com.twentyzhang.bluewhale.mapper.OrderMapper;
import com.twentyzhang.bluewhale.mapper.ProductCategoryMapper;
import com.twentyzhang.bluewhale.mapper.ProductMapper;
import com.twentyzhang.bluewhale.mapper.ReviewMapper;
import com.twentyzhang.bluewhale.mapper.StoreMapper;
import com.twentyzhang.bluewhale.service.ProductService;
import com.twentyzhang.bluewhale.util.AuthUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalDouble;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProductServiceImpl extends ServiceImpl<ProductMapper, Product> implements ProductService {

    private final StoreMapper storeMapper;
    private final ProductCategoryMapper productCategoryMapper;
    private final ReviewMapper reviewMapper;
    private final OrderItemMapper orderItemMapper;
    private final OrderMapper orderMapper;

    private static final List<String> ACTIVE_ORDER_STATUSES =
            List.of("PENDING_PAYMENT", "PAID", "SHIPPED");

    // ── 1. listStoreProducts ──────────────────────────────────────────────────
    @Override
    public IPage<ProductListItemResponse> getProductsByStore(Long storeId, String keyword, Long categoryId,
                                                             BigDecimal minPrice, BigDecimal maxPrice,
                                                             int page, int size) {
        if (storeMapper.selectById(storeId) == null) {
            throw new BusinessException(Result.CODE_NOT_FOUND, "商店不存在");
        }

        LambdaQueryWrapper<Product> wrapper = buildProductFilter(keyword, categoryId, minPrice, maxPrice)
                .eq(Product::getStoreId, storeId);

        IPage<Product> productPage = this.page(new Page<>(page, size), wrapper);
        return toListResponse(productPage);
    }

    // ── 2. getProductDetail ───────────────────────────────────────────────────
    @Override
    public ProductDetailResponse getProductById(Long productId) {
        Product product = getById(productId);
        if (product == null) {
            throw new BusinessException(Result.CODE_NOT_FOUND, "商品不存在");
        }

        // 查商店名称
        Store store = storeMapper.selectById(product.getStoreId());

        // 查分类（一次），查父分类（有需要时再查一次）
        ProductCategory category = product.getCategoryId() != null
                ? productCategoryMapper.selectById(product.getCategoryId()) : null;
        ProductCategory parent = (category != null && category.getParentId() != null)
                ? productCategoryMapper.selectById(category.getParentId()) : null;

        // 查一级评论（parentId IS NULL）：count + 平均评分
        List<Review> topReviews = reviewMapper.selectList(
                new LambdaQueryWrapper<Review>()
                        .select(Review::getRating)       // 只取 rating 列，减少数据传输
                        .eq(Review::getProductId, productId)
                        .isNull(Review::getParentId));

        int reviewCount = topReviews.size();
        OptionalDouble avg = topReviews.stream()
                .filter(r -> r.getRating() != null)
                .mapToInt(Review::getRating)
                .average();
        Double averageRating = avg.isPresent() ? avg.getAsDouble() : null;

        return ProductDetailResponse.builder()
                .id(product.getId())
                .name(product.getName())
                .price(product.getPrice())
                .stock(product.getStock())
                .imageUrl(product.getImageUrl())
                .category(category == null ? null : ProductCategoryInfo.builder()
                        .id(category.getId())
                        .name(category.getName())
                        .parentId(category.getParentId())
                        .parentName(parent != null ? parent.getName() : null)
                        .build())
                .storeId(product.getStoreId())
                .storeName(store != null ? store.getName() : null)
                .averageRating(averageRating)
                .reviewCount(reviewCount)
                .build();
    }

    // ── 3. searchProducts ─────────────────────────────────────────────────────
    @Override
    public IPage<ProductListItemResponse> searchProducts(String keyword, Long categoryId,
                                                         BigDecimal minPrice, BigDecimal maxPrice,
                                                         int page, int size) {
        LambdaQueryWrapper<Product> wrapper = buildProductFilter(keyword, categoryId, minPrice, maxPrice);
        IPage<Product> productPage = this.page(new Page<>(page, size), wrapper);
        return toListResponse(productPage);
    }

    // ── 4. createProduct ─────────────────────────────────────────────────────
    @Override
    public Long createProduct(Long storeId, CreateProductRequest request) {
        AuthUtil.requireRole(AuthUtil.ROLE_STAFF);
        AuthUtil.requireStoreAccess(storeId);

        if (productCategoryMapper.selectById(request.getCategoryId()) == null) {
            throw new BusinessException(Result.CODE_NOT_FOUND, "商品分类不存在");
        }

        Product product = Product.builder()
                .storeId(storeId)
                .name(request.getName())
                .price(request.getPrice())
                .stock(request.getStock())
                .categoryId(request.getCategoryId())
                .imageUrl(request.getImageUrl())
                .build();
        save(product);
        return product.getId();
    }

    // ── 5. updateProduct ─────────────────────────────────────────────────────
    @Override
    public void updateProduct(Long storeId, Long productId, UpdateProductRequest request) {
        AuthUtil.requireRole(AuthUtil.ROLE_STAFF);
        AuthUtil.requireStoreAccess(storeId);

        Product product = getById(productId);
        if (product == null || !storeId.equals(product.getStoreId())) {
            throw new BusinessException(Result.CODE_NOT_FOUND, "商品不存在");
        }

        if (request.getCategoryId() != null
                && productCategoryMapper.selectById(request.getCategoryId()) == null) {
            throw new BusinessException(Result.CODE_NOT_FOUND, "商品分类不存在");
        }

        if (request.getName()       != null) product.setName(request.getName());
        if (request.getPrice()      != null) product.setPrice(request.getPrice());
        if (request.getCategoryId() != null) product.setCategoryId(request.getCategoryId());
        if (request.getImageUrl()   != null) product.setImageUrl(request.getImageUrl());

        updateById(product);
    }

    // ── 6. deleteProduct ─────────────────────────────────────────────────────
    @Override
    @Transactional
    public void deleteProduct(Long storeId, Long productId) {
        AuthUtil.requireRole(AuthUtil.ROLE_STAFF);
        AuthUtil.requireStoreAccess(storeId);

        Product product = getById(productId);
        if (product == null || !storeId.equals(product.getStoreId())) {
            throw new BusinessException(Result.CODE_NOT_FOUND, "商品不存在");
        }

        // 查该商品所在的所有订单 ID（二次查询模式）
        List<Long> orderIds = orderItemMapper
                .selectList(new LambdaQueryWrapper<OrderItem>()
                        .select(OrderItem::getOrderId)
                        .eq(OrderItem::getProductId, productId))
                .stream()
                .map(OrderItem::getOrderId)
                .distinct()
                .collect(Collectors.toList());

        if (!orderIds.isEmpty()) {
            long activeCount = orderMapper.selectCount(
                    new LambdaQueryWrapper<Order>()
                            .in(Order::getId, orderIds)
                            .in(Order::getStatus, ACTIVE_ORDER_STATUSES));
            if (activeCount > 0) {
                throw new BusinessException(
                        "该商品存在 " + activeCount + " 个未完成的订单（待付款/已付款/已发货），无法删除");
            }
        }

        removeById(productId);
    }

    // ── 7. updateStock ────────────────────────────────────────────────────────
    @Override
    public UpdateStockResponse updateStock(Long storeId, Long productId, UpdateStockRequest request) {
        AuthUtil.requireRole(AuthUtil.ROLE_STAFF);
        AuthUtil.requireStoreAccess(storeId);

        Product product = getById(productId);
        if (product == null || !storeId.equals(product.getStoreId())) {
            throw new BusinessException(Result.CODE_NOT_FOUND, "商品不存在");
        }

        int newStock;
        if ("IN".equals(request.getType())) {
            newStock = product.getStock() + request.getDelta();
        } else {
            if (product.getStock() < request.getDelta()) {
                throw new BusinessException(
                        "库存不足，当前库存 " + product.getStock() + "，出库数量 " + request.getDelta());
            }
            newStock = product.getStock() - request.getDelta();
        }

        product.setStock(newStock);
        updateById(product);
        return UpdateStockResponse.builder().currentStock(newStock).build();
    }

    // ── 私有辅助方法 ──────────────────────────────────────────────────────────

    /**
     * 构建通用商品过滤条件（keyword / categoryId / minPrice / maxPrice），
     * 各条件均为可选，为 null / blank 时自动跳过。
     */
    private LambdaQueryWrapper<Product> buildProductFilter(String keyword, Long categoryId,
                                                           BigDecimal minPrice, BigDecimal maxPrice) {
        return new LambdaQueryWrapper<Product>()
                .like(keyword != null && !keyword.isBlank(), Product::getName, keyword)
                .eq(categoryId != null, Product::getCategoryId, categoryId)
                .ge(minPrice != null, Product::getPrice, minPrice)
                .le(maxPrice != null, Product::getPrice, maxPrice);
    }

    /**
     * 将商品分页结果转为列表响应 VO。
     * 采用"二次查询"模式：批量拉取本页商品涉及的分类，
     * 避免逐条 selectById 产生 N+1 查询。（参见决策文档第 16 条）
     */
    private IPage<ProductListItemResponse> toListResponse(IPage<Product> productPage) {
        List<Product> products = productPage.getRecords();
        Map<Long, String> categoryNameMap = buildCategoryNameMap(products);

        return productPage.convert(p -> ProductListItemResponse.builder()
                .id(p.getId())
                .name(p.getName())
                .price(p.getPrice())
                .stock(p.getStock())
                .imageUrl(p.getImageUrl())
                .categoryName(categoryNameMap.get(p.getCategoryId()))
                .build());
    }

    /** 从商品列表中提取 categoryId，批量查分类，构建 id→name 映射。 */
    private Map<Long, String> buildCategoryNameMap(List<Product> products) {
        List<Long> categoryIds = products.stream()
                .map(Product::getCategoryId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(java.util.stream.Collectors.toList());

        if (categoryIds.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<Long, String> map = new HashMap<>();
        productCategoryMapper.selectBatchIds(categoryIds)
                .forEach(c -> map.put(c.getId(), c.getName()));
        return map;
    }
}
