package com.timo.words.modules.user.service;

import com.timo.words.common.BusinessException;
import com.timo.words.common.ResultCode;
import com.timo.words.modules.user.entity.User;
import com.timo.words.modules.user.repository.UserRepository;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    @Data
    public static class ProfileDTO {
        private Long id;
        private String email;
        private String nickname;
        private String examType;
        private Integer targetVocab;
        private Integer studyDays;
        private String selfAssessedLevel;
        private String difficultyPreference;
        private String avatarUrl;
        private Integer dailyNewLimit;
        private String defaultStudyMode;
        private Boolean fatigueReminder;
    }

    @Data
    public static class UpdatePreferencesRequest {
        private String nickname;
        private String examType;
        private Integer targetVocab;
        private Integer studyDays;
        private String difficultyPreference;
        private Integer dailyNewLimit;
        private String defaultStudyMode;
        private Boolean fatigueReminder;
    }

    public ProfileDTO getProfile(Long userId) {
        User user = userRepository.findById(userId).orElseThrow(() -> new BusinessException(ResultCode.USER_NOT_FOUND));
        ProfileDTO dto = new ProfileDTO();
        dto.setId(user.getId());
        dto.setEmail(user.getEmail());
        dto.setNickname(user.getNickname());
        dto.setExamType(user.getExamType());
        dto.setTargetVocab(user.getTargetVocab());
        dto.setStudyDays(user.getStudyDays());
        dto.setSelfAssessedLevel(user.getSelfAssessedLevel());
        dto.setDifficultyPreference(user.getDifficultyPreference());
        dto.setAvatarUrl(user.getAvatarUrl());
        dto.setDailyNewLimit(user.getDailyNewLimit());
        dto.setDefaultStudyMode(user.getDefaultStudyMode());
        dto.setFatigueReminder(user.getFatigueReminder());
        return dto;
    }

    public ProfileDTO updatePreferences(Long userId, UpdatePreferencesRequest req) {
        User user = userRepository.findById(userId).orElseThrow(() -> new BusinessException(ResultCode.USER_NOT_FOUND));
        if (req.getNickname() != null) user.setNickname(req.getNickname());
        if (req.getExamType() != null) user.setExamType(req.getExamType());
        if (req.getTargetVocab() != null) user.setTargetVocab(req.getTargetVocab());
        if (req.getStudyDays() != null) user.setStudyDays(req.getStudyDays());
        if (req.getDifficultyPreference() != null) user.setDifficultyPreference(req.getDifficultyPreference());
        if (req.getDailyNewLimit() != null) user.setDailyNewLimit(req.getDailyNewLimit());
        if (req.getDefaultStudyMode() != null) user.setDefaultStudyMode(req.getDefaultStudyMode());
        if (req.getFatigueReminder() != null) user.setFatigueReminder(req.getFatigueReminder());
        userRepository.save(user);
        return getProfile(userId);
    }

    public ProfileDTO updateAvatar(Long userId, String avatarUrl) {
        User user = userRepository.findById(userId).orElseThrow(() -> new BusinessException(ResultCode.USER_NOT_FOUND));
        user.setAvatarUrl(avatarUrl);
        userRepository.save(user);
        return getProfile(userId);
    }

    public void deleteAccount(Long userId) {
        User user = userRepository.findById(userId).orElseThrow(() -> new BusinessException(ResultCode.USER_NOT_FOUND));
        userRepository.delete(user);
    }
}
