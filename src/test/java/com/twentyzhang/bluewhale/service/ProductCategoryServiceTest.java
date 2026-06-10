package com.twentyzhang.bluewhale.service;

import com.twentyzhang.bluewhale.BaseServiceTest;
import com.twentyzhang.bluewhale.common.Result;
import com.twentyzhang.bluewhale.dto.CategoryNodeResponse;
import com.twentyzhang.bluewhale.dto.CreateCategoryRequest;
import com.twentyzhang.bluewhale.entity.ProductCategory;
import com.twentyzhang.bluewhale.exception.BusinessException;
import com.twentyzhang.bluewhale.mapper.ProductCategoryMapper;
import com.twentyzhang.bluewhale.mapper.ProductMapper;
import com.twentyzhang.bluewhale.service.impl.ProductCategoryServiceImpl;
import com.twentyzhang.bluewhale.util.AuthUtil;
import com.twentyzhang.bluewhale.util.CacheKeys;
import com.twentyzhang.bluewhale.util.CacheUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import java.util.function.Supplier;
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
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("ProductCategoryService")
class ProductCategoryServiceTest extends BaseServiceTest {

    @Mock
    private ProductCategoryMapper productCategoryMapper;

    @Mock
    private ProductMapper productMapper;

    @Mock
    private CacheUtil cacheUtil;

    @InjectMocks
    private ProductCategoryServiceImpl categoryService;

    // ── 消歧辅助方法（详见 docs/debug记录.md 第 1 条） ────────────────────────
    /** insert(T) vs insert(Collection<T>) */
    private static ProductCategory anyCategory() {
        return ArgumentMatchers.any(ProductCategory.class);
    }
    /** deleteById(Serializable) vs deleteById(T)：用 (Serializable) 转型消歧 */
    private static Serializable anyId() {
        return ArgumentMatchers.any();
    }

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(categoryService, "baseMapper", productCategoryMapper);
        // CacheUtil 透传：执行 loader 并返回其结果（隔离缓存层，验证回源逻辑本身）
        lenient().when(cacheUtil.getOrLoad(anyString(), anyLong(), anyLong(),
                        any(Supplier.class), any(TypeReference.class)))
                .thenAnswer(inv -> ((Supplier<?>) inv.getArgument(3)).get());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // getCategoryTree
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("getCategoryTree")
    class GetCategoryTree {

        @Test
        @DisplayName("正常返回树形结构，验证父子关系组装正确")
        void success_buildsTreeCorrectly() {
            // 食品(1) → 饮料(3)、零食(4)；电子(2) → 无子节点
            List<ProductCategory> flat = List.of(
                    cat(1L, null, "食品"),
                    cat(2L, null, "电子"),
                    cat(3L, 1L,   "饮料"),
                    cat(4L, 1L,   "零食")
            );
            when(productCategoryMapper.selectList(any())).thenReturn(flat);

            List<CategoryNodeResponse> tree = categoryService.getCategoryTree();

            assertEquals(2, tree.size());

            CategoryNodeResponse food = findById(tree, 1L);
            assertEquals("食品", food.getName());
            assertNull(food.getParentId());
            assertEquals(2, food.getChildren().size());

            List<Long> childIds = food.getChildren().stream().map(CategoryNodeResponse::getId).toList();
            assertTrue(childIds.containsAll(List.of(3L, 4L)));

            CategoryNodeResponse drink = food.getChildren().stream()
                    .filter(n -> n.getId().equals(3L)).findFirst().orElseThrow();
            assertEquals(1L, drink.getParentId());
            assertTrue(drink.getChildren().isEmpty());

            CategoryNodeResponse electronics = findById(tree, 2L);
            assertTrue(electronics.getChildren().isEmpty());
        }

        @Test
        @DisplayName("无分类数据时返回空列表")
        void noData_returnsEmptyList() {
            when(productCategoryMapper.selectList(any())).thenReturn(Collections.emptyList());

            List<CategoryNodeResponse> tree = categoryService.getCategoryTree();

            assertNotNull(tree);
            assertTrue(tree.isEmpty());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // createCategory
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("createCategory")
    class CreateCategory {

        @Test
        @DisplayName("正常创建顶级分类（parentId 为 null）")
        void createTopLevel_success() {
            mockAuthUser(1L, AuthUtil.ROLE_STAFF, 1L);

            CreateCategoryRequest req = new CreateCategoryRequest();
            req.setName("食品");
            req.setParentId(null);

            when(productCategoryMapper.selectCount(any())).thenReturn(0L);
            // 用 when().thenAnswer() 代替 doAnswer().when()，规避 doAnswer 链中的歧义（见 debug记录.md）
            when(productCategoryMapper.insert(anyCategory())).thenAnswer(inv -> {
                ((ProductCategory) inv.getArgument(0)).setId(10L);
                return 1;
            });

            Long id = categoryService.createCategory(req);

            assertEquals(10L, id);
            // parentId 为 null 时不应查父分类
            verify(productCategoryMapper, never()).selectById(any());
            // 验证写库的分类字段
            ArgumentCaptor<ProductCategory> captor = ArgumentCaptor.forClass(ProductCategory.class);
            verify(productCategoryMapper).insert((ProductCategory) captor.capture());
            assertEquals("食品", captor.getValue().getName());
            assertNull(captor.getValue().getParentId());
            verify(cacheUtil).invalidate(CacheKeys.CATEGORY_TREE); // 创建后失效分类树缓存
        }

        @Test
        @DisplayName("正常创建子分类（parentId 存在）")
        void createSubCategory_success() {
            mockAuthUser(1L, AuthUtil.ROLE_ADMIN, null);

            CreateCategoryRequest req = new CreateCategoryRequest();
            req.setName("饮料");
            req.setParentId(1L);

            when(productCategoryMapper.selectById(1L)).thenReturn(cat(1L, null, "食品"));
            when(productCategoryMapper.selectCount(any())).thenReturn(0L);
            when(productCategoryMapper.insert(anyCategory())).thenAnswer(inv -> {
                ((ProductCategory) inv.getArgument(0)).setId(11L);
                return 1;
            });

            Long id = categoryService.createCategory(req);

            assertEquals(11L, id);
            ArgumentCaptor<ProductCategory> captor = ArgumentCaptor.forClass(ProductCategory.class);
            verify(productCategoryMapper).insert((ProductCategory) captor.capture());
            assertEquals(1L,    captor.getValue().getParentId());
            assertEquals("饮料", captor.getValue().getName());
        }

        @Test
        @DisplayName("parentId 对应分类不存在时抛出 code=404 的 BusinessException")
        void parentNotFound_throws404() {
            mockAuthUser(1L, AuthUtil.ROLE_ADMIN, null);

            CreateCategoryRequest req = new CreateCategoryRequest();
            req.setName("饮料");
            req.setParentId(999L);

            when(productCategoryMapper.selectById(999L)).thenReturn(null);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> categoryService.createCategory(req));
            assertEquals(Result.CODE_NOT_FOUND, ex.getCode());
            assertEquals("父分类不存在", ex.getMessage());
            // 父分类不存在，后续步骤不应执行
            verify(productCategoryMapper, never()).selectCount(any());
            verify(productCategoryMapper, never()).insert(anyCategory());
        }

        @Test
        @DisplayName("同级下存在同名分类时抛出 BusinessException")
        void duplicateName_throwsBusinessException() {
            mockAuthUser(1L, AuthUtil.ROLE_ADMIN, null);

            CreateCategoryRequest req = new CreateCategoryRequest();
            req.setName("饮料");
            req.setParentId(1L);

            when(productCategoryMapper.selectById(1L)).thenReturn(cat(1L, null, "食品"));
            when(productCategoryMapper.selectCount(any())).thenReturn(1L);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> categoryService.createCategory(req));
            assertEquals("该父级下已存在同名分类", ex.getMessage());
            verify(productCategoryMapper, never()).insert(anyCategory());
        }

        @Test
        @DisplayName("非 Staff/Admin 调用时抛出 code=403 的 BusinessException")
        void notAuthorized_throws403() {
            mockAuthUser(1L, AuthUtil.ROLE_CUSTOMER, null);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> categoryService.createCategory(new CreateCategoryRequest()));
            assertEquals(Result.CODE_FORBIDDEN, ex.getCode());
            verifyNoInteractions(productCategoryMapper);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // deleteCategory
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("deleteCategory")
    class DeleteCategory {

        @Test
        @DisplayName("正常删除叶子节点分类（无子分类、无商品）")
        void deleteLeaf_success() {
            mockAuthUser(1L, AuthUtil.ROLE_ADMIN, null);

            when(productCategoryMapper.selectById(5L)).thenReturn(cat(5L, 1L, "果汁"));
            when(productCategoryMapper.selectCount(any())).thenReturn(0L);
            when(productMapper.selectCount(any())).thenReturn(0L);
            // (Serializable) 转型消除 deleteById(Serializable) vs deleteById(T) 歧义
            when(productCategoryMapper.deleteById((Serializable) 5L)).thenReturn(1);

            assertDoesNotThrow(() -> categoryService.deleteCategory(5L));
            verify(productCategoryMapper).deleteById((Serializable) 5L);
            verify(cacheUtil).invalidate(CacheKeys.CATEGORY_TREE); // 删除后失效分类树缓存
        }

        @Test
        @DisplayName("分类不存在时抛出 code=404 的 BusinessException")
        void notFound_throws404() {
            mockAuthUser(1L, AuthUtil.ROLE_ADMIN, null);

            when(productCategoryMapper.selectById(999L)).thenReturn(null);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> categoryService.deleteCategory(999L));
            assertEquals(Result.CODE_NOT_FOUND, ex.getCode());
            assertEquals("分类不存在", ex.getMessage());
            verify(productCategoryMapper, never()).deleteById(anyId());
        }

        @Test
        @DisplayName("存在子分类时抛出 BusinessException，消息含子分类数量")
        void hasChildren_throwsBusinessException() {
            mockAuthUser(1L, AuthUtil.ROLE_ADMIN, null);

            when(productCategoryMapper.selectById(1L)).thenReturn(cat(1L, null, "食品"));
            when(productCategoryMapper.selectCount(any())).thenReturn(2L);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> categoryService.deleteCategory(1L));
            assertTrue(ex.getMessage().contains("2"));
            assertTrue(ex.getMessage().contains("子分类"));
            verify(productCategoryMapper, never()).deleteById(anyId());
        }

        @Test
        @DisplayName("存在关联商品时抛出 BusinessException，消息含商品数量")
        void hasProducts_throwsBusinessException() {
            mockAuthUser(1L, AuthUtil.ROLE_ADMIN, null);

            when(productCategoryMapper.selectById(3L)).thenReturn(cat(3L, 1L, "饮料"));
            when(productCategoryMapper.selectCount(any())).thenReturn(0L);
            when(productMapper.selectCount(any())).thenReturn(5L);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> categoryService.deleteCategory(3L));
            assertTrue(ex.getMessage().contains("5"));
            assertTrue(ex.getMessage().contains("商品"));
            verify(productCategoryMapper, never()).deleteById(anyId());
        }

        @Test
        @DisplayName("非 Admin 调用时抛出 code=403 的 BusinessException")
        void notAdmin_throws403() {
            mockAuthUser(1L, AuthUtil.ROLE_STAFF, 1L);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> categoryService.deleteCategory(1L));
            assertEquals(Result.CODE_FORBIDDEN, ex.getCode());
            verifyNoInteractions(productCategoryMapper);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 私有辅助方法
    // ─────────────────────────────────────────────────────────────────────────

    private static ProductCategory cat(Long id, Long parentId, String name) {
        return ProductCategory.builder().id(id).parentId(parentId).name(name).build();
    }

    private static CategoryNodeResponse findById(List<CategoryNodeResponse> list, Long id) {
        return list.stream().filter(n -> n.getId().equals(id)).findFirst().orElseThrow();
    }
}
