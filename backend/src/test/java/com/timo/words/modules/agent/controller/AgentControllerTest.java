package com.timo.words.modules.agent.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.timo.words.common.GlobalExceptionHandler;
import com.timo.words.common.Result;
import com.timo.words.modules.agent.service.AgentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class AgentControllerTest {

    @Mock private AgentService agentService;
    @InjectMocks private AgentController agentController;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(agentController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        objectMapper = new ObjectMapper();
    }

    @Test
    void testSessionReport_returnsSummary() throws Exception {
        AgentService.SessionReportRequest request = new AgentService.SessionReportRequest();
        request.setStudyMode("quick_memory");
        request.setTotalWords(10);
        request.setCorrectCount(8);
        request.setWrongCount(2);
        request.setElapsedMs(60000L);
        request.setWordTexts(List.of("word1", "word2"));
        request.setWrongWordTexts(List.of("word1"));

        AgentService.SessionReportResponse response = new AgentService.SessionReportResponse();
        response.setSummary("正确率80%！今天状态不错！");
        response.setTiMoState("success");
        response.setActions(List.of("开始今日学习"));

        when(agentService.generateSessionReport(any())).thenReturn(response);

        String json = objectMapper.writeValueAsString(request);

        mockMvc.perform(post("/api/agent/session-report")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.summary").value("正确率80%！今天状态不错！"))
                .andExpect(jsonPath("$.data.tiMoState").value("success"))
                .andExpect(jsonPath("$.data.actions[0]").value("开始今日学习"));

        verify(agentService, times(1)).generateSessionReport(any());
    }

    @Test
    void testSessionReport_validationFails() throws Exception {
        String invalidJson = "{\"studyMode\":\"\",\"totalWords\":0}";

        mockMvc.perform(post("/api/agent/session-report")
                .contentType(MediaType.APPLICATION_JSON)
                .content(invalidJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }
}
