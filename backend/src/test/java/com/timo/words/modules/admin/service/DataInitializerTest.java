package com.timo.words.modules.admin.service;

import com.timo.words.modules.admin.entity.SystemConfig;
import com.timo.words.modules.admin.repository.AiProviderConfigRepository;
import com.timo.words.modules.admin.repository.SystemConfigRepository;
import com.timo.words.modules.user.entity.User;
import com.timo.words.modules.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DataInitializerTest {

    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private SystemConfigRepository systemConfigRepository;
    @Mock private AiProviderConfigRepository aiProviderConfigRepository;

    @InjectMocks private DataInitializer dataInitializer;

    @Test
    void run_noExistingAdmin_createsSuperAdmin() {
        when(userRepository.findByEmail("tangjunhua@timo.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode(any())).thenReturn("encoded");
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(systemConfigRepository.findById(any())).thenReturn(Optional.empty());
        when(systemConfigRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(aiProviderConfigRepository.count()).thenReturn(0L);
        when(aiProviderConfigRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        dataInitializer.run();

        verify(userRepository).save(any(User.class));
    }

    @Test
    void run_existingAdmin_skipsCreation() {
        User existingAdmin = new User();
        existingAdmin.setEmail("tangjunhua@timo.com");
        when(userRepository.findByEmail("tangjunhua@timo.com")).thenReturn(Optional.of(existingAdmin));
        when(userRepository.findAll()).thenReturn(Collections.emptyList());
        when(systemConfigRepository.findById(any())).thenReturn(Optional.empty());
        when(systemConfigRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(aiProviderConfigRepository.count()).thenReturn(1L);
        when(aiProviderConfigRepository.findAll()).thenReturn(Collections.emptyList());

        dataInitializer.run();

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void run_fixExistingUsers_setsDefaults() {
        User user = new User();
        user.setId(1L);
        user.setEmail("user@test.com");
        user.setRole(null);
        user.setStatus(null);
        when(userRepository.findByEmail("tangjunhua@timo.com")).thenReturn(Optional.of(new User()));
        when(userRepository.findAll()).thenReturn(List.of(user));
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(systemConfigRepository.findById(any())).thenReturn(Optional.empty());
        when(systemConfigRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(aiProviderConfigRepository.count()).thenReturn(1L);
        when(aiProviderConfigRepository.findAll()).thenReturn(Collections.emptyList());

        dataInitializer.run();

        verify(userRepository).save(user);
    }

    @Test
    void run_fixExistingUsers_skipsAlreadySet() {
        User user = new User();
        user.setId(1L);
        user.setEmail("user@test.com");
        user.setRole("USER");
        user.setStatus("ACTIVE");
        when(userRepository.findByEmail("tangjunhua@timo.com")).thenReturn(Optional.of(new User()));
        when(userRepository.findAll()).thenReturn(List.of(user));
        when(systemConfigRepository.findById(any())).thenReturn(Optional.empty());
        when(systemConfigRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(aiProviderConfigRepository.count()).thenReturn(1L);
        when(aiProviderConfigRepository.findAll()).thenReturn(Collections.emptyList());

        dataInitializer.run();

        verify(userRepository, never()).save(user);
    }

    @Test
    void run_existingConfigs_skipsCreation() {
        SystemConfig existing = new SystemConfig();
        existing.setConfigKey("admin_secret");
        existing.setConfigValue("value");
        when(userRepository.findByEmail("tangjunhua@timo.com")).thenReturn(Optional.of(new User()));
        when(userRepository.findAll()).thenReturn(Collections.emptyList());
        when(systemConfigRepository.findById(any())).thenReturn(Optional.of(existing));
        when(aiProviderConfigRepository.count()).thenReturn(1L);
        when(aiProviderConfigRepository.findAll()).thenReturn(Collections.emptyList());

        dataInitializer.run();

        verify(systemConfigRepository, never()).save(any());
    }
}
