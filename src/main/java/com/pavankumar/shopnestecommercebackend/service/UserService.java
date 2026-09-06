package com.pavankumar.shopnestecommercebackend.service;

import com.pavankumar.shopnestecommercebackend.dto.AuthResponse;
import com.pavankumar.shopnestecommercebackend.dto.LoginRequest;
import com.pavankumar.shopnestecommercebackend.dto.RegisterRequest;
import com.pavankumar.shopnestecommercebackend.exception.ResourceAlreadyExistsException;
import com.pavankumar.shopnestecommercebackend.model.Role;
import com.pavankumar.shopnestecommercebackend.model.User;
import com.pavankumar.shopnestecommercebackend.repository.UserRepository;
import com.pavankumar.shopnestecommercebackend.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserService  {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtUtil jwtUtil;





    public AuthResponse register(RegisterRequest request){
        if(userRepository.existsByEmail(request.getEmail())){
            throw new ResourceAlreadyExistsException
                    ("Email already registered: "+request.getEmail());
        }
        User user=User.builder()
                .name(request.getName())
                .password(passwordEncoder.encode(request.getPassword()))
                .email(request.getEmail())
                .role(Role.ROLE_USER)
                .build();
        userRepository.save(user);
        return AuthResponse.builder()
                .role(user.getRole().name())
                .message("registration successfully")
                .build();
    }
    public AuthResponse login(LoginRequest request){
        try{
            Authentication authentication=authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.getEmail(),request.getPassword()));
            UserDetails userDetails=(UserDetails) authentication.getPrincipal();
            String token= jwtUtil.generateToken(userDetails);

            return AuthResponse.builder()
                    .token(token)
                    .role(jwtUtil.extractRoleFromUserDetails(userDetails))
                    .message("login successful")
                    .build();
        } catch (BadCredentialsException e) {
             throw e;
        }
    }
}
