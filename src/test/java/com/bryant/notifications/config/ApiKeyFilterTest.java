package com.bryant.notifications.config;

import jakarta.servlet.ServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ApiKeyFilterTest {
    @Test
    void rejectsMissingOrIncorrectApiKey() throws Exception {
        ApiKeyFilter filter = new ApiKeyFilter("expected-secret");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/notifications");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertEquals(401, response.getStatus());
        assertEquals("application/json", response.getContentType());
    }

    @Test
    void acceptsCorrectApiKey() throws Exception {
        ApiKeyFilter filter = new ApiKeyFilter("expected-secret");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/notifications");
        request.addHeader("X-API-Key", "expected-secret");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertEquals(200, response.getStatus());
        ServletRequest forwardedRequest = chain.getRequest();
        assertNotNull(forwardedRequest);
    }
}
