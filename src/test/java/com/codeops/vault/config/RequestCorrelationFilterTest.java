package com.codeops.vault.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class RequestCorrelationFilterTest {

    private RequestCorrelationFilter filter;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private FilterChain filterChain;

    @BeforeEach
    void setUp() {
        filter = new RequestCorrelationFilter();
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        filterChain = mock(FilterChain.class);
    }

    @Test
    void generatesNewCorrelationIdWhenAbsent() throws ServletException, IOException {
        request.setMethod("GET");
        request.setRequestURI("/health");

        filter.doFilterInternal(request, response, filterChain);

        String correlationId = response.getHeader("X-Correlation-ID");
        assertThat(correlationId).isNotNull().isNotBlank();
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void propagatesExistingCorrelationId() throws ServletException, IOException {
        String existingId = "test-correlation-456";
        request.setMethod("GET");
        request.setRequestURI("/health");
        request.addHeader("X-Correlation-ID", existingId);

        filter.doFilterInternal(request, response, filterChain);

        assertThat(response.getHeader("X-Correlation-ID")).isEqualTo(existingId);
    }

    @Test
    void addsCorrelationIdToResponse() throws ServletException, IOException {
        request.setMethod("GET");
        request.setRequestURI("/health");

        filter.doFilterInternal(request, response, filterChain);

        assertThat(response.getHeader("X-Correlation-ID")).isNotNull();
    }

    @Test
    void setsMdcDuringFilterExecution() throws ServletException, IOException {
        request.setMethod("POST");
        request.setRequestURI("/api/v1/vault/secrets");

        FilterChain captureChain = (req, res) -> {
            assertThat(MDC.get("correlationId")).isNotNull();
            assertThat(MDC.get("requestPath")).isEqualTo("/api/v1/vault/secrets");
            assertThat(MDC.get("requestMethod")).isEqualTo("POST");
        };

        filter.doFilterInternal(request, response, captureChain);

        // MDC cleared after filter completes
        assertThat(MDC.get("correlationId")).isNull();
    }
}
