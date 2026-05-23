package com.timo.words.modules.review.service;

import com.timo.words.common.BusinessException;
import com.timo.words.common.ResultCode;
import com.timo.words.modules.study.entity.UserWordBind;
import com.timo.words.modules.study.repository.UserWordBindRepository;
import com.timo.words.modules.study.service.StudyService;
import com.timo.words.modules.user.entity.User;
import com.timo.words.modules.user.repository.UserRepository;
import com.timo.words.modules.word.dto.WordDTO;
import com.timo.words.modules.word.entity.Word;
import com.timo.words.modules.word.repository.WordRepository;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ReviewService {

    private final UserWordBindRepository userWordBindRepository;
    private final UserRepository userRepository;
    private final WordRepository wordRepository;
    private final StudyService studyService;

    @Data
    public static class ReviewWordDTO {
        private Long bindId;
        private Long wordId;
        private String word;
        private String phonetic;
        private String pos;
        private List<WordDTO.MeaningDTO> meanings;
        private List<WordDTO.ExampleDTO> examples;
        private boolean stubborn;
        private int consecutiveErrors;     // # of consecutive recent failures — drives badge intensity
        private double stability;
        private double difficulty;
    }

    @Data
    public static class ReviewQueueResponse {
        private List<ReviewWordDTO> words;
        private int stubbornCount;
        private int dueCount;
    }

    @Data
    public static class ReviewResultRequest {
        private Long userId;
        private Long wordId;
        private int step1, step2, step3;
        private int reactionTimeMs;
        private boolean spellingAttempted;
        private boolean spellingCorrect;
    }

    @Data
    public static class NearForgottenWordDTO {
        private Long wordId;
        private String word;
        private String phonetic;
        private LocalDateTime nextReviewTime;
        private double retrievability;
    }

    public ReviewQueueResponse getReviewQueue(Long userId) {
        return getReviewQueue(userId, 50);
    }

    public ReviewQueueResponse getReviewQueue(Long userId, int limit) {
        LocalDateTime now = LocalDateTime.now();

        // Get due words (next_review_time <= now), excluding mastered words.
        // Mastered words (masteredAt != null) drop out of the default queue —
        // FSRS will naturally re-surface them when their interval truly elapses,
        // but a "mastered counter" tracks them separately for motivation.
        List<UserWordBind> dueBinds = userWordBindRepository
                .findActiveDueByUserIdAndTimeBefore(userId, now);

        // Get stubborn words (these are NOT filtered by mastered — if a previously
        // mastered word becomes stubborn, the demotion in StudyService.updateMasteredStatus
        // will have already cleared masteredAt).
        List<UserWordBind> stubbornBinds = userWordBindRepository
                .findByUserIdAndIsStubbornTrue(userId);

        // Merge: stubborn words that aren't already in due list
        List<UserWordBind> allBinds = new ArrayList<>(dueBinds);
        var dueIds = dueBinds.stream().map(UserWordBind::getWordId).collect(Collectors.toSet());
        for (UserWordBind sb : stubbornBinds) {
            if (!dueIds.contains(sb.getWordId())) {
                allBinds.add(sb);
            }
        }

        // Sort: stubborn first, then by next_review_time ascending
        allBinds.sort(Comparator
                .comparing((UserWordBind b) -> !Boolean.TRUE.equals(b.getIsStubborn()))
                .thenComparing(b -> b.getNextReviewTime() != null ? b.getNextReviewTime() : LocalDateTime.MIN));

        // Apply limit to avoid overwhelming review sessions
        if (allBinds.size() > limit) {
            allBinds = new ArrayList<>(allBinds.subList(0, limit));
        }

        // Convert to DTO
        List<Long> wordIds = allBinds.stream().map(UserWordBind::getWordId).collect(Collectors.toList());
        var words = wordIds.isEmpty() ? List.<Word>of() : wordRepository.findByIdIn(wordIds);
        var wordMap = words.stream().collect(Collectors.toMap(Word::getId, w -> w));

        List<ReviewWordDTO> dtos = allBinds.stream().map(bind -> {
            ReviewWordDTO dto = new ReviewWordDTO();
            dto.setBindId(bind.getId());
            dto.setWordId(bind.getWordId());
            dto.setStability(bind.getStability() != null ? bind.getStability() : 1.0);
            dto.setDifficulty(bind.getDifficulty() != null ? bind.getDifficulty() : 5.0);
            dto.setStubborn(Boolean.TRUE.equals(bind.getIsStubborn()));
            dto.setConsecutiveErrors(bind.getConsecutiveErrors() != null ? bind.getConsecutiveErrors() : 0);

            Word w = wordMap.get(bind.getWordId());
            if (w != null) {
                dto.setWord(w.getWord());
                dto.setPhonetic(w.getPhonetic());
                dto.setPos(w.getPos());
                if (w.getMeanings() != null) {
                    dto.setMeanings(w.getMeanings().stream().map(m -> {
                        WordDTO.MeaningDTO md = new WordDTO.MeaningDTO();
                        md.setId(m.getId());
                        md.setMeaning(m.getMeaning());
                        md.setPartOfSpeech(m.getPartOfSpeech());
                        return md;
                    }).collect(Collectors.toList()));
                }
                if (w.getExamples() != null && !w.getExamples().isEmpty()) {
                    dto.setExamples(w.getExamples().stream().map(e -> {
                        WordDTO.ExampleDTO ed = new WordDTO.ExampleDTO();
                        ed.setId(e.getId());
                        ed.setSentence(e.getSentence());
                        ed.setTranslation(e.getTranslation());
                        ed.setSource(e.getSource());
                        return ed;
                    }).collect(Collectors.toList()));
                }
            }
            return dto;
        }).collect(Collectors.toList());

        ReviewQueueResponse resp = new ReviewQueueResponse();
        resp.setWords(dtos);
        resp.setDueCount(dueBinds.size());
        resp.setStubbornCount(stubbornBinds.size());
        return resp;
    }

    public StudyService.SubmitResponse submitReview(ReviewResultRequest req) {
        StudyService.UnifiedReviewSubmitRequest submitReq = new StudyService.UnifiedReviewSubmitRequest();
        submitReq.setUserId(req.getUserId());
        submitReq.setWordId(req.getWordId());
        submitReq.setStep1(req.getStep1());
        submitReq.setStep2(req.getStep2());
        submitReq.setStep3(req.getStep3());
        submitReq.setReactionTimeMs(req.getReactionTimeMs());
        submitReq.setSpellingAttempted(req.isSpellingAttempted());
        submitReq.setSpellingCorrect(req.isSpellingCorrect());
        return studyService.submitUnifiedReview(submitReq);
    }

    public List<NearForgottenWordDTO> getNearForgotten(Long userId) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime deadline = now.plusHours(24);

        List<UserWordBind> binds = userWordBindRepository
                .findNearForgottenByUserId(userId, now, deadline);

        // Limit to top 5
        List<UserWordBind> top5 = binds.stream().limit(5).collect(Collectors.toList());
        if (top5.isEmpty()) return List.of();

        // Fetch word data
        List<Long> wordIds = top5.stream().map(UserWordBind::getWordId).collect(Collectors.toList());
        var wordMap = wordIds.isEmpty() ? Map.<Long, Word>of()
                : wordRepository.findByIdIn(wordIds).stream().collect(Collectors.toMap(Word::getId, w -> w));

        return top5.stream().map(bind -> {
            NearForgottenWordDTO dto = new NearForgottenWordDTO();
            dto.setWordId(bind.getWordId());
            dto.setNextReviewTime(bind.getNextReviewTime());

            Word w = wordMap.get(bind.getWordId());
            if (w != null) {
                dto.setWord(w.getWord());
                dto.setPhonetic(w.getPhonetic());
            }

            // FSRS retrievability: R = exp(ln(0.9) * deltaT / S)
            double stability = bind.getStability() != null ? bind.getStability() : 1.0;
            long deltaHours = java.time.Duration.between(now, bind.getNextReviewTime()).toHours();
            double r = Math.exp(Math.log(0.9) * deltaHours / (stability * 24));
            dto.setRetrievability(Math.max(0, Math.min(1, r)));

            return dto;
        }).collect(Collectors.toList());
    }
}
