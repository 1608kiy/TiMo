package com.timo.words.modules.user.service;

import com.timo.words.common.BusinessException;
import com.timo.words.modules.user.entity.User;
import com.timo.words.modules.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
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
class UserServiceTest {

    @Mock private UserRepository userRepository;
    @InjectMocks private UserService userService;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = new User();
        testUser.setId(1L);
        testUser.setEmail("test@example.com");
        testUser.setNickname("TestUser");
        testUser.setExamType("cet4");
        testUser.setTargetVocab(5000);
        testUser.setMuInit(8.0);
        testUser.setSigmaInit(3.0);
        testUser.setDefaultStudyMode("context_deep");
        testUser.setDailyNewLimit(20);
        testUser.setFatigueReminder(true);
    }

    @Test
    void getProfile_success() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));

        UserService.ProfileDTO profile = userService.getProfile(1L);

        assertEquals(1L, profile.getId());
        assertEquals("test@example.com", profile.getEmail());
        assertEquals("TestUser", profile.getNickname());
        assertEquals("cet4", profile.getExamType());
    }

    @Test
    void getProfile_notFound_throws() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(BusinessException.class, () -> userService.getProfile(99L));
    }

    @Test
    void updatePreferences_partialUpdate() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));

        UserService.UpdatePreferencesRequest req = new UserService.UpdatePreferencesRequest();
        req.setNickname("NewName");

        userService.updatePreferences(1L, req);

        assertEquals("NewName", testUser.getNickname());
        assertEquals("cet4", testUser.getExamType()); // unchanged
        verify(userRepository).save(testUser);
    }

    @Test
    void updatePreferences_allNull_noChange() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(userRepository.save(any())).thenReturn(testUser);

        UserService.UpdatePreferencesRequest req = new UserService.UpdatePreferencesRequest();
        UserService.ProfileDTO profile = userService.updatePreferences(1L, req);

        assertEquals("TestUser", profile.getNickname());
    }

    @Test
    void updateAvatar_success() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));

        UserService.ProfileDTO profile = userService.updateAvatar(1L, "/uploads/avatars/new.png");

        assertEquals("/uploads/avatars/new.png", profile.getAvatarUrl());
        verify(userRepository).save(testUser);
    }

    @Test
    void deleteAccount_success() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));

        userService.deleteAccount(1L);

        verify(userRepository).delete(testUser);
    }

    @Test
    void deleteAccount_notFound_throws() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(BusinessException.class, () -> userService.deleteAccount(99L));
    }
}
