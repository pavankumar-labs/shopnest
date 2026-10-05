package com.pavankumar.shopnestecommercebackend.security;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.MalformedJwtException;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JwtAuthFilterTest {
    @Mock
    private JwtUtil jwtUtil;
    @Mock
    private UserDetailsServiceImpl userDetailsService;
    @Mock
    private CustomAuthEntryPoint entryPoint;
    @Mock
    private FilterChain filterChain;

    @InjectMocks
    private JwtAuthFilter jwtAuthFilter;

    private final MockHttpServletRequest request = new MockHttpServletRequest();
    private final MockHttpServletResponse response = new MockHttpServletResponse();

    @BeforeEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private UserDetails userDetailsWithAuthority(String email, String authority) {
        return new UserDetails() {
            @Override
            public List<? extends GrantedAuthority> getAuthorities() {
                return List.of(new SimpleGrantedAuthority(authority));
            }

            @Override
            public String getPassword() {
                return "irrelevant";
            }

            @Override
            public String getUsername() {
                return email;
            }

            @Override
            public boolean isAccountNonExpired() {
                return true;
            }

            @Override
            public boolean isAccountNonLocked() {
                return true;
            }

            @Override
            public boolean isCredentialsNonExpired() {
                return true;
            }

            @Override
            public boolean isEnabled() {
                return true;
            }
        };
    }

    @Test
    void doFilter_noAuthorizationHeader_passesThrough() throws Exception {
        jwtAuthFilter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(jwtUtil, never()).extractEmail(anyString());
        verify(userDetailsService, never()).loadUserByUsername(anyString());
        verifyNoInteractions(entryPoint);
    }

    @Test
    void doFilter_headerNotBearer_passesThrough() throws Exception {
        request.addHeader("Authorization", "Basic somevalue");
        jwtAuthFilter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(jwtUtil, never()).extractEmail(anyString());
        verifyNoInteractions(entryPoint);
    }

    @Test
    void doFilter_extractedEmailIsNull_blocksAndCallsEntryPoint() throws Exception {
        request.addHeader("Authorization", "Bearer sometoken");
        when(jwtUtil.extractEmail("sometoken")).thenReturn(null);
        jwtAuthFilter.doFilter(request, response, filterChain);

        verify(entryPoint).commence(eq(request), eq(response), any());
        verify(filterChain, never()).doFilter(any(), any());
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(userDetailsService, never()).loadUserByUsername(anyString());
    }

    @Test
    void doFilter_alreadyAuthenticated_skipsReAuthenticationAndPassesThrough() throws Exception {
        request.addHeader("Authorization", "Bearer sometoken");
        when(jwtUtil.extractEmail("sometoken")).thenReturn("buyer@test.com");

        UserDetails existingUser = userDetailsWithAuthority("buyer@test.com", "ROLE_USER");
        SecurityContextHolder.getContext().setAuthentication(
                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                        existingUser, null, existingUser.getAuthorities()));
        jwtAuthFilter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verify(userDetailsService, never()).loadUserByUsername(anyString());
        verifyNoInteractions(entryPoint);
    }

    @Test
    void doFilter_validToken_setsAuthenticationWithCorrectPrincipalAndAuthorities() throws Exception {
        request.addHeader("Authorization", "Bearer validtoken");
        when(jwtUtil.extractEmail("validtoken")).thenReturn("buyer@test.com");

        UserDetails userDetails = userDetailsWithAuthority("buyer@test.com", "ROLE_USER");
        when(userDetailsService.loadUserByUsername("buyer@test.com")).thenReturn(userDetails);
        when(jwtUtil.isTokenValid("validtoken", userDetails)).thenReturn(true);
        jwtAuthFilter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verifyNoInteractions(entryPoint);
        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
        assertEquals(userDetails, SecurityContextHolder.getContext().getAuthentication().getPrincipal());
        assertEquals(userDetails.getAuthorities(),
                SecurityContextHolder.getContext().getAuthentication().getAuthorities());
    }

    @Test
    void doFilter_tokenInvalidForUser_blocksAndCallsEntryPoint() throws Exception {
        request.addHeader("Authorization", "Bearer mismatchedtoken");
        when(jwtUtil.extractEmail("mismatchedtoken")).thenReturn("buyer@test.com");

        UserDetails userDetails = userDetailsWithAuthority("buyer@test.com", "ROLE_USER");
        when(userDetailsService.loadUserByUsername("buyer@test.com")).thenReturn(userDetails);
        when(jwtUtil.isTokenValid("mismatchedtoken", userDetails)).thenReturn(false);
        jwtAuthFilter.doFilter(request, response, filterChain);

        verify(entryPoint).commence(eq(request), eq(response), any());
        verify(filterChain, never()).doFilter(any(), any());
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void doFilter_expiredToken_clearsContextAndCallsEntryPointWithExpiredMessage() throws Exception {
        request.addHeader("Authorization", "Bearer expiredtoken");
        when(jwtUtil.extractEmail("expiredtoken"))
                .thenThrow(new ExpiredJwtException(null, null, "expired"));
        jwtAuthFilter.doFilter(request, response, filterChain);

        verify(entryPoint).commence(eq(request), eq(response), any());
        verify(filterChain, never()).doFilter(any(), any());
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(userDetailsService, never()).loadUserByUsername(anyString());
    }

    @Test
    void doFilter_malformedToken_clearsContextAndCallsEntryPointWithInvalidMessage() throws Exception {
        request.addHeader("Authorization", "Bearer malformedtoken");
        when(jwtUtil.extractEmail("malformedtoken"))
                .thenThrow(new MalformedJwtException("bad signature"));
        jwtAuthFilter.doFilter(request, response, filterChain);

        verify(entryPoint).commence(eq(request), eq(response), any());
        verify(filterChain, never()).doFilter(any(), any());
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void doFilter_userNoLongerExists_propagatesUsernameNotFoundExceptionUncaught() {
        request.addHeader("Authorization", "Bearer validtoken");
        when(jwtUtil.extractEmail("validtoken")).thenReturn("deleted-user@test.com");
        when(userDetailsService.loadUserByUsername("deleted-user@test.com"))
                .thenThrow(new UsernameNotFoundException("User not found"));

        assertThrows(UsernameNotFoundException.class,
                () -> jwtAuthFilter.doFilter(request, response, filterChain));
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verifyNoInteractions(entryPoint);
    }
}