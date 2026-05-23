package com.timo.words.modules.admin.service;

import com.timo.words.common.BusinessException;
import com.timo.words.modules.admin.repository.AdminOperationLogRepository;
import com.timo.words.modules.user.entity.User;
import com.timo.words.modules.user.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminUserServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private AdminOperationLogRepository operationLogRepository;
    @Mock private HttpServletRequest request;

    @InjectMocks private AdminUserService adminUserService;

    @Test
    void getUserDetail_existing_returnsUser() {
        User user = new User();
        user.setId(1L);
        user.setEmail("test@test.com");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        User result = adminUserService.getUserDetail(1L);

        assertEquals("test@test.com", result.getEmail());
    }

    @Test
    void getUserDetail_notFound_throwsException() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(BusinessException.class, () -> adminUserService.getUserDetail(99L));
    }

    @Test
    void updateRole_superAdmin_canChangeRole() {
        User operator = new User();
        operator.setId(1L);
        operator.setEmail("admin@test.com");
        operator.setRole("SUPER_ADMIN");
        when(userRepository.findById(1L)).thenReturn(Optional.of(operator));

        User target = new User();
        target.setId(2L);
        target.setEmail("user@test.com");
        target.setRole("USER");
        when(userRepository.findById(2L)).thenReturn(Optional.of(target));
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");

        adminUserService.updateRole(2L, "ADMIN", 1L, request);

        assertEquals("ADMIN", target.getRole());
        verify(userRepository).save(target);
        verify(operationLogRepository).save(any());
    }

    @Test
    void updateRole_nonSuperAdmin_throwsException() {
        User target = new User();
        target.setId(2L);
        target.setEmail("user@test.com");
        target.setRole("USER");
        when(userRepository.findById(2L)).thenReturn(Optional.of(target));

        User operator = new User();
        operator.setId(1L);
        operator.setRole("ADMIN");
        when(userRepository.findById(1L)).thenReturn(Optional.of(operator));

        assertThrows(BusinessException.class, () ->
                adminUserService.updateRole(2L, "ADMIN", 1L, request));
    }

    @Test
    void updateRole_selfChange_throwsException() {
        User operator = new User();
        operator.setId(1L);
        operator.setRole("SUPER_ADMIN");
        when(userRepository.findById(1L)).thenReturn(Optional.of(operator));

        assertThrows(BusinessException.class, () ->
                adminUserService.updateRole(1L, "ADMIN", 1L, request));
    }

    @Test
    void updateStatus_ban_logsBan() {
        User operator = new User();
        operator.setId(1L);
        operator.setEmail("admin@test.com");
        when(userRepository.findById(1L)).thenReturn(Optional.of(operator));

        User target = new User();
        target.setId(2L);
        target.setStatus("ACTIVE");
        when(userRepository.findById(2L)).thenReturn(Optional.of(target));
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");

        adminUserService.updateStatus(2L, "BANNED", 1L, request);

        assertEquals("BANNED", target.getStatus());
        verify(operationLogRepository).save(any());
    }

    @Test
    void updateStatus_selfBan_throwsException() {
        User operator = new User();
        operator.setId(1L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(operator));

        assertThrows(BusinessException.class, () ->
                adminUserService.updateStatus(1L, "BANNED", 1L, request));
    }

    @Test
    void deleteUser_superAdmin_canDelete() {
        User operator = new User();
        operator.setId(1L);
        operator.setEmail("admin@test.com");
        operator.setRole("SUPER_ADMIN");
        when(userRepository.findById(1L)).thenReturn(Optional.of(operator));

        User target = new User();
        target.setId(2L);
        target.setEmail("user@test.com");
        target.setRole("USER");
        when(userRepository.findById(2L)).thenReturn(Optional.of(target));
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");

        adminUserService.deleteUser(2L, 1L, request);

        verify(userRepository).delete(target);
        verify(operationLogRepository).save(any());
    }

    @Test
    void deleteUser_nonSuperAdmin_throwsException() {
        User operator = new User();
        operator.setId(1L);
        operator.setRole("ADMIN");
        when(userRepository.findById(1L)).thenReturn(Optional.of(operator));

        assertThrows(BusinessException.class, () ->
                adminUserService.deleteUser(2L, 1L, request));
    }

    @Test
    void deleteUser_selfDelete_throwsException() {
        User operator = new User();
        operator.setId(1L);
        operator.setRole("SUPER_ADMIN");
        when(userRepository.findById(1L)).thenReturn(Optional.of(operator));

        assertThrows(BusinessException.class, () ->
                adminUserService.deleteUser(1L, 1L, request));
    }

    @Test
    void deleteUser_lastSuperAdmin_throwsException() {
        User operator = new User();
        operator.setId(1L);
        operator.setRole("SUPER_ADMIN");
        when(userRepository.findById(1L)).thenReturn(Optional.of(operator));

        User target = new User();
        target.setId(2L);
        target.setRole("SUPER_ADMIN");
        when(userRepository.findById(2L)).thenReturn(Optional.of(target));
        when(userRepository.countByRole("SUPER_ADMIN")).thenReturn(1L);

        assertThrows(BusinessException.class, () ->
                adminUserService.deleteUser(2L, 1L, request));
    }
}
