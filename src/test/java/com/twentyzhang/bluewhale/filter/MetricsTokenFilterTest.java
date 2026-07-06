package com.twentyzhang.bluewhale.filter;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@DisplayName("MetricsTokenFilter")
class MetricsTokenFilterTest {

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("valid X-Metrics-Token on /actuator/prometheus grants ADMIN authority")
    void validTokenGrantsAdmin() throws Exception {
        MetricsTokenFilter filter = new MetricsTokenFilter("secret-token");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/actuator/prometheus");
        request.addHeader(MetricsTokenFilter.HEADER_NAME, "secret-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        var authentication = SecurityContextHolder.getContext().getAuthentication();
        assertNotNull(authentication);
        assertTrue(authentication.isAuthenticated());
        assertEquals("metrics-token", authentication.getPrincipal());
        assertTrue(authentication.getAuthorities().stream()
                .anyMatch(a -> "ADMIN".equals(a.getAuthority())));
        verify(chain).doFilter(request, response);
    }

    @Test
    @DisplayName("wrong token does not create authentication")
    void wrongTokenDoesNotAuthenticate() throws Exception {
        MetricsTokenFilter filter = new MetricsTokenFilter("secret-token");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/actuator/prometheus");
        request.addHeader(MetricsTokenFilter.HEADER_NAME, "wrong-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(chain).doFilter(request, response);
    }

    @Test
    @DisplayName("empty configured token disables metrics-token auth")
    void emptyConfiguredTokenDisablesFilter() throws Exception {
        MetricsTokenFilter filter = new MetricsTokenFilter("");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/actuator/prometheus");
        request.addHeader(MetricsTokenFilter.HEADER_NAME, "anything");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(chain).doFilter(request, response);
    }

    @Test
    @DisplayName("non-prometheus path is ignored")
    void nonPrometheusPathIgnored() throws Exception {
        MetricsTokenFilter filter = new MetricsTokenFilter("secret-token");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/products");
        request.addHeader(MetricsTokenFilter.HEADER_NAME, "secret-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(chain).doFilter(request, response);
    }
}
