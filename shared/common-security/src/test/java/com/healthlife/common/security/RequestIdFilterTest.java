package com.healthlife.common.security;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RequestIdFilterTest {

    private RequestIdFilter filter = new RequestIdFilter();

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    void doFilter_shouldGenerateNewRequestId_whenHeaderNotPresent() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        String requestId = response.getHeader("X-Request-Id");
        assertThat(requestId).isNotNull();
        verify(chain).doFilter(request, response);
    }

    @Test
    void doFilter_shouldUseProvidedRequestId_whenHeaderPresent() throws Exception {
        String expectedId = "test-request-id-123";
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Request-Id", expectedId);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        String requestId = response.getHeader("X-Request-Id");
        assertThat(requestId).isEqualTo(expectedId);
        verify(chain).doFilter(request, response);
    }

    @Test
    void doFilter_shouldClearMdc_whenRequestCompletes() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertThat(MDC.get("requestId")).isNull();
    }

    @Test
    void doFilter_shouldClearMdc_evenOnException() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);
        doThrow(new RuntimeException("test exception")).when(chain).doFilter(any(), any());

        assertThatThrownBy(() -> filter.doFilter(request, response, chain))
                .isInstanceOf(RuntimeException.class);

        assertThat(MDC.get("requestId")).isNull();
    }
}
