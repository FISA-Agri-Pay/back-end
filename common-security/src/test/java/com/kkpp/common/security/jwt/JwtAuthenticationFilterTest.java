package com.kkpp.common.security.jwt;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class JwtAuthenticationFilterTest {

    private JwtAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        filter = new JwtAuthenticationFilter();
    }

    @Test
    void doFilterInternalAlwaysDelegatesToFilterChain() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = mock(FilterChain.class);

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
    }

    @Test
    void doFilterInternalDelegatesEvenWithAuthorizationHeader() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer some.jwt.token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = mock(FilterChain.class);

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verifyNoMoreInteractions(filterChain);
    }

    @Test
    void doFilterInternalDelegatesWithNoAuthorizationHeader() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = mock(FilterChain.class);

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verifyNoMoreInteractions(filterChain);
    }

    @Test
    void doFilterInternalDoesNotSetSecurityContext() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer malformed.token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = mock(FilterChain.class);

        org.springframework.security.core.context.SecurityContextHolder.clearContext();

        filter.doFilterInternal(request, response, filterChain);

        // After PR change, the filter no longer processes JWT — SecurityContext remains empty
        org.springframework.security.core.Authentication authentication =
                org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        org.assertj.core.api.Assertions.assertThat(authentication).isNull();

        verify(filterChain).doFilter(request, response);
    }

    @Test
    void filterIsInstanceOfOncePerRequestFilter() {
        org.assertj.core.api.Assertions.assertThat(filter)
                .isInstanceOf(org.springframework.web.filter.OncePerRequestFilter.class);
    }

    @Test
    void filterCanBeInstantiatedWithNoArgConstructor() {
        // After the PR change, JwtAuthenticationFilter no longer requires a secret or entrypoint.
        JwtAuthenticationFilter newFilter = new JwtAuthenticationFilter();
        org.assertj.core.api.Assertions.assertThat(newFilter).isNotNull();
    }
}
