package com.pavankumar.shopnestecommercebackend.security;

import com.pavankumar.shopnestecommercebackend.model.Role;
import com.pavankumar.shopnestecommercebackend.model.User;
import com.pavankumar.shopnestecommercebackend.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserDetailsServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserDetailsServiceImpl userDetailsServiceImpl;

    @Test
    void loadUserByUsername_userFound_mapsToCorrectSpringSecurityUserDetails() {
        User domainUser = User.builder()
                .id(1L)
                .name("Pavan")
                .email("pavan@test.com")
                .password("hashed-pw-value")
                .role(Role.ROLE_ADMIN)
                .build();

        when(userRepository.findByEmail("pavan@test.com")).thenReturn(Optional.of(domainUser));

        UserDetails result = userDetailsServiceImpl.loadUserByUsername("pavan@test.com");

        assertEquals("pavan@test.com", result.getUsername());
        assertEquals("hashed-pw-value", result.getPassword());
        assertEquals(1, result.getAuthorities().size());
        assertEquals("ROLE_ADMIN", result.getAuthorities().iterator().next().getAuthority());
    }

    @Test
    void loadUserByUsername_userNotFound_throwsUsernameNotFoundException() {
        when(userRepository.findByEmail("missing@test.com")).thenReturn(Optional.empty());

        assertThrows(UsernameNotFoundException.class,
                () -> userDetailsServiceImpl.loadUserByUsername("missing@test.com"));
    }
}