package com.pavankumar.shopnestecommercebackend.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pavankumar.shopnestecommercebackend.dto.AuthResponse;
import com.pavankumar.shopnestecommercebackend.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.io.PrintWriter;
import java.io.StringWriter;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GoogleOAuth2SuccessHandlerTest {

    @Mock private UserService userService;
    @Mock private HttpServletRequest request;
    @Mock private HttpServletResponse response;
    @Mock private Authentication authentication;
    @Mock private OAuth2User oAuth2User;

    private ObjectMapper objectMapper;
    private GoogleOAuth2SuccessHandler handler;
    private StringWriter responseBody;

    @BeforeEach
    void setUp() throws Exception {
        objectMapper = new ObjectMapper();
        handler = new GoogleOAuth2SuccessHandler(userService, objectMapper);

        responseBody = new StringWriter();
        lenient().when(response.getWriter()).thenReturn(new PrintWriter(responseBody));
        when(authentication.getPrincipal()).thenReturn(oAuth2User);
    }

    private void stubGoogleAttributes(String sub, String email, String name, Boolean emailVerified) {
        when(oAuth2User.getAttribute("sub")).thenReturn(sub);
        when(oAuth2User.getAttribute("email")).thenReturn(email);
        when(oAuth2User.getAttribute("name")).thenReturn(name);
        when(oAuth2User.getAttribute("email_verified")).thenReturn(emailVerified);
    }

    @Test
    void onAuthenticationSuccess_emailNotVerified_sendsUnauthorizedAndNeverCallsUserService() throws Exception {
        stubGoogleAttributes("google-id-123", "pavan@test.com", "Pavan", false);
        handler.onAuthenticationSuccess(request, response, authentication);

        verify(response).sendError(HttpServletResponse.SC_UNAUTHORIZED, "Google email is not verified");
        verifyNoInteractions(userService);
    }

    @Test
    void onAuthenticationSuccess_missingGoogleId_sendsBadRequestAndNeverCallsUserService() throws Exception {
        stubGoogleAttributes(null, "pavan@test.com", "Pavan", true);
        handler.onAuthenticationSuccess(request, response, authentication);

        verify(response).sendError(HttpServletResponse.SC_BAD_REQUEST, "Google account information is incomplete");
        verifyNoInteractions(userService);
    }

    @Test
    void onAuthenticationSuccess_missingEmail_sendsBadRequestAndNeverCallsUserService() throws Exception {
        stubGoogleAttributes("google-id-123", null, "Pavan", true);
        handler.onAuthenticationSuccess(request, response, authentication);

        verify(response).sendError(HttpServletResponse.SC_BAD_REQUEST, "Google account information is incomplete");
        verifyNoInteractions(userService);
    }

    @Test
    void onAuthenticationSuccess_validGoogleUser_callsUserServiceAndWritesJsonResponse() throws Exception {
        stubGoogleAttributes("google-id-123", "pavan@test.com", "Pavan", true);

        AuthResponse fakeResponse = AuthResponse.builder()
                .token("fake-jwt").refreshToken("fake-refresh")
                .role("ROLE_USER").message("Google login successful")
                .build();
        when(userService.loginWithGoogle("google-id-123", "pavan@test.com", "Pavan"))
                .thenReturn(fakeResponse);
        handler.onAuthenticationSuccess(request, response, authentication);

        verify(userService).loginWithGoogle("google-id-123", "pavan@test.com", "Pavan");
        verify(response).setContentType("application/json");
        verify(response).setCharacterEncoding("UTF-8");
        verify(response, never()).sendError(anyInt(), anyString());
        assertTrue(responseBody.toString().contains("fake-jwt"));
        assertTrue(responseBody.toString().contains("Google login successful"));
    }
}