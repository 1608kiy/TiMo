package com.timo.words.modules.study.service;

import com.timo.words.algorithm.common.EditDistance;
import com.timo.words.algorithm.fsrs.ErrorReinforcementHandler;
import com.timo.words.algorithm.fsrs.ReviewResult;
import com.timo.words.algorithm.fsrs.Scheduler;
import com.timo.words.algorithm.scoring.GradeMapper;
import com.timo.words.common.BusinessException;
import com.timo.words.common.ResultCode;
import com.timo.words.modules.study.entity.QuizRecord;
import com.timo.words.modules.study.entity.UserWordBind;
import com.timo.words.modules.study.repository.QuizRecordRepository;
import com.timo.words.modules.study.repository.UserWordBindRepository;
import com.timo.words.modules.user.entity.User;
import com.timo.words.modules.user.repository.UserRepository;
import com.timo.words.modules.word.entity.Word;
import com.timo.words.modules.word.repository.WordRepository;
import com.timo.words.modules.calendar.service.CalendarService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StudyService {

    private final UserWordBindRepository userWordBindRepository;
    private final QuizRecordRepository quizRecordRepository;
    private final UserRepository userRepository;
    private final WordRepository wordRepository;
    private final CalendarService calendarService;

    // Default for the rare case where a word entity is missing (deleted concurrently).
    // BASELINE_WORD_LENGTH from DF; using 6 keeps RT normalization neutral.
    private static final int DEFAULT_WORD_LENGTH = 6;

    // --- DTOs ---

    @Data
    public static class QuickMemorySubmitRequest {
        private Long userId;
        private Long wordId;
        private boolean recognized;
        private boolean verifiedCorrect;
        private int reactionTimeMs;
    }

    @Data
    public static class SubmitResponse {
        private double grade;
        private double newStability;
        private double newDifficulty;
        private double newRetrievability;
        private double df;
        private boolean needsSpelling;
        private LocalDateTime nextReviewTime;
        private boolean isStubborn;
        private boolean suggestDeepLearning;
    }

    @Data
    public static class ContextDeepSubmitRequest {
        private Long userId;
        private Long wordId;
        private int s2, s3, s4, s5;
        private int hintTotal;
        private int reactionTimeMs;
    }

    @Data
    public static class ContextDeepGroupItem {
        @com.fasterxml.jackson.annotation.JsonProperty("word_id")
        private Long wordId;
        @com.fasterxml.jackson.annotation.JsonProperty("s2_raw")
        private int s2Raw;
        @com.fasterxml.jackson.annotation.JsonProperty("s3_raw")
        private int s3Raw;
        @com.fasterxml.jackson.annotation.JsonProperty("s4_raw")
        private int s4Raw;
        @com.fasterxml.jackson.annotation.JsonProperty("s5_raw")
        private int s5Raw;
        @com.fasterxml.jackson.annotation.JsonProperty("hint_total")
        private int hintTotal;
        @com.fasterxml.jackson.annotation.JsonProperty("dwell_time_ms")
        private int dwellTimeMs;
    }

    @Data
    public static class ContextDeepGroupSubmitRequest {
        @com.fasterxml.jackson.annotation.JsonProperty("user_id")
        private Long userId;
        @com.fasterxml.jackson.annotation.JsonProperty("group_results")
        private java.util.List<ContextDeepGroupItem> groupResults;
    }

    @Data
    public static class UnifiedReviewSubmitRequest {
        private Long userId;
        private Long wordId;
        private int step1, step2, step3;
        private int reactionTimeMs;
        private boolean spellingAttempted;
        private boolean spellingCorrect;
    }

    @Data
    public static class ReverseRecallSubmitRequest {
        private Long userId;
        private Long wordId;
        private String userInput;
        private int reactionTimeMs;
        /**
         * Hint level used during this attempt:
         *   0 = no hint
         *   1 = first letter revealed
         *   2 = first half (ceil(L/2) letters) revealed
         */
        private int hintLevel;
    }

    @Data
    public static class ReverseRecallCandidate {
        private Long wordId;
        private String word;
        private String phonetic;
        private List<MeaningView> meanings;
        private Double stability;
        private Integer reviewCount;

        @Data
        public static class MeaningView {
            private Long id;
            private String meaning;
            private String partOfSpeech;
        }
    }

    // --- Service Methods ---

    @Transactional
    public SubmitResponse submitQuickMemory(QuickMemorySubmitRequest req) {
        User user = getUser(req.getUserId());
        UserWordBind bind = getOrCreateBind(req.getUserId(), req.getWordId());

        // 5-tier grade with RT differentiation (was 3-tier 4/2/1).
        double grade = GradeMapper.mapQuickMemory(req.isRecognized(), req.isVerifiedCorrect(),
                req.getReactionTimeMs());
        double reactionTimeSec = req.getReactionTimeMs() / 1000.0;
        int wordLength = getWordLength(req.getWordId());

        int correctCount = (int) quizRecordRepository.countByUserIdAndWordIdAndGradeGreaterThanEqual(
                req.getUserId(), req.getWordId(), 3.0);
        long totalAttempts = quizRecordRepository.countByUserIdAndWordId(req.getUserId(), req.getWordId());

        ReviewResult result = Scheduler.review(
                bind, grade, "quick_memory",
                reactionTimeSec, 0,
                correctCount, totalAttempts,
                user.getMuInit(), user.getSigmaInit(),
                wordLength);

        applyResult(bind, result, true);
        ErrorReinforcementHandler.apply(bind, "quick_memory", grade, null, 0, true, false);
        updateMasteredStatus(bind, grade);
        userWordBindRepository.save(bind);

        saveQuizRecord(req.getUserId(), req.getWordId(), "quick_memory",
                grade, null, null, 0, req.getReactionTimeMs());

        SubmitResponse resp = mapResponse(result, bind, grade);
        // Scenario 1: Agent intervention — suggest deep learning for stubborn words
        if (bind.getConsecutiveErrors() != null && bind.getConsecutiveErrors() >= 3) {
            resp.setSuggestDeepLearning(true);
        }
        // 自动打卡
        calendarService.autoCheckin(req.getUserId(), 1);
        return resp;
    }

    @Transactional
    public SubmitResponse submitContextDeepWord(ContextDeepSubmitRequest req) {
        User user = getUser(req.getUserId());
        UserWordBind bind = getOrCreateBind(req.getUserId(), req.getWordId());

        double grade = GradeMapper.mapContextDeep(req.getS2(), req.getS3(), req.getS4(), req.getS5());
        double reactionTimeSec = req.getReactionTimeMs() / 1000.0;
        int wordLength = getWordLength(req.getWordId());

        int correctCount = (int) quizRecordRepository.countByUserIdAndWordIdAndGradeGreaterThanEqual(
                req.getUserId(), req.getWordId(), 3.0);
        long totalAttempts = quizRecordRepository.countByUserIdAndWordId(req.getUserId(), req.getWordId());

        ReviewResult result = Scheduler.review(
                bind, grade, "context_deep",
                reactionTimeSec, req.getHintTotal(),
                correctCount, totalAttempts,
                user.getMuInit(), user.getSigmaInit(),
                wordLength);

        boolean suppressReviewCount = req.getHintTotal() >= 4;
        applyResult(bind, result, !suppressReviewCount);
        ErrorReinforcementHandler.apply(bind, "context_deep", grade,
                new int[]{req.getS2(), req.getS3(), req.getS4(), req.getS5()},
                req.getHintTotal(), true, false);
        updateMasteredStatus(bind, grade);
        userWordBindRepository.save(bind);

        String stepResults = String.format("{\"s2\":%d,\"s3\":%d,\"s4\":%d,\"s5\":%d}",
                req.getS2(), req.getS3(), req.getS4(), req.getS5());
        saveQuizRecord(req.getUserId(), req.getWordId(), "context_deep",
                grade, grade, stepResults, req.getHintTotal(), req.getReactionTimeMs());

        return mapResponse(result, bind, grade);
    }

    @Transactional
    public void submitContextDeepGroup(ContextDeepGroupSubmitRequest req) {
        if (req.getGroupResults() == null || req.getGroupResults().isEmpty()) return;

        // Batch-fetch user and all binds upfront to avoid N+1 queries
        User user = getUser(req.getUserId());
        List<Long> wordIds = req.getGroupResults().stream()
                .map(ContextDeepGroupItem::getWordId)
                .collect(Collectors.toList());
        Map<Long, UserWordBind> bindMap = userWordBindRepository.findByUserIdAndWordIdIn(req.getUserId(), wordIds)
                .stream().collect(Collectors.toMap(UserWordBind::getWordId, b -> b));

        // Batch-fetch quiz record stats to avoid N+1 in the loop
        Map<Long, Long> totalAttemptsMap = quizRecordRepository.countByUserIdAndWordIdIn(req.getUserId(), wordIds)
                .stream().collect(Collectors.toMap(r -> (Long) r[0], r -> (Long) r[1]));
        Map<Long, Long> correctCountMap = quizRecordRepository.countByUserIdAndWordIdInAndGradeGte(req.getUserId(), wordIds, 3.0)
                .stream().collect(Collectors.toMap(r -> (Long) r[0], r -> (Long) r[1]));

        // Batch-fetch word lengths (single round-trip) for RT normalization
        Map<Long, Integer> wordLengthMap = getWordLengthsByIds(wordIds);

        for (ContextDeepGroupItem item : req.getGroupResults()) {
            long totalAttempts = totalAttemptsMap.getOrDefault(item.getWordId(), 0L);
            int correctCount = correctCountMap.getOrDefault(item.getWordId(), 0L).intValue();
            int wordLength = wordLengthMap.getOrDefault(item.getWordId(), DEFAULT_WORD_LENGTH);
            processContextDeepWord(user, bindMap, item.getWordId(),
                    item.getS2Raw(), item.getS3Raw(), item.getS4Raw(), item.getS5Raw(),
                    item.getHintTotal(), item.getDwellTimeMs(),
                    correctCount, totalAttempts, wordLength);
        }

        // Batch save all modified binds
        userWordBindRepository.saveAll(bindMap.values());
        // 自动打卡
        calendarService.autoCheckin(req.getUserId(), req.getGroupResults().size());
    }

    private void processContextDeepWord(User user, Map<Long, UserWordBind> bindMap,
                                         Long wordId, int s2, int s3, int s4, int s5,
                                         int hintTotal, int reactionTimeMs,
                                         int correctCount, long totalAttempts, int wordLength) {
        UserWordBind bind = bindMap.computeIfAbsent(wordId, wid -> {
            UserWordBind newBind = new UserWordBind();
            newBind.setUserId(user.getId());
            newBind.setWordId(wid);
            newBind.setStability(Scheduler.INITIAL_STABILITY);
            newBind.setDifficulty(Scheduler.INITIAL_DIFFICULTY);
            newBind.setDifficultyInitial(Scheduler.INITIAL_DIFFICULTY);
            newBind.setReviewCount(0);
            newBind.setConsecutiveErrors(0);
            newBind.setConsecutiveCorrectSameMode(0);
            return newBind;
        });

        double grade = GradeMapper.mapContextDeep(s2, s3, s4, s5);
        double reactionTimeSec = reactionTimeMs / 1000.0;

        ReviewResult result = Scheduler.review(
                bind, grade, "context_deep",
                reactionTimeSec, hintTotal,
                correctCount, totalAttempts,
                user.getMuInit(), user.getSigmaInit(),
                wordLength);

        boolean suppressReviewCount = hintTotal >= 4;
        applyResult(bind, result, !suppressReviewCount);
        ErrorReinforcementHandler.apply(bind, "context_deep", grade,
                new int[]{s2, s3, s4, s5}, hintTotal, true, false);
        updateMasteredStatus(bind, grade);

        String stepResults = String.format("{\"s2\":%d,\"s3\":%d,\"s4\":%d,\"s5\":%d}", s2, s3, s4, s5);
        saveQuizRecord(user.getId(), wordId, "context_deep",
                grade, grade, stepResults, hintTotal, reactionTimeMs);
    }

    @Transactional
    public SubmitResponse submitUnifiedReview(UnifiedReviewSubmitRequest req) {
        User user = getUser(req.getUserId());
        UserWordBind bind = getOrCreateBind(req.getUserId(), req.getWordId());

        double grade = GradeMapper.mapUnifiedReview(req.getStep1(), req.getStep2(), req.getStep3());
        double reactionTimeSec = req.getReactionTimeMs() / 1000.0;

        boolean needsSpelling = grade < 2.5;
        if (needsSpelling && !req.isSpellingAttempted()) {
            // Return early - spelling step needed
            SubmitResponse resp = new SubmitResponse();
            resp.setGrade(grade);
            resp.setNeedsSpelling(true);
            resp.setNewStability(bind.getStability() != null ? bind.getStability() : Scheduler.INITIAL_STABILITY);
            resp.setNewDifficulty(bind.getDifficulty() != null ? bind.getDifficulty() : Scheduler.INITIAL_DIFFICULTY);
            resp.setNewRetrievability(1.0);
            resp.setDf(1.0);
            resp.setNextReviewTime(bind.getNextReviewTime());
            resp.setStubborn(bind.getIsStubborn() != null && bind.getIsStubborn());
            return resp;
        }

        int correctCount = (int) quizRecordRepository.countByUserIdAndWordIdAndGradeGreaterThanEqual(
                req.getUserId(), req.getWordId(), 3.0);
        long totalAttempts = quizRecordRepository.countByUserIdAndWordId(req.getUserId(), req.getWordId());
        int wordLength = getWordLength(req.getWordId());

        ReviewResult result = Scheduler.review(
                bind, grade, "unified_review",
                reactionTimeSec, 0,
                correctCount, totalAttempts,
                user.getMuInit(), user.getSigmaInit(),
                wordLength);

        applyResult(bind, result, true);
        ErrorReinforcementHandler.apply(bind, "unified_review", grade,
                null, 0, req.isSpellingCorrect(), req.isSpellingAttempted());
        updateMasteredStatus(bind, grade);
        userWordBindRepository.save(bind);

        saveQuizRecord(req.getUserId(), req.getWordId(), "unified_review",
                grade, null, null, 0, req.getReactionTimeMs());

        // 自动打卡
        calendarService.autoCheckin(req.getUserId(), 1);

        return mapResponse(result, bind, grade);
    }

    /**
     * Reverse-recall mode (Chinese → English active recall).
     *
     * The learner sees a Chinese gloss and must type the matching English word.
     * Grade mapping derives from edit distance + hint level:
     *
     *   d == 0  &&  hint == 0      → 4.0   (typed cleanly, no help)
     *   d == 0  &&  hint == 1      → 3.5   (correct after first-letter hint)
     *   d == 0  &&  hint >= 2      → 3.0   (correct after half-word hint)
     *   d == 1                     → 2.5   (single-letter typo — partial credit)
     *   d >= 2  ||  blank          → 1.0   (essentially didn't know it)
     *
     * Shares the DF formula with quick_memory / unified_review (λ_rt × λ_acc) — see
     * {@link com.timo.words.algorithm.df.DynamicForgettingFactor#REVERSE_RECALL}.
     */
    @Transactional
    public SubmitResponse submitReverseRecall(ReverseRecallSubmitRequest req) {
        User user = getUser(req.getUserId());
        UserWordBind bind = getOrCreateBind(req.getUserId(), req.getWordId());

        Word word = wordRepository.findById(req.getWordId())
                .orElseThrow(() -> new BusinessException(ResultCode.WORD_NOT_FOUND));
        int distance = EditDistance.normalizedDistance(req.getUserInput(), word.getWord());
        double grade = mapReverseRecallGrade(distance, req.getHintLevel());

        double reactionTimeSec = req.getReactionTimeMs() / 1000.0;
        int wordLength = word.getWord() != null ? word.getWord().length() : DEFAULT_WORD_LENGTH;

        int correctCount = (int) quizRecordRepository.countByUserIdAndWordIdAndGradeGreaterThanEqual(
                req.getUserId(), req.getWordId(), 3.0);
        long totalAttempts = quizRecordRepository.countByUserIdAndWordId(req.getUserId(), req.getWordId());

        ReviewResult result = Scheduler.review(
                bind, grade, "reverse_recall",
                reactionTimeSec, 0,
                correctCount, totalAttempts,
                user.getMuInit(), user.getSigmaInit(),
                wordLength);

        applyResult(bind, result, true);
        ErrorReinforcementHandler.apply(bind, "reverse_recall", grade, null, 0, true, false);
        updateMasteredStatus(bind, grade);
        userWordBindRepository.save(bind);

        String stepResults = String.format("{\"distance\":%d,\"hintLevel\":%d}",
                distance, req.getHintLevel());
        saveQuizRecord(req.getUserId(), req.getWordId(), "reverse_recall",
                grade, null, stepResults, 0, req.getReactionTimeMs());

        // 自动打卡
        calendarService.autoCheckin(req.getUserId(), 1);

        return mapResponse(result, bind, grade);
    }

    /**
     * Pure grade mapping for reverse recall — broken out so tests can exercise it
     * without mocking the entire FSRS / DF stack.
     */
    static double mapReverseRecallGrade(int distance, int hintLevel) {
        if (distance == 0) {
            if (hintLevel <= 0) return 4.0;
            if (hintLevel == 1) return 3.5;
            return 3.0;
        }
        if (distance == 1) return 2.5;
        return 1.0;
    }

    /**
     * Candidate pool for reverse recall sessions. Returns "half-learned" words:
     * reviewCount ≥ 3, not yet mastered, stability in [1, 21] days — exactly the
     * memory state where active recall is most productive (too fresh = guessing,
     * too solid = nothing to gain).
     */
    @Transactional(readOnly = true)
    public List<ReverseRecallCandidate> getReverseRecallCandidates(Long userId, int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 50));
        List<UserWordBind> binds = userWordBindRepository.findReverseRecallCandidates(
                userId, PageRequest.of(0, safeLimit));
        if (binds.isEmpty()) return List.of();

        List<Long> wordIds = binds.stream().map(UserWordBind::getWordId).collect(Collectors.toList());
        Map<Long, Word> wordMap = wordRepository.findByIdIn(wordIds).stream()
                .collect(Collectors.toMap(Word::getId, Function.identity()));

        return binds.stream()
                .map(b -> {
                    Word w = wordMap.get(b.getWordId());
                    if (w == null) return null;
                    ReverseRecallCandidate c = new ReverseRecallCandidate();
                    c.setWordId(w.getId());
                    c.setWord(w.getWord());
                    c.setPhonetic(w.getPhonetic());
                    c.setStability(b.getStability());
                    c.setReviewCount(b.getReviewCount());
                    if (w.getMeanings() != null) {
                        c.setMeanings(w.getMeanings().stream().map(m -> {
                            ReverseRecallCandidate.MeaningView mv = new ReverseRecallCandidate.MeaningView();
                            mv.setId(m.getId());
                            mv.setMeaning(m.getMeaning());
                            mv.setPartOfSpeech(m.getPartOfSpeech());
                            return mv;
                        }).collect(Collectors.toList()));
                    }
                    return c;
                })
                .filter(c -> c != null)
                .collect(Collectors.toList());
    }

    /**
     * Fetch the character length of a word for FSRS RT-normalization.
     * Uses a length-only projection query to avoid loading the full Word entity.
     * Falls back to {@link #DEFAULT_WORD_LENGTH} if the word is missing.
     */
    private int getWordLength(Long wordId) {
        return wordRepository.findWordLengthById(wordId).orElse(DEFAULT_WORD_LENGTH);
    }

    /**
     * Batch version of {@link #getWordLength(Long)} — single SQL round-trip for a list of IDs.
     */
    private Map<Long, Integer> getWordLengthsByIds(List<Long> wordIds) {
        return wordRepository.findWordLengthsByIds(wordIds).stream()
                .collect(Collectors.toMap(
                        r -> (Long) r[0],
                        r -> ((Number) r[1]).intValue()));
    }

    private User getUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ResultCode.USER_NOT_FOUND));
    }

    private UserWordBind getOrCreateBind(Long userId, Long wordId) {
        return userWordBindRepository.findByUserIdAndWordId(userId, wordId)
                .orElseGet(() -> {
                    try {
                        UserWordBind bind = new UserWordBind();
                        bind.setUserId(userId);
                        bind.setWordId(wordId);
                        bind.setStability(Scheduler.INITIAL_STABILITY);
                        bind.setDifficulty(Scheduler.INITIAL_DIFFICULTY);
                        bind.setDifficultyInitial(Scheduler.INITIAL_DIFFICULTY);
                        bind.setReviewCount(0);
                        bind.setConsecutiveErrors(0);
                        bind.setConsecutiveCorrectSameMode(0);
                        return userWordBindRepository.save(bind);
                    } catch (DataIntegrityViolationException e) {
                        return userWordBindRepository.findByUserIdAndWordId(userId, wordId)
                                .orElseThrow(() -> new BusinessException(ResultCode.STUDY_SESSION_ERROR));
                    }
                });
    }

    private void applyResult(UserWordBind bind, ReviewResult result, boolean incrementReviewCount) {
        bind.setStability(result.newStability());
        bind.setDifficulty(result.newDifficulty());
        bind.setRetrievability(result.newRetrievability());
        bind.setNextReviewTime(LocalDateTime.now().plusSeconds((long)(result.nextReviewDays() * 86400)));
        if (incrementReviewCount) {
            bind.setReviewCount((bind.getReviewCount() != null ? bind.getReviewCount() : 0) + 1);
        }
        bind.setLastStudyTime(LocalDateTime.now());
        // Note: stubborn mark/unmark is handled by ErrorReinforcementHandler
    }

    /**
     * Mastered state transition — promote / demote based on current review outcome.
     *
     * Promotion criteria (all must hold):
     *   - grade ≥ 3.5 (this review was a solid success)
     *   - stability > 21 days (FSRS believes the user can wait 3+ weeks)
     *   - consecutiveErrors == 0 (no current error streak)
     *
     * Demotion: grade < 3.0 clears masteredAt regardless of stability — the user
     * actually failed, so we should not pretend they have it down.
     *
     * Must be called AFTER applyResult and ErrorReinforcementHandler so consecutiveErrors
     * reflects the current submission.
     */
    private void updateMasteredStatus(UserWordBind bind, double grade) {
        if (grade >= 3.5
                && bind.getStability() != null && bind.getStability() > 21.0
                && (bind.getConsecutiveErrors() == null || bind.getConsecutiveErrors() == 0)) {
            if (bind.getMasteredAt() == null) {
                bind.setMasteredAt(LocalDateTime.now());
            }
        } else if (grade < 3.0 && bind.getMasteredAt() != null) {
            bind.setMasteredAt(null);
        }
    }

    private void saveQuizRecord(Long userId, Long wordId, String mode,
                                double grade, Double compositeGrade,
                                String stepResults, int hintTotal, int reactionTimeMs) {
        QuizRecord record = new QuizRecord();
        record.setUserId(userId);
        record.setWordId(wordId);
        record.setStudyMode(mode);
        record.setGrade(grade);
        record.setCompositeGrade(compositeGrade);
        record.setStepResults(stepResults);
        record.setHintTotal(hintTotal);
        record.setReactionTimeMs(reactionTimeMs);
        quizRecordRepository.save(record);
    }

    private SubmitResponse mapResponse(ReviewResult result, UserWordBind bind, double grade) {
        SubmitResponse resp = new SubmitResponse();
        resp.setGrade(grade);
        resp.setNewStability(result.newStability());
        resp.setNewDifficulty(result.newDifficulty());
        resp.setNewRetrievability(result.newRetrievability());
        resp.setDf(result.df());
        resp.setNextReviewTime(bind.getNextReviewTime());
        resp.setStubborn(bind.getIsStubborn() != null && bind.getIsStubborn());
        resp.setNeedsSpelling(false);
        return resp;
    }
}
