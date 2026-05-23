package com.timo.words.modules.auth.service;

import com.timo.words.common.BusinessException;
import com.timo.words.common.ResultCode;
import com.timo.words.modules.auth.dto.LoginRequest;
import com.timo.words.modules.auth.dto.LoginResponse;
import com.timo.words.modules.auth.dto.RegisterRequest;
import com.timo.words.modules.user.entity.User;
import com.timo.words.modules.user.repository.UserRepository;
import com.timo.words.infrastructure.security.JwtUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtUtil jwtUtil;

    @InjectMocks private AuthService authService;

    // --- Register ---

    @Test
    void register_success() {
        RegisterRequest req = new RegisterRequest();
        req.setEmail("new@test.com");
        req.setPassword("pass123");
        req.setNickname("TestUser");

        when(userRepository.existsByEmail("new@test.com")).thenReturn(false);
        when(passwordEncoder.encode("pass123")).thenReturn("encoded");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(1L);
            return u;
        });
        when(jwtUtil.generateToken(eq(1L), eq("new@test.com"), eq("USER"))).thenReturn("token");

        LoginResponse resp = authService.register(req);

        assertNotNull(resp);
        assertEquals("token", resp.getToken());
        assertEquals("new@test.com", resp.getEmail());
        verify(userRepository).save(any(User.class));
    }

    @Test
    void register_defaultNickname() {
        RegisterRequest req = new RegisterRequest();
        req.setEmail("user@test.com");
        req.setPassword("pass");

        when(userRepository.existsByEmail("user@test.com")).thenReturn(false);
        when(passwordEncoder.encode("pass")).thenReturn("encoded");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(2L);
            return u;
        });
        when(jwtUtil.generateToken(eq(2L), eq("user@test.com"), eq("USER"))).thenReturn("t");

        LoginResponse resp = authService.register(req);
        assertEquals("user", resp.getNickname());
    }

    @Test
    void register_duplicateEmail_throws() {
        RegisterRequest req = new RegisterRequest();
        req.setEmail("dup@test.com");
        req.setPassword("pass");

        when(userRepository.existsByEmail("dup@test.com")).thenReturn(true);

        BusinessException ex = assertThrows(BusinessException.class, () -> authService.register(req));
        assertEquals(ResultCode.USER_ALREADY_EXISTS.getCode(), ex.getCode());
    }

    @Test
    void register_dbConstraint_throws() {
        RegisterRequest req = new RegisterRequest();
        req.setEmail("dup@test.com");
        req.setPassword("pass");

        when(userRepository.existsByEmail("dup@test.com")).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("encoded");
        doThrow(new org.springframework.dao.DataIntegrityViolationException("dup"))
                .when(userRepository).save(any());

        BusinessException ex = assertThrows(BusinessException.class, () -> authService.register(req));
        assertEquals(ResultCode.USER_ALREADY_EXISTS.getCode(), ex.getCode());
    }

    // --- Login ---

    @Test
    void login_success() {
        LoginRequest req = new LoginRequest();
        req.setEmail("user@test.com");
        req.setPassword("pass");

        User user = new User();
        user.setId(1L);
        user.setEmail("user@test.com");
        user.setPasswordHash("encoded");
        user.setNickname("Nick");

        when(userRepository.findByEmail("user@test.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("pass", "encoded")).thenReturn(true);
        when(jwtUtil.generateToken(1L, "user@test.com", "USER")).thenReturn("tok");

        LoginResponse resp = authService.login(req);
        assertEquals("tok", resp.getToken());
        assertEquals(1L, resp.getUserId());
        assertEquals("Nick", resp.getNickname());
    }

    @Test
    void login_userNotFound_throws() {
        LoginRequest req = new LoginRequest();
        req.setEmail("no@test.com");
        req.setPassword("pass");

        when(userRepository.findByEmail("no@test.com")).thenReturn(Optional.empty());

        BusinessException ex = assertThrows(BusinessException.class, () -> authService.login(req));
        assertEquals(ResultCode.USER_NOT_FOUND.getCode(), ex.getCode());
    }

    @Test
    void login_wrongPassword_throws() {
        LoginRequest req = new LoginRequest();
        req.setEmail("user@test.com");
        req.setPassword("wrong");

        User user = new User();
        user.setId(1L);
        user.setEmail("user@test.com");
        user.setPasswordHash("encoded");

        when(userRepository.findByEmail("user@test.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", "encoded")).thenReturn(false);

        BusinessException ex = assertThrows(BusinessException.class, () -> authService.login(req));
        assertEquals(ResultCode.PASSWORD_ERROR.getCode(), ex.getCode());
    }
}
