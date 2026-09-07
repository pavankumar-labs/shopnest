package com.pavankumar.shopnestecommercebackend.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pavankumar.shopnestecommercebackend.dto.AuthResponse;
import com.pavankumar.shopnestecommercebackend.service.UserService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class GoogleOAuth2SuccessHandler implements AuthenticationSuccessHandler {


    private final UserService userService;
    private final ObjectMapper objectMapper;

    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication)throws IOException, ServletException{

        OAuth2User googleUser =
                (OAuth2User) authentication.getPrincipal();

        String googleId =
                googleUser.getAttribute("sub");

        String email =
                googleUser.getAttribute("email");

        String name =
                googleUser.getAttribute("name");

        Boolean emailVerified =
                googleUser.getAttribute("email_verified");

        if (!Boolean.TRUE.equals(emailVerified)) {
            response.sendError(
                    HttpServletResponse.SC_UNAUTHORIZED,
                    "Google email is not verified"
            );
            return;
        }

        if (googleId == null || email == null) {
            response.sendError(
                    HttpServletResponse.SC_BAD_REQUEST,
                    "Google account information is incomplete"
            );
            return;
        }

        AuthResponse authResponse =
                userService.loginWithGoogle(
                        googleId,
                        email,
                        name
                );

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        response.getWriter().write(
                objectMapper.writeValueAsString(authResponse)
        );
    }
}
