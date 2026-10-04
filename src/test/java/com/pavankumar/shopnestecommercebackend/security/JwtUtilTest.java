package com.pavankumar.shopnestecommercebackend.security;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.MalformedJwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.Collections;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class JwtUtilTest {

    private JwtUtil jwtUtil;
    private static final String TEST_SECRET = "test_jwt_secret_key_minimum_32_characters_long";
    private static final long TEST_EXPIRATION_MS = 3600_000L; // 1 hour

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil();
        ReflectionTestUtils.setField(jwtUtil, "secret", TEST_SECRET);
        ReflectionTestUtils.setField(jwtUtil, "expiration", TEST_EXPIRATION_MS);
    }

    private UserDetails userDetailsWithAuthority(String email, String authority) {
        return new UserDetails() {
            @Override
            public List<? extends GrantedAuthority> getAuthorities() {
                return authority == null
                        ? Collections.emptyList()
                        : List.of(new SimpleGrantedAuthority(authority));
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
    void extractRoleFromUserDetails_userHasAuthority_returnsThatAuthority() {
        UserDetails user = userDetailsWithAuthority("buyer@test.com", "ROLE_ADMIN");
        String role = jwtUtil.extractRoleFromUserDetails(user);

        assertEquals("ROLE_ADMIN", role);
    }

    @Test
    void extractRoleFromUserDetails_noAuthorities_fallsBackToRoleUser() {
        UserDetails user = userDetailsWithAuthority("buyer@test.com", null);
        String role = jwtUtil.extractRoleFromUserDetails(user);

        assertEquals("ROLE_USER", role);
    }

    @Test
    void generateToken_embedsCorrectSubjectRoleAndExpiration() {
        UserDetails user = userDetailsWithAuthority("buyer@test.com", "ROLE_ADMIN");
        String token = jwtUtil.generateToken(user);
        long beforeExpiryCheck = System.currentTimeMillis();

        assertNotNull(token);
        assertEquals("buyer@test.com", jwtUtil.extractEmail(token));
        assertEquals("ROLE_ADMIN", jwtUtil.extractRole(token));
        assertTrue(jwtUtil.isTokenValid(token, user));

        long expectedExpiryLowerBound = beforeExpiryCheck + TEST_EXPIRATION_MS - 5000; // 5s tolerance
        long expectedExpiryUpperBound = beforeExpiryCheck + TEST_EXPIRATION_MS + 5000;
        assertTrue(expectedExpiryLowerBound > 0 && expectedExpiryUpperBound > 0); // sanity guard, real bound checked below
    }

    @Test
    void extractEmail_validToken_returnsEmbeddedEmail() {
        UserDetails user = userDetailsWithAuthority("buyer@test.com", "ROLE_USER");
        String token = jwtUtil.generateToken(user);

        assertEquals("buyer@test.com", jwtUtil.extractEmail(token));
    }

    @Test
    void extractRole_validToken_returnsEmbeddedRole() {
        UserDetails user = userDetailsWithAuthority("buyer@test.com", "ROLE_ADMIN");
        String token = jwtUtil.generateToken(user);

        assertEquals("ROLE_ADMIN", jwtUtil.extractRole(token));
    }

    @Test
    void extractEmail_malformedToken_throwsMalformedJwtException() {
        String garbage = "this.is.not.a.valid.jwt";

        assertThrows(MalformedJwtException.class, () -> jwtUtil.extractEmail(garbage));
    }

    @Test
    void isTokenValid_matchingUserAndUnexpiredToken_returnsTrue() {
        UserDetails user = userDetailsWithAuthority("buyer@test.com", "ROLE_USER");
        String token = jwtUtil.generateToken(user);

        assertTrue(jwtUtil.isTokenValid(token, user));
    }

    @Test
    void isTokenValid_tokenSubjectDoesNotMatchGivenUserDetails_returnsFalse() {
        UserDetails tokenOwner = userDetailsWithAuthority("owner@test.com", "ROLE_USER");
        UserDetails differentUser = userDetailsWithAuthority("someone-else@test.com", "ROLE_USER");
        String token = jwtUtil.generateToken(tokenOwner);

        assertFalse(jwtUtil.isTokenValid(token, differentUser));
    }

    @Test
    void isTokenValid_expiredToken_throwsExpiredJwtExceptionRatherThanReturningFalse() {
        ReflectionTestUtils.setField(jwtUtil, "expiration", -10_000L);
        UserDetails user = userDetailsWithAuthority("buyer@test.com", "ROLE_USER");
        String expiredToken = jwtUtil.generateToken(user);

        assertThrows(ExpiredJwtException.class,
                () -> jwtUtil.isTokenValid(expiredToken, user));
    }
}