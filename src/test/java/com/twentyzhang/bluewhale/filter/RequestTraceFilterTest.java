package com.twentyzhang.bluewhale.filter;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@DisplayName("RequestTraceFilter")
class RequestTraceFilterTest {

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    @DisplayName("generates requestId when header is missing")
    void generatesRequestId() throws Exception {
        RequestTraceFilter filter = new RequestTraceFilter();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/products");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = (req, res) -> assertNotNull(MDC.get(RequestTraceFilter.MDC_KEY));

        filter.doFilter(request, response, chain);

        String responseId = response.getHeader(RequestTraceFilter.HEADER_NAME);
        assertNotNull(responseId);
        assertFalse(responseId.isBlank());
        assertNull(MDC.get(RequestTraceFilter.MDC_KEY));
    }

    @Test
    @DisplayName("propagates existing X-Request-Id")
    void propagatesExistingRequestId() throws Exception {
        RequestTraceFilter filter = new RequestTraceFilter();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/products");
        request.addHeader(RequestTraceFilter.HEADER_NAME, "req-123");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = (req, res) -> assertEquals("req-123", MDC.get(RequestTraceFilter.MDC_KEY));

        filter.doFilter(request, response, chain);

        assertEquals("req-123", response.getHeader(RequestTraceFilter.HEADER_NAME));
        assertNull(MDC.get(RequestTraceFilter.MDC_KEY));
    }

    @Test
    @DisplayName("continues filter chain")
    void continuesFilterChain() throws Exception {
        RequestTraceFilter filter = new RequestTraceFilter();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/products");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
    }
}
