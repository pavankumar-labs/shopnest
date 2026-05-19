package com.pavankumar.shopnestecommercebackend.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.time.Duration;

@Slf4j
@Component
@RequiredArgsConstructor
public class RateLimitFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final RedisTemplate<String,String > redisTemplate;

    public static final int AUTH_LIMIT=5;
    public static final int PAYMENT_LIMIT=5;
    public static final int GENERAL_LIMIT=100;
    private static final Duration window=Duration.ofMinutes(1);

    @Override
    protected  void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response,
            FilterChain filterChain
            ) throws IOException, ServletException {
        String path=request.getRequestURI().split("\\?")[0];
        ;
        String identifier=getIdentifier(request,path);
        int limit=getLimitForPath(path);
        String redisKey="rate_limit:"+identifier+":"+path;
        try{
            redisTemplate.opsForValue().setIfAbsent(redisKey, "0", window);
            Long count = redisTemplate.opsForValue().increment(redisKey);
            if(count>limit){
                response.setStatus(429);
                response.setContentType("application/json");
                response.getWriter().write(
                        "{\"success\":false," +
                                "\"message\":\"Too many requests. " +
                                "Try again in 1 minute\"}"
                );
                return;
        }
        } catch (Exception e) {
            log.error("Redis unavailable, skipping rate limit: "
                    , e);
        }
        filterChain.doFilter(request,response);
    }
    private String getIdentifier(HttpServletRequest request,String path){
        if (path.contains("/api/auth") || path.contains("/api/payments")){
            return "ip: "+getClientIp(request);
        }
        String header =request.getHeader("Authorization");
        if(header !=null && header.startsWith("Bearer ")){
            String token=header.substring(7);
            try{
                String email=jwtUtil.extractEmail(token);
                return "email: "+email;
            }
            catch (Exception e){
                return "ip: "+getClientIp(request);
            }

        }
        return "ip: "+getClientIp(request);
    }
    private String getClientIp(HttpServletRequest request){
        String forwarded=request.getHeader("X-Forwarded-For");
        if(forwarded!=null && !(forwarded.isEmpty())){
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
    private int getLimitForPath(String path){
        if(path.contains("/api/auth")){
            return AUTH_LIMIT;
        }
        if (path.contains("/api/payments")){
            return PAYMENT_LIMIT;
        }
        else {
            return GENERAL_LIMIT;
        }
    }

}
