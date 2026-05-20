package com.timo.words.modules.auth.service;

import com.timo.words.common.BusinessException;
import com.timo.words.modules.user.entity.User;
import com.timo.words.modules.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.lang.reflect.Field;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ForgotPasswordServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private StringRedisTemplate stringRedisTemplate;
    @Mock private ValueOperations<String, String> valueOperations;
    @InjectMocks private ForgotPasswordService forgotPasswordService;

    @BeforeEach
    void setUp() throws Exception {
        // Set @Value fields via reflection for tests
        Field fromEmailField = ForgotPasswordService.class.getDeclaredField("fromEmail");
        fromEmailField.setAccessible(true);
        fromEmailField.set(forgotPasswordService, "test@qq.com");

        Field fromPasswordField = ForgotPasswordService.class.getDeclaredField("fromPassword");
        fromPasswordField.setAccessible(true);
        fromPasswordField.set(forgotPasswordService, "test-auth-code");
    }

    @Test
    void sendVerificationCode_userNotFound_throws() {
        when(userRepository.findByEmail("no@test.com")).thenReturn(Optional.empty());

        assertThrows(BusinessException.class,
                () -> forgotPasswordService.sendVerificationCode("no@test.com"));
    }

    @Test
    void sendVerificationCode_success() {
        User user = new User();
        user.setId(1L);
        user.setEmail("test@test.com");

        when(userRepository.findByEmail("test@test.com")).thenReturn(Optional.of(user));
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);

        forgotPasswordService.sendVerificationCode("test@test.com");

        verify(valueOperations).set(eq("pwd:reset:test@test.com"), anyString(), eq(5L), eq(TimeUnit.MINUTES));
    }

    @Test
    void resetPassword_invalidCode_throws() {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("pwd:reset:test@test.com")).thenReturn("123456");

        assertThrows(BusinessException.class,
                () -> forgotPasswordService.resetPassword("test@test.com", "000000", "newpass"));
    }

    @Test
    void resetPassword_expiredCode_throws() {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("pwd:reset:test@test.com")).thenReturn(null);

        assertThrows(BusinessException.class,
                () -> forgotPasswordService.resetPassword("test@test.com", "123456", "newpass"));
    }

    @Test
    void resetPassword_success() {
        User user = new User();
        user.setId(1L);
        user.setEmail("test@test.com");

        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("pwd:reset:test@test.com")).thenReturn("123456");
        when(userRepository.findByEmail("test@test.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.encode("newpass")).thenReturn("encoded");

        forgotPasswordService.resetPassword("test@test.com", "123456", "newpass");

        assertEquals("encoded", user.getPasswordHash());
        verify(userRepository).save(user);
        verify(stringRedisTemplate).delete("pwd:reset:test@test.com");
    }

    @Test
    void resetPassword_userNotFoundAfterCodeCheck_throws() {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("pwd:reset:test@test.com")).thenReturn("123456");
        when(userRepository.findByEmail("test@test.com")).thenReturn(Optional.empty());

        assertThrows(BusinessException.class,
                () -> forgotPasswordService.resetPassword("test@test.com", "123456", "newpass"));
    }
}
