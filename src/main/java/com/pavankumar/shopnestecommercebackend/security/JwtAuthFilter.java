package com.pavankumar.shopnestecommercebackend.security;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;

@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {
    private final JwtUtil jwtUtil;
    private final UserDetailsServiceImpl userDetailsService;
    private final CustomAuthEntryPoint entryPoint;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response
    , FilterChain filterChain) throws IOException, ServletException{
        String authHeader=request.getHeader("Authorization");
        if(authHeader==null || !authHeader.startsWith("Bearer ")){
            filterChain.doFilter(request,response);
            return;
        }
        String token=authHeader.substring(7);
        try {
            String email = jwtUtil.extractEmail(token);
            if (email == null) {
                entryPoint.commence(request, response, new BadCredentialsException("Invalid token"));
                return;
            }
            if (SecurityContextHolder.getContext().getAuthentication() == null) {
                UserDetails userDetails = userDetailsService.loadUserByUsername(email);
                if (jwtUtil.isTokenValid(token, userDetails)) {
                    UsernamePasswordAuthenticationToken authenticationToken =
                            new UsernamePasswordAuthenticationToken(userDetails, null
                                    , userDetails.getAuthorities());
                    authenticationToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authenticationToken);
                }
                else {
                    entryPoint.commence(request, response,
                            new BadCredentialsException("Token is invalid"));
                    return;
                }


            }
        }
        catch (ExpiredJwtException e) {
            SecurityContextHolder.clearContext();
            entryPoint.commence(request,response
                    ,new BadCredentialsException("expired token"));
            return;
        }
        catch (JwtException e) {
            SecurityContextHolder.clearContext();
            entryPoint.commence(request, response,
                    new BadCredentialsException("Invalid token"));
            return;
        }
        filterChain.doFilter(request, response);
    }
}
