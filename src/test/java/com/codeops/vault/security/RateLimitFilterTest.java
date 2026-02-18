package com.codeops.vault.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class RateLimitFilterTest {

    private RateLimitFilter filter;
    private FilterChain filterChain;

    @BeforeEach
    void setUp() {
        filter = new RateLimitFilter();
        filterChain = mock(FilterChain.class);
    }

    @Test
    void requestsUnderLimit_pass() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/v1/vault/secrets");
        request.setRemoteAddr("192.168.1.1");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        assertThat(response.getStatus()).isNotEqualTo(429);
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void requestsOverLimit_return429() throws ServletException, IOException {
        String ip = "10.0.0.1";

        // Send 101 requests — first 100 should pass, 101st should be rejected
        for (int i = 0; i < 101; i++) {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setRequestURI("/api/v1/vault/secrets");
            request.setRemoteAddr(ip);
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilterInternal(request, response, filterChain);

            if (i < 100) {
                assertThat(response.getStatus()).isNotEqualTo(429);
            } else {
                assertThat(response.getStatus()).isEqualTo(429);
            }
        }
    }

    @Test
    void differentIps_trackedSeparately() throws ServletException, IOException {
        // Fill up IP 1 to 100 requests
        for (int i = 0; i < 100; i++) {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setRequestURI("/api/v1/vault/secrets");
            request.setRemoteAddr("10.0.0.1");
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilterInternal(request, response, filterChain);
        }

        // IP 2 should still be able to make requests
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/v1/vault/secrets");
        request.setRemoteAddr("10.0.0.2");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);
        assertThat(response.getStatus()).isNotEqualTo(429);
    }
}
