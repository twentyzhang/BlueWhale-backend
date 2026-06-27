package com.twentyzhang.bluewhale.controller;

import com.twentyzhang.bluewhale.common.Result;
import com.twentyzhang.bluewhale.config.SearchProperties;
import com.twentyzhang.bluewhale.dto.ProductListItemResponse;
import com.twentyzhang.bluewhale.service.SemanticSearchService;
import com.twentyzhang.bluewhale.util.AuthUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class SemanticSearchController {

    private final SemanticSearchService semanticSearchService;
    private final SearchProperties props;

    /** 语义搜索（开放）。topK 不传用配置默认值。 */
    @GetMapping("/products/semantic")
    public Result<List<ProductListItemResponse>> semanticSearch(
            @RequestParam String q,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(required = false) Integer topK) {
        int k = topK != null ? topK : props.getSemantic().getDefaultTopK();
        return Result.success(semanticSearchService.search(q, categoryId, minPrice, maxPrice, k));
    }

    /** 全量重建索引（Admin 手动）。鉴权在 Controller 层（reindexAll 也由系统路径用，不读 SecurityContext）。 */
    @PostMapping("/admin/products/reindex")
    public Result<Integer> reindex() {
        AuthUtil.requireRole(AuthUtil.ROLE_ADMIN);
        return Result.success(semanticSearchService.reindexAll());
    }
}
