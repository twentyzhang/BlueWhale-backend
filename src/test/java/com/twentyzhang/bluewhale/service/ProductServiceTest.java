package com.twentyzhang.bluewhale.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.twentyzhang.bluewhale.BaseServiceTest;
import com.twentyzhang.bluewhale.common.Result;
import com.twentyzhang.bluewhale.dto.*;
import com.twentyzhang.bluewhale.entity.*;
import com.twentyzhang.bluewhale.exception.BusinessException;
import com.twentyzhang.bluewhale.mapper.*;
import com.twentyzhang.bluewhale.service.impl.ProductServiceImpl;
import com.twentyzhang.bluewhale.util.AuthUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("ProductService")
class ProductServiceTest extends BaseServiceTest {

    @Mock private ProductMapper productMapper;
    @Mock private StoreMapper storeMapper;
    @Mock private ProductCategoryMapper productCategoryMapper;
    @Mock private ReviewMapper reviewMapper;
    // OrderItemMapper 是 deleteProduct 的构造器依赖，用户列表中漏列，此处补充
    @Mock private OrderItemMapper orderItemMapper;
    @Mock private OrderMapper orderMapper;

    @InjectMocks
    private ProductServiceImpl productService;

    // ── 消歧辅助方法（BaseMapper 3.5.9 重载问题，见 debug记录.md 第 1 条）─────────
    private static Product anyProduct() { return ArgumentMatchers.any(Product.class); }
    private static Serializable anyProductId() { return ArgumentMatchers.any(); }

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(productService, "baseMapper", productMapper);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // listStoreProducts
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("listStoreProducts")
    class ListStoreProducts {

        @Test
        @DisplayName("正常分页返回商品列表，categoryName 正确填充")
        void success() {
            when(storeMapper.selectById(1L)).thenReturn(store(1L, "南鲸旗舰店"));
            Product p = product(101L, 1L, 1L, "老陈醋", new BigDecimal("12.80"), 200);
            Page<Product> page = page(List.of(p), 1L);
            when(productMapper.selectPage(any(), any())).thenReturn(page);
            when(productCategoryMapper.selectBatchIds(any()))
                    .thenReturn(List.of(category(1L, "调味品")));

            IPage<ProductListItemResponse> result =
                    productService.getProductsByStore(1L, null, null, null, null, 1, 10);

            assertEquals(1L, result.getTotal());
            ProductListItemResponse item = result.getRecords().get(0);
            assertEquals("老陈醋", item.getName());
            assertEquals("调味品", item.getCategoryName());
            assertEquals(200, item.getStock());
        }

        @Test
        @DisplayName("商店不存在时抛出 code=404 的 BusinessException")
        void storeNotFound_throws404() {
            when(storeMapper.selectById(999L)).thenReturn(null);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> productService.getProductsByStore(999L, null, null, null, null, 1, 10));
            assertEquals(Result.CODE_NOT_FOUND, ex.getCode());
            verifyNoInteractions(productMapper);
        }

        @Test
        @DisplayName("传入 keyword 时，查询条件包含 LIKE")
        void withKeyword_wrapperContainsLike() {
            when(storeMapper.selectById(1L)).thenReturn(store(1L, "南鲸旗舰店"));
            when(productMapper.selectPage(any(), any())).thenReturn(page(List.of(), 0L));
            when(productCategoryMapper.selectBatchIds(any())).thenReturn(List.of());

            productService.getProductsByStore(1L, "醋", null, null, null, 1, 10);

            @SuppressWarnings("unchecked")
            ArgumentCaptor<Wrapper<Product>> captor = ArgumentCaptor.forClass(Wrapper.class);
            verify(productMapper).selectPage(any(), captor.capture());
            String sql = captor.getValue().getSqlSegment();
            assertNotNull(sql);
            assertTrue(sql.toUpperCase().contains("LIKE"),
                    "传入 keyword 时 SQL 应包含 LIKE，实际 SQL: " + sql);
        }

        @Test
        @DisplayName("传入 minPrice/maxPrice 时，查询条件包含价格区间")
        void withPriceRange_wrapperContainsPriceConditions() {
            when(storeMapper.selectById(1L)).thenReturn(store(1L, "南鲸旗舰店"));
            when(productMapper.selectPage(any(), any())).thenReturn(page(List.of(), 0L));
            when(productCategoryMapper.selectBatchIds(any())).thenReturn(List.of());

            productService.getProductsByStore(1L, null, null,
                    new BigDecimal("5.00"), new BigDecimal("50.00"), 1, 10);

            @SuppressWarnings("unchecked")
            ArgumentCaptor<Wrapper<Product>> captor = ArgumentCaptor.forClass(Wrapper.class);
            verify(productMapper).selectPage(any(), captor.capture());
            String sql = captor.getValue().getSqlSegment();
            assertNotNull(sql);
            assertTrue(sql.contains(">="), "应包含 >= 条件，实际 SQL: " + sql);
            assertTrue(sql.contains("<="), "应包含 <= 条件，实际 SQL: " + sql);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // getProductDetail
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("getProductDetail")
    class GetProductDetail {

        @Test
        @DisplayName("正常返回详情，averageRating 和 reviewCount 计算正确")
        void success_verifyRatingAndCount() {
            Product p = product(101L, 1L, 2L, "老陈醋", new BigDecimal("12.80"), 200);
            when(productMapper.selectById(101L)).thenReturn(p);
            when(storeMapper.selectById(1L)).thenReturn(store(1L, "南鲸旗舰店"));
            when(productCategoryMapper.selectById(2L)).thenReturn(category(2L, "调味品"));
            when(productCategoryMapper.selectById(null)).thenReturn(null); // 无父分类
            // 两条评论：4 分和 5 分
            Review r1 = Review.builder().rating(4).productId(101L).build();
            Review r2 = Review.builder().rating(5).productId(101L).build();
            when(reviewMapper.selectList(any())).thenReturn(List.of(r1, r2));

            ProductDetailResponse resp = productService.getProductById(101L);

            assertEquals(101L, resp.getId());
            assertEquals("南鲸旗舰店", resp.getStoreName());
            assertEquals("调味品", resp.getCategory().getName());
            assertEquals(2, resp.getReviewCount());
            assertEquals(4.5, resp.getAverageRating(), 0.001);
        }

        @Test
        @DisplayName("无评论时 averageRating 为 null，reviewCount 为 0")
        void noReviews_ratingIsNull() {
            when(productMapper.selectById(101L)).thenReturn(
                    product(101L, 1L, null, "老陈醋", new BigDecimal("12.80"), 200));
            when(storeMapper.selectById(1L)).thenReturn(store(1L, "南鲸旗舰店"));
            when(reviewMapper.selectList(any())).thenReturn(List.of());

            ProductDetailResponse resp = productService.getProductById(101L);

            assertEquals(0, resp.getReviewCount());
            assertNull(resp.getAverageRating());
        }

        @Test
        @DisplayName("商品不存在时抛出 code=404 的 BusinessException")
        void notFound_throws404() {
            when(productMapper.selectById(999L)).thenReturn(null);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> productService.getProductById(999L));
            assertEquals(Result.CODE_NOT_FOUND, ex.getCode());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // searchProducts
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("searchProducts")
    class SearchProducts {

        @Test
        @DisplayName("带 keyword 和 categoryId 的搜索正常返回")
        void success_withFilters() {
            Product p = product(101L, 1L, 1L, "老陈醋", new BigDecimal("12.80"), 200);
            when(productMapper.selectPage(any(), any())).thenReturn(page(List.of(p), 1L));
            when(productCategoryMapper.selectBatchIds(any()))
                    .thenReturn(List.of(category(1L, "调味品")));

            IPage<ProductListItemResponse> result =
                    productService.searchProducts("醋", 1L, null, null, 1, 10);

            assertEquals(1L, result.getTotal());
            assertEquals("老陈醋", result.getRecords().get(0).getName());
        }

        @Test
        @DisplayName("keyword 和 categoryId 均为空时返回全部商品（不抛异常）")
        void noFilters_returnsAll() {
            when(productMapper.selectPage(any(), any())).thenReturn(page(List.of(), 0L));
            when(productCategoryMapper.selectBatchIds(any())).thenReturn(List.of());

            assertDoesNotThrow(() ->
                    productService.searchProducts(null, null, null, null, 1, 10));
            verify(productMapper).selectPage(any(), any());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // createProduct
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("createProduct")
    class CreateProduct {

        @Test
        @DisplayName("Staff 正常创建商品，返回新商品 id")
        void success() {
            mockAuthUser(1L, AuthUtil.ROLE_STAFF, 1L);

            CreateProductRequest req = new CreateProductRequest();
            req.setName("老陈醋");
            req.setPrice(new BigDecimal("12.80"));
            req.setStock(200);
            req.setCategoryId(1L);

            when(productCategoryMapper.selectById(1L)).thenReturn(category(1L, "调味品"));
            when(productMapper.insert(anyProduct())).thenAnswer(inv -> {
                ((Product) inv.getArgument(0)).setId(101L);
                return 1;
            });

            Long id = productService.createProduct(1L, req);

            assertEquals(101L, id);
            ArgumentCaptor<Product> captor = ArgumentCaptor.forClass(Product.class);
            verify(productMapper).insert((Product) captor.capture());
            assertEquals(1L,  captor.getValue().getStoreId());
            assertEquals("老陈醋", captor.getValue().getName());
        }

        @Test
        @DisplayName("非 Staff 调用时抛出 code=403 的 BusinessException")
        void notStaff_throws403() {
            mockAuthUser(1L, AuthUtil.ROLE_CUSTOMER, null);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> productService.createProduct(1L, new CreateProductRequest()));
            assertEquals(Result.CODE_FORBIDDEN, ex.getCode());
        }

        @Test
        @DisplayName("Staff 操作其他店铺时抛出 code=403 的 BusinessException")
        void wrongStore_throws403() {
            mockAuthUser(1L, AuthUtil.ROLE_STAFF, 2L); // Staff 属于 store 2，操作 store 1

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> productService.createProduct(1L, new CreateProductRequest()));
            assertEquals(Result.CODE_FORBIDDEN, ex.getCode());
        }

        @Test
        @DisplayName("categoryId 不存在时抛出 code=404 的 BusinessException")
        void categoryNotFound_throws404() {
            mockAuthUser(1L, AuthUtil.ROLE_STAFF, 1L);

            CreateProductRequest req = new CreateProductRequest();
            req.setName("老陈醋");
            req.setPrice(new BigDecimal("12.80"));
            req.setStock(200);
            req.setCategoryId(999L);

            when(productCategoryMapper.selectById(999L)).thenReturn(null);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> productService.createProduct(1L, req));
            assertEquals(Result.CODE_NOT_FOUND, ex.getCode());
            verify(productMapper, never()).insert(anyProduct());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // updateProduct
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("updateProduct")
    class UpdateProduct {

        @Test
        @DisplayName("只更新传入的字段，未传字段保持原值")
        void success_partialUpdate() {
            mockAuthUser(1L, AuthUtil.ROLE_STAFF, 1L);

            Product existing = product(101L, 1L, 1L, "旧名", new BigDecimal("10.00"), 100);
            when(productMapper.selectById(101L)).thenReturn(existing);
            when(productMapper.updateById(anyProduct())).thenReturn(1);

            UpdateProductRequest req = new UpdateProductRequest();
            req.setName("新名"); // 只更新名称

            productService.updateProduct(1L, 101L, req);

            ArgumentCaptor<Product> captor = ArgumentCaptor.forClass(Product.class);
            verify(productMapper).updateById((Product) captor.capture());
            assertEquals("新名",             captor.getValue().getName());
            assertEquals(new BigDecimal("10.00"), captor.getValue().getPrice()); // 未修改
            assertEquals(1L,                 captor.getValue().getCategoryId()); // 未修改
        }

        @Test
        @DisplayName("商品不存在时抛出 code=404 的 BusinessException")
        void notFound_throws404() {
            mockAuthUser(1L, AuthUtil.ROLE_STAFF, 1L);

            when(productMapper.selectById(999L)).thenReturn(null);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> productService.updateProduct(1L, 999L, new UpdateProductRequest()));
            assertEquals(Result.CODE_NOT_FOUND, ex.getCode());
            verify(productMapper, never()).updateById(anyProduct());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // deleteProduct
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("deleteProduct")
    class DeleteProduct {

        @Test
        @DisplayName("无未完成订单时正常删除")
        void success_noActiveOrders() {
            mockAuthUser(1L, AuthUtil.ROLE_STAFF, 1L);

            when(productMapper.selectById(101L)).thenReturn(product(101L, 1L, 1L, "老陈醋", null, 100));
            when(orderItemMapper.selectList(any())).thenReturn(List.of());
            when(productMapper.deleteById((Serializable) 101L)).thenReturn(1);

            assertDoesNotThrow(() -> productService.deleteProduct(1L, 101L));
            verify(productMapper).deleteById((Serializable) 101L);
            // 没有 orderItems，不应查 orderMapper
            verifyNoInteractions(orderMapper);
        }

        @Test
        @DisplayName("存在未完成订单时抛出 BusinessException")
        void activeOrders_throwsBusinessException() {
            mockAuthUser(1L, AuthUtil.ROLE_STAFF, 1L);

            when(productMapper.selectById(101L)).thenReturn(product(101L, 1L, 1L, "老陈醋", null, 100));
            OrderItem item = OrderItem.builder().orderId(500L).productId(101L).build();
            when(orderItemMapper.selectList(any())).thenReturn(List.of(item));
            when(orderMapper.selectCount(any())).thenReturn(2L); // 2 个活跃订单

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> productService.deleteProduct(1L, 101L));
            assertTrue(ex.getMessage().contains("2"));
            assertTrue(ex.getMessage().contains("未完成"));
            verify(productMapper, never()).deleteById(anyProductId());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // updateStock
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("updateStock")
    class UpdateStock {

        @Test
        @DisplayName("IN 操作：库存正确增加")
        void stockIn_increasesStock() {
            mockAuthUser(1L, AuthUtil.ROLE_STAFF, 1L);
            when(productMapper.selectById(101L)).thenReturn(product(101L, 1L, 1L, "老陈醋", null, 100));
            when(productMapper.updateById(anyProduct())).thenReturn(1);

            UpdateStockRequest req = new UpdateStockRequest();
            req.setDelta(50);
            req.setType("IN");

            UpdateStockResponse resp = productService.updateStock(1L, 101L, req);

            assertEquals(150, resp.getCurrentStock());
        }

        @Test
        @DisplayName("OUT 操作：库存正确减少")
        void stockOut_decreasesStock() {
            mockAuthUser(1L, AuthUtil.ROLE_STAFF, 1L);
            when(productMapper.selectById(101L)).thenReturn(product(101L, 1L, 1L, "老陈醋", null, 100));
            when(productMapper.updateById(anyProduct())).thenReturn(1);

            UpdateStockRequest req = new UpdateStockRequest();
            req.setDelta(30);
            req.setType("OUT");

            UpdateStockResponse resp = productService.updateStock(1L, 101L, req);

            assertEquals(70, resp.getCurrentStock());
        }

        @Test
        @DisplayName("OUT 操作库存不足时抛出 BusinessException")
        void stockInsufficient_throwsBusinessException() {
            mockAuthUser(1L, AuthUtil.ROLE_STAFF, 1L);
            when(productMapper.selectById(101L)).thenReturn(product(101L, 1L, 1L, "老陈醋", null, 10));

            UpdateStockRequest req = new UpdateStockRequest();
            req.setDelta(50); // 出库 50，但库存只有 10
            req.setType("OUT");

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> productService.updateStock(1L, 101L, req));
            assertTrue(ex.getMessage().contains("10")); // 当前库存
            assertTrue(ex.getMessage().contains("50")); // 出库数量
            verify(productMapper, never()).updateById(anyProduct());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 私有辅助方法
    // ─────────────────────────────────────────────────────────────────────────

    private static Product product(Long id, Long storeId, Long categoryId,
                                   String name, BigDecimal price, Integer stock) {
        return Product.builder()
                .id(id).storeId(storeId).categoryId(categoryId)
                .name(name).price(price).stock(stock).build();
    }

    private static Store store(Long id, String name) {
        return Store.builder().id(id).name(name).build();
    }

    private static ProductCategory category(Long id, String name) {
        return ProductCategory.builder().id(id).name(name).build();
    }

    private static Page<Product> page(List<Product> records, long total) {
        Page<Product> p = new Page<>(1, 10);
        p.setRecords(records);
        p.setTotal(total);
        return p;
    }
}
