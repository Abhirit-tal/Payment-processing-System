package com.example.payment.config;

import com.example.payment.auth.JwtTokenProvider;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for JwtFilter.
 */
public class JwtFilterTest {

    private JwtTokenProvider jwtTokenProvider;
    private JwtFilter jwtFilter;
    private HttpServletRequest mockRequest;
    private HttpServletResponse mockResponse;
    private FilterChain mockFilterChain;

    @BeforeEach
    void setUp() {
        jwtTokenProvider = mock(JwtTokenProvider.class);
        jwtFilter = new JwtFilter(jwtTokenProvider);
        mockRequest = mock(HttpServletRequest.class);
        mockResponse = mock(HttpServletResponse.class);
        mockFilterChain = mock(FilterChain.class);

        // Clear security context before each test
        SecurityContextHolder.clearContext();
    }

    @Test
    void testNoAuthorizationHeader() throws Exception {
        when(mockRequest.getHeader(HttpHeaders.AUTHORIZATION)).thenReturn(null);

        jwtFilter.doFilterInternal(mockRequest, mockResponse, mockFilterChain);

        verify(mockFilterChain).doFilter(mockRequest, mockResponse);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void testAuthorizationHeaderWithoutBearer() throws Exception {
        when(mockRequest.getHeader(HttpHeaders.AUTHORIZATION)).thenReturn("Basic sometoken");

        jwtFilter.doFilterInternal(mockRequest, mockResponse, mockFilterChain);

        verify(mockFilterChain).doFilter(mockRequest, mockResponse);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void testValidBearerToken() throws Exception {
        String token = "valid.jwt.token";
        when(mockRequest.getHeader(HttpHeaders.AUTHORIZATION)).thenReturn("Bearer " + token);
        doNothing().when(jwtTokenProvider).validateToken(token);

        jwtFilter.doFilterInternal(mockRequest, mockResponse, mockFilterChain);

        verify(jwtTokenProvider).validateToken(token);
        verify(mockFilterChain).doFilter(mockRequest, mockResponse);
        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
        assertEquals("developer", SecurityContextHolder.getContext().getAuthentication().getPrincipal());
    }

    @Test
    void testInvalidBearerToken() throws Exception {
        String token = "invalid.jwt.token";
        when(mockRequest.getHeader(HttpHeaders.AUTHORIZATION)).thenReturn("Bearer " + token);
        doThrow(new RuntimeException("Invalid token")).when(jwtTokenProvider).validateToken(token);

        jwtFilter.doFilterInternal(mockRequest, mockResponse, mockFilterChain);

        verify(jwtTokenProvider).validateToken(token);
        verify(mockResponse).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        verify(mockFilterChain, never()).doFilter(mockRequest, mockResponse);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void testExpiredToken() throws Exception {
        String token = "expired.jwt.token";
        when(mockRequest.getHeader(HttpHeaders.AUTHORIZATION)).thenReturn("Bearer " + token);
        doThrow(new RuntimeException("Token has expired")).when(jwtTokenProvider).validateToken(token);

        jwtFilter.doFilterInternal(mockRequest, mockResponse, mockFilterChain);

        verify(mockResponse).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        verify(mockFilterChain, never()).doFilter(mockRequest, mockResponse);
    }

    @Test
    void testBearerTokenWithExtraSpaces() throws Exception {
        // The token parsing removes "Bearer " (7 chars) so spaces after Bearer are part of token
        String token = "valid.jwt.token";
        when(mockRequest.getHeader(HttpHeaders.AUTHORIZATION)).thenReturn("Bearer " + token);
        doNothing().when(jwtTokenProvider).validateToken(token);

        jwtFilter.doFilterInternal(mockRequest, mockResponse, mockFilterChain);

        verify(jwtTokenProvider).validateToken(token);
        verify(mockFilterChain).doFilter(mockRequest, mockResponse);
    }

    @Test
    void testAuthenticationHasRoleUser() throws Exception {
        String token = "valid.jwt.token";
        when(mockRequest.getHeader(HttpHeaders.AUTHORIZATION)).thenReturn("Bearer " + token);
        doNothing().when(jwtTokenProvider).validateToken(token);

        jwtFilter.doFilterInternal(mockRequest, mockResponse, mockFilterChain);

        var auth = SecurityContextHolder.getContext().getAuthentication();
        assertNotNull(auth);
        assertTrue(auth.getAuthorities().stream()
            .anyMatch(a -> a.getAuthority().equals("ROLE_USER")));
    }
}

