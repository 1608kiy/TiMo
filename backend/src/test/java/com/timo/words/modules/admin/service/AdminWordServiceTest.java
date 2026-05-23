package com.timo.words.modules.admin.service;

import com.timo.words.common.BusinessException;
import com.timo.words.modules.admin.repository.AdminOperationLogRepository;
import com.timo.words.modules.admin.repository.WordImportBatchRepository;
import com.timo.words.modules.study.repository.QuizRecordRepository;
import com.timo.words.modules.study.repository.UserWordBindRepository;
import com.timo.words.modules.user.repository.UserRepository;
import com.timo.words.modules.word.entity.Word;
import com.timo.words.modules.word.repository.WordRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminWordServiceTest {

    @Mock private WordRepository wordRepository;
    @Mock private WordImportBatchRepository batchRepository;
    @Mock private AdminOperationLogRepository operationLogRepository;
    @Mock private QuizRecordRepository quizRecordRepository;
    @Mock private UserWordBindRepository userWordBindRepository;
    @Mock private UserRepository userRepository;
    @Mock private HttpServletRequest request;

    @InjectMocks private AdminWordService adminWordService;

    @Test
    void createWord_savesAndLogs() {
        Word word = new Word();
        word.setWord("abandon");
        when(wordRepository.save(any())).thenAnswer(inv -> {
            Word w = inv.getArgument(0);
            w.setId(1L);
            return w;
        });
        when(userRepository.findById(1L)).thenReturn(Optional.empty());
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");

        Word result = adminWordService.createWord(word, 1L, request);

        assertNotNull(result.getId());
        verify(wordRepository).save(word);
        verify(operationLogRepository).save(any());
    }

    @Test
    void updateWord_existing_updatesAndLogs() {
        Word existing = new Word();
        existing.setId(1L);
        existing.setWord("old");
        when(wordRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(wordRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(userRepository.findById(1L)).thenReturn(Optional.empty());
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");

        Word update = new Word();
        update.setWord("abandon");
        update.setPhonetic("/əˈbændən/");
        update.setPos("v.");
        update.setExamType("CET4");

        Word result = adminWordService.updateWord(1L, update, 1L, request);

        assertEquals("abandon", result.getWord());
        assertEquals("/əˈbændən/", result.getPhonetic());
        verify(operationLogRepository).save(any());
    }

    @Test
    void updateWord_notFound_throwsException() {
        when(wordRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(BusinessException.class, () ->
                adminWordService.updateWord(99L, new Word(), 1L, request));
    }

    @Test
    void deleteWord_existing_deletesAndLogs() {
        Word word = new Word();
        word.setId(1L);
        word.setWord("abandon");
        when(wordRepository.findById(1L)).thenReturn(Optional.of(word));
        when(userRepository.findById(1L)).thenReturn(Optional.empty());
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");

        adminWordService.deleteWord(1L, 1L, request);

        verify(wordRepository).delete(word);
        verify(operationLogRepository).save(any());
    }

    @Test
    void deleteWord_notFound_throwsException() {
        when(wordRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(BusinessException.class, () ->
                adminWordService.deleteWord(99L, 1L, request));
    }

    @Test
    void deleteByExamTypes_withWords_deletesAndLogs() {
        Word w1 = new Word();
        w1.setId(1L);
        Word w2 = new Word();
        w2.setId(2L);
        when(wordRepository.findByExamTypeIn(List.of("CET4"))).thenReturn(List.of(w1, w2));
        when(userRepository.findById(1L)).thenReturn(Optional.empty());
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");

        int count = adminWordService.deleteByExamTypes(List.of("CET4"), 1L, request);

        assertEquals(2, count);
        verify(quizRecordRepository).deleteByWordIdIn(any());
        verify(userWordBindRepository).deleteByWordIdIn(any());
        verify(wordRepository).deleteAll(any());
    }

    @Test
    void deleteByExamTypes_noWords_returnsZero() {
        when(wordRepository.findByExamTypeIn(List.of("GRE"))).thenReturn(List.of());

        int count = adminWordService.deleteByExamTypes(List.of("GRE"), 1L, request);

        assertEquals(0, count);
        verify(wordRepository, never()).deleteAll(any());
    }
}
