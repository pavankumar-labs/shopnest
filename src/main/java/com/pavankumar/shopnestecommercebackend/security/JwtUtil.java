package com.pavankumar.shopnestecommercebackend.security;


import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import java.security.Key;
import java.util.Date;


@Component
public class JwtUtil {

    @Value("${JWT_SECRET}")
    private String secret;
    @Value("${JWT_EXPIRATION}")
    private long expiration;

    private Key getSigningKey(){
        return Keys.hmacShaKeyFor(secret.getBytes());
    }
    public String extractRoleFromUserDetails(UserDetails user){
        return user.getAuthorities().stream().findFirst().
                map(a->a.getAuthority()).
                orElse("ROLE_USER");
    }

    public String generateToken(UserDetails user){
        return Jwts.builder()
                .setSubject(user.getUsername())
                .claim("role",extractRoleFromUserDetails(user))
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis()+expiration))
                .signWith(getSigningKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    private Claims parseToken(String token){
        return Jwts.parserBuilder()
                .setSigningKey(getSigningKey())
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    public String extractEmail(String token){
        return parseToken(token).getSubject();
    }

    public String extractRole(String token){
        return parseToken(token).get("role",String.class);
    }


    public boolean isTokenValid(String token, UserDetails userDetails){
        Claims claims=parseToken(token);
        String email=claims.getSubject();
        Date expiration=claims.getExpiration();
        return email!=null && email.equals(userDetails.getUsername()) &&
                expiration!=null &&  expiration.after(new Date());
    }

}
