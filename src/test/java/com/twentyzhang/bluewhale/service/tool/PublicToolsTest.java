package com.twentyzhang.bluewhale.service.tool;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.twentyzhang.bluewhale.dto.CouponGroupResponse;
import com.twentyzhang.bluewhale.dto.ProductDetailResponse;
import com.twentyzhang.bluewhale.dto.ProductListItemResponse;
import com.twentyzhang.bluewhale.service.CouponGroupService;
import com.twentyzhang.bluewhale.service.ProductService;
import com.twentyzhang.bluewhale.service.SemanticSearchService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PublicToolsTest {

    final ObjectMapper om = new ObjectMapper();
    @Mock SemanticSearchService semantic;
    @Mock ProductService productService;
    @Mock CouponGroupService couponGroupService;

    @Test
    void searchProducts_callsSemanticAndTruncatesToEight() throws Exception {
        when(semantic.search(eq("耳机"), any(), any(), any(), anyInt()))
                .thenReturn(List.of(ProductListItemResponse.builder().id(1L).name("蓝牙耳机").build()));
        SearchProductsTool tool = new SearchProductsTool(semantic);
        Object result = tool.execute(om.readTree("{\"q\":\"耳机\"}"), new AgentContext(1L, "CUSTOMER"));
        assertTrue(result instanceof List<?>);
        assertTrue(tool.producesProducts());
        assertEquals("search_products", tool.name());
        assertTrue(tool.description().contains("商品推荐"));
        assertTrue(tool.description().contains("预算/用途/对象"));
    }

    @Test
    void searchProducts_truncatesTo8WhenServiceReturns9() throws Exception {
        // Simulate service returning 9 items even though limit=8 was requested
        List<ProductListItemResponse> nineItems = IntStream.rangeClosed(1, 9)
                .mapToObj(i -> ProductListItemResponse.builder().id((long) i).name("商品" + i).build())
                .collect(Collectors.toList());
        when(semantic.search(any(), any(), any(), any(), anyInt())).thenReturn(nineItems);
        SearchProductsTool tool = new SearchProductsTool(semantic);
        Object result = tool.execute(om.readTree("{\"q\":\"test\"}"), new AgentContext(1L, "CUSTOMER"));
        List<?> list = (List<?>) result;
        assertEquals(8, list.size(), "Guard r.size()>8 must truncate to exactly 8 items");
    }

    @Test
    void checkStock_returnsStockFromDetail() throws Exception {
        when(productService.getProductById(5L))
                .thenReturn(ProductDetailResponse.builder().id(5L).name("酱油").stock(12).price(new BigDecimal("9.9")).build());
        CheckStockTool tool = new CheckStockTool(productService);
        Object r = tool.execute(om.readTree("{\"productId\":5}"), new AgentContext(1L, "CUSTOMER"));
        Map<?, ?> m = (Map<?, ?>) r;
        assertTrue(tool.description().contains("能不能买"));
        assertTrue(tool.description().contains("需先知道 productId"));
        assertEquals(5L, m.get("productId"));
        assertEquals("酱油", m.get("name"));
        assertEquals(12, m.get("stock"));
    }

    @Test
    void productDetail_delegatesToProductServiceAndReturnsResponse() throws Exception {
        ProductDetailResponse expected = ProductDetailResponse.builder()
                .id(7L).name("香醋").price(new BigDecimal("5.5")).stock(30).build();
        when(productService.getProductById(7L)).thenReturn(expected);
        ProductDetailTool tool = new ProductDetailTool(productService);
        Object result = tool.execute(om.readTree("{\"productId\":7}"), new AgentContext(1L, "CUSTOMER"));
        assertTrue(tool.description().contains("进一步比较"));
        assertTrue(tool.description().contains("需 productId"));
        assertSame(expected, result);
        verify(productService).getProductById(7L);
    }

    @Test
    void listClaimableCoupons_returnsPageRecords() throws Exception {
        CouponGroupResponse coupon = CouponGroupResponse.builder()
                .id(1L).name("满100减20").type("FULL_REDUCTION").value(new BigDecimal("20")).build();
        Page<CouponGroupResponse> page = new Page<>();
        page.setRecords(List.of(coupon));
        when(couponGroupService.getAvailableCouponGroups(1, 20)).thenReturn(page);
        ListClaimableCouponsTool tool = new ListClaimableCouponsTool(couponGroupService);
        Object result = tool.execute(om.readTree("{}"), new AgentContext(1L, "CUSTOMER"));
        List<?> records = (List<?>) result;
        assertTrue(tool.description().contains("平台或店铺"));
        assertTrue(tool.description().contains("有什么优惠可以领"));
        assertEquals(1, records.size());
        assertSame(coupon, records.get(0));
    }
}
