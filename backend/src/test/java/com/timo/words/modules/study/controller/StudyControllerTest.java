package com.timo.words.modules.study.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.timo.words.config.SecurityConfig;
import com.timo.words.infrastructure.security.JwtAuthenticationFilter;
import com.timo.words.infrastructure.security.JwtUtil;
import com.timo.words.infrastructure.security.RateLimitFilter;
import com.timo.words.infrastructure.security.TokenBlacklistService;
import com.timo.words.modules.study.service.StudyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(StudyController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, RateLimitFilter.class})
class StudyControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockitoBean private StudyService studyService;
    @MockitoBean private JwtUtil jwtUtil;
    @MockitoBean private TokenBlacklistService tokenBlacklistService;
    @MockitoBean private StringRedisTemplate stringRedisTemplate;

    @BeforeEach
    void setUp() {
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOps = mock(ValueOperations.class);
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.increment(anyString())).thenReturn(1L);
    }

    private UsernamePasswordAuthenticationToken mockAuth() {
        return new UsernamePasswordAuthenticationToken(1L, null, List.of());
    }

    @Test
    void submitQuickMemory_success() throws Exception {
        StudyService.SubmitResponse resp = new StudyService.SubmitResponse();
        resp.setGrade(4.0);
        resp.setNewStability(1.5);
        resp.setNewDifficulty(5.0);
        resp.setNewRetrievability(1.0);
        resp.setDf(1.2);
        resp.setNeedsSpelling(false);
        resp.setNextReviewTime(LocalDateTime.now().plusDays(7));

        when(studyService.submitQuickMemory(any())).thenReturn(resp);

        String body = """
                {
                    "userId": 1,
                    "wordId": 100,
                    "recognized": true,
                    "verifiedCorrect": true,
                    "reactionTimeMs": 3000
                }
                """;

        mockMvc.perform(post("/api/study/submit-quick-memory")
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .with(SecurityMockMvcRequestPostProcessors.authentication(mockAuth()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.grade").value(4.0))
                .andExpect(jsonPath("$.data.needsSpelling").value(false));
    }

    @Test
    void submitContextDeep_success() throws Exception {
        StudyService.SubmitResponse resp = new StudyService.SubmitResponse();
        resp.setGrade(3.5);
        resp.setNewStability(1.2);
        resp.setNewDifficulty(5.5);
        resp.setNewRetrievability(1.0);
        resp.setDf(1.0);
        resp.setNeedsSpelling(false);

        when(studyService.submitContextDeepWord(any())).thenReturn(resp);

        String body = """
                {
                    "userId": 1,
                    "wordId": 100,
                    "s2": 4,
                    "s3": 3,
                    "s4": 4,
                    "s5": 3,
                    "hintTotal": 0,
                    "reactionTimeMs": 5000
                }
                """;

        mockMvc.perform(post("/api/study/submit-context-deep")
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .with(SecurityMockMvcRequestPostProcessors.authentication(mockAuth()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.grade").value(3.5));
    }

    @Test
    void submitQuickMemory_forbiddenWithoutAuth() throws Exception {
        String body = """
                {
                    "userId": 1,
                    "wordId": 100,
                    "recognized": true,
                    "verifiedCorrect": true,
                    "reactionTimeMs": 3000
                }
                """;

        mockMvc.perform(post("/api/study/submit-quick-memory")
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized());
    }
}
