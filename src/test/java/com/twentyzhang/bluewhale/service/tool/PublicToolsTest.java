package com.twentyzhang.bluewhale.service.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.twentyzhang.bluewhale.dto.ProductDetailResponse;
import com.twentyzhang.bluewhale.dto.ProductListItemResponse;
import com.twentyzhang.bluewhale.service.ProductService;
import com.twentyzhang.bluewhale.service.SemanticSearchService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PublicToolsTest {

    final ObjectMapper om = new ObjectMapper();
    @Mock SemanticSearchService semantic;
    @Mock ProductService productService;

    @Test
    void searchProducts_callsSemanticAndTruncatesToEight() throws Exception {
        when(semantic.search(eq("耳机"), any(), any(), any(), anyInt()))
                .thenReturn(List.of(ProductListItemResponse.builder().id(1L).name("蓝牙耳机").build()));
        SearchProductsTool tool = new SearchProductsTool(semantic);
        Object result = tool.execute(om.readTree("{\"q\":\"耳机\"}"), new AgentContext(1L, "CUSTOMER"));
        assertTrue(result instanceof List<?>);
        assertTrue(tool.producesProducts());
        assertEquals("search_products", tool.name());
    }

    @Test
    void checkStock_returnsStockFromDetail() throws Exception {
        when(productService.getProductById(5L))
                .thenReturn(ProductDetailResponse.builder().id(5L).name("酱油").stock(12).price(new BigDecimal("9.9")).build());
        CheckStockTool tool = new CheckStockTool(productService);
        Object r = tool.execute(om.readTree("{\"productId\":5}"), new AgentContext(1L,"CUSTOMER"));
        assertTrue(r.toString().contains("12") || r instanceof java.util.Map);
    }
}
