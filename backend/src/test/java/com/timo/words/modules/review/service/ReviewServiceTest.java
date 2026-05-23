package com.timo.words.modules.review.service;

import com.timo.words.modules.study.entity.UserWordBind;
import com.timo.words.modules.study.repository.UserWordBindRepository;
import com.timo.words.modules.user.repository.UserRepository;
import com.timo.words.modules.word.entity.Word;
import com.timo.words.modules.word.repository.WordRepository;
import com.timo.words.modules.study.service.StudyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReviewServiceTest {

    @Mock private UserWordBindRepository userWordBindRepository;
    @Mock private UserRepository userRepository;
    @Mock private WordRepository wordRepository;
    @Mock private StudyService studyService;
    @InjectMocks private ReviewService reviewService;

    private UserWordBind dueBind;
    private UserWordBind stubbornBind;

    @BeforeEach
    void setUp() {
        dueBind = new UserWordBind();
        dueBind.setId(1L);
        dueBind.setUserId(1L);
        dueBind.setWordId(10L);
        dueBind.setStability(0.8);
        dueBind.setDifficulty(5.0);
        dueBind.setIsStubborn(false);
        dueBind.setNextReviewTime(LocalDateTime.now().minusHours(1));

        stubbornBind = new UserWordBind();
        stubbornBind.setId(2L);
        stubbornBind.setUserId(1L);
        stubbornBind.setWordId(20L);
        stubbornBind.setStability(0.6);
        stubbornBind.setDifficulty(7.0);
        dueBind.setConsecutiveErrors(0);
        stubbornBind.setIsStubborn(true);
        stubbornBind.setNextReviewTime(LocalDateTime.now().plusDays(1));
    }

    @Test
    void getReviewQueue_mergesDueAndStubborn() {
        when(userWordBindRepository.findActiveDueByUserIdAndTimeBefore(eq(1L), any()))
                .thenReturn(List.of(dueBind));
        when(userWordBindRepository.findByUserIdAndIsStubbornTrue(1L))
                .thenReturn(List.of(stubbornBind));

        Word w1 = new Word();
        w1.setId(10L);
        w1.setWord("test1");
        Word w2 = new Word();
        w2.setId(20L);
        w2.setWord("test2");
        when(wordRepository.findByIdIn(anyList())).thenReturn(List.of(w1, w2));

        ReviewService.ReviewQueueResponse resp = reviewService.getReviewQueue(1L);

        assertEquals(2, resp.getWords().size());
        assertEquals(1, resp.getDueCount());
        assertEquals(1, resp.getStubbornCount());
    }

    @Test
    void getReviewQueue_stubbornFirst() {
        when(userWordBindRepository.findActiveDueByUserIdAndTimeBefore(eq(1L), any()))
                .thenReturn(List.of(dueBind));
        when(userWordBindRepository.findByUserIdAndIsStubbornTrue(1L))
                .thenReturn(List.of(stubbornBind));

        Word w1 = new Word();
        w1.setId(10L);
        w1.setWord("aaa");
        Word w2 = new Word();
        w2.setId(20L);
        w2.setWord("bbb");
        when(wordRepository.findByIdIn(anyList())).thenReturn(List.of(w1, w2));

        ReviewService.ReviewQueueResponse resp = reviewService.getReviewQueue(1L);

        assertTrue(resp.getWords().get(0).isStubborn(), "Stubborn word should be first");
    }

    @Test
    void getReviewQueue_empty() {
        when(userWordBindRepository.findActiveDueByUserIdAndTimeBefore(eq(1L), any()))
                .thenReturn(Collections.emptyList());
        when(userWordBindRepository.findByUserIdAndIsStubbornTrue(1L))
                .thenReturn(Collections.emptyList());

        ReviewService.ReviewQueueResponse resp = reviewService.getReviewQueue(1L);
        assertTrue(resp.getWords().isEmpty());
        assertEquals(0, resp.getDueCount());
    }

    @Test
    void getReviewQueue_deduplicatesStubborn() {
        // A stubborn word that's also due should appear once
        dueBind.setIsStubborn(true);
        when(userWordBindRepository.findActiveDueByUserIdAndTimeBefore(eq(1L), any()))
                .thenReturn(List.of(dueBind));
        when(userWordBindRepository.findByUserIdAndIsStubbornTrue(1L))
                .thenReturn(List.of(dueBind));

        Word w = new Word();
        w.setId(10L);
        w.setWord("test");
        when(wordRepository.findByIdIn(anyList())).thenReturn(List.of(w));

        ReviewService.ReviewQueueResponse resp = reviewService.getReviewQueue(1L);
        assertEquals(1, resp.getWords().size());
    }

    @Test
    void getReviewQueue_excludesMasteredFromDue() {
        // Mastered words are filtered at the SQL level by findActiveDueByUserIdAndTimeBefore.
        // Simulating that filter: even though we have a "due" bind that's mastered,
        // the repository returns empty due list (the filter excluded it).
        UserWordBind masteredBind = new UserWordBind();
        masteredBind.setId(3L);
        masteredBind.setUserId(1L);
        masteredBind.setWordId(30L);
        masteredBind.setIsStubborn(false);
        masteredBind.setMasteredAt(LocalDateTime.now().minusDays(1));
        masteredBind.setNextReviewTime(LocalDateTime.now().minusHours(1));

        when(userWordBindRepository.findActiveDueByUserIdAndTimeBefore(eq(1L), any()))
                .thenReturn(Collections.emptyList()); // filter excludes mastered
        when(userWordBindRepository.findByUserIdAndIsStubbornTrue(1L))
                .thenReturn(Collections.emptyList());

        ReviewService.ReviewQueueResponse resp = reviewService.getReviewQueue(1L);
        assertEquals(0, resp.getWords().size(), "Mastered words should not appear in default queue");
    }

    @Test
    void submitReview_delegatesToStudyService() {
        ReviewService.ReviewResultRequest req = new ReviewService.ReviewResultRequest();
        req.setUserId(1L);
        req.setWordId(10L);
        req.setStep1(3);
        req.setStep2(3);
        req.setStep3(3);
        req.setReactionTimeMs(5000);

        StudyService.SubmitResponse mockResp = new StudyService.SubmitResponse();
        mockResp.setGrade(2.8);
        when(studyService.submitUnifiedReview(any())).thenReturn(mockResp);

        StudyService.SubmitResponse resp = reviewService.submitReview(req);
        assertEquals(2.8, resp.getGrade(), 0.001);
        verify(studyService).submitUnifiedReview(any());
    }

    @Test
    void getNearForgotten_empty() {
        when(userWordBindRepository.findNearForgottenByUserId(eq(1L), any(), any()))
                .thenReturn(Collections.emptyList());

        List<ReviewService.NearForgottenWordDTO> result = reviewService.getNearForgotten(1L);
        assertTrue(result.isEmpty());
    }

    @Test
    void getNearForgotten_returnsWords() {
        UserWordBind bind = new UserWordBind();
        bind.setWordId(10L);
        bind.setStability(0.5);
        bind.setNextReviewTime(LocalDateTime.now().plusHours(6));

        when(userWordBindRepository.findNearForgottenByUserId(eq(1L), any(), any()))
                .thenReturn(List.of(bind));

        Word w = new Word();
        w.setId(10L);
        w.setWord("forget");
        w.setPhonetic("/fərˈɡet/");
        when(wordRepository.findByIdIn(anyList())).thenReturn(List.of(w));

        List<ReviewService.NearForgottenWordDTO> result = reviewService.getNearForgotten(1L);
        assertEquals(1, result.size());
        assertEquals("forget", result.get(0).getWord());
        assertTrue(result.get(0).getRetrievability() > 0);
        assertTrue(result.get(0).getRetrievability() <= 1);
    }
}
