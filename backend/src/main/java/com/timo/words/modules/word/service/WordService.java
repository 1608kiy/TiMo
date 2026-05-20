package com.timo.words.modules.word.service;

import com.timo.words.common.BusinessException;
import com.timo.words.common.ResultCode;
import com.timo.words.modules.study.entity.UserWordBind;
import com.timo.words.modules.study.repository.UserWordBindRepository;
import com.timo.words.modules.word.dto.WordDTO;
import com.timo.words.modules.word.entity.Word;
import com.timo.words.modules.word.repository.WordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class WordService {

    private final WordRepository wordRepository;
    private final UserWordBindRepository userWordBindRepository;

    public Page<WordDTO> list(String keyword, String examType, String pos, String familiarity, int page, int size) {
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by("word").ascending());
        Page<Word> words = wordRepository.searchByKeywordAndExamType(keyword, examType, pos, pageRequest);

        if (familiarity == null || familiarity.isBlank()) {
            return words.map(w -> toDTO(w, null));
        }

        // Two-step: fetch all words on this page, get their binds, filter by familiarity
        List<Long> wordIds = words.getContent().stream().map(Word::getId).collect(Collectors.toList());
        // We need userId — but list() doesn't have it. We'll do familiarity filter at controller level.
        // For now, return all with null familiarity when called without userId context.
        return words.map(w -> toDTO(w, null));
    }

    public Page<WordDTO> listWithFamiliarity(String keyword, String examType, String pos, String familiarity, Long userId, int page, int size) {
        // Fetch all matching words (without pagination) to correctly filter and count by familiarity
        PageRequest allRequest = PageRequest.of(0, 10000, Sort.by("word").ascending());
        Page<Word> allWords = wordRepository.searchByKeywordAndExamType(keyword, examType, pos, allRequest);

        if (familiarity == null || familiarity.isBlank()) {
            long total = allWords.getTotalElements();
            int start = page * size;
            List<Word> content = start < allWords.getContent().size()
                    ? allWords.getContent().subList(start, Math.min(start + size, allWords.getContent().size()))
                    : List.of();
            List<WordDTO> dtos = content.stream().map(w -> toDTO(w, null)).collect(Collectors.toList());
            return new PageImpl<>(dtos, PageRequest.of(page, size), total);
        }

        List<Long> wordIds = allWords.getContent().stream().map(Word::getId).collect(Collectors.toList());
        Map<Long, UserWordBind> bindMap = userWordBindRepository.findByUserIdAndWordIdIn(userId, wordIds).stream()
                .collect(Collectors.toMap(UserWordBind::getWordId, b -> b));

        // Filter by familiarity across ALL matching words
        List<Word> filtered = allWords.getContent().stream()
                .filter(w -> matchesFamiliarity(bindMap.get(w.getId()), familiarity))
                .collect(Collectors.toList());

        long total = filtered.size();
        int start = page * size;
        List<Word> paged = start < filtered.size()
                ? filtered.subList(start, Math.min(start + size, filtered.size()))
                : List.of();
        List<WordDTO> dtos = paged.stream().map(w -> toDTO(w, bindMap.get(w.getId()))).collect(Collectors.toList());
        return new PageImpl<>(dtos, PageRequest.of(page, size), total);
    }

    private boolean matchesFamiliarity(UserWordBind bind, String familiarity) {
        return switch (familiarity) {
            case "new" -> bind == null;
            case "learning" -> bind != null && bind.getStability() != null && bind.getStability() < 1.2 && !Boolean.TRUE.equals(bind.getIsStubborn());
            case "mastered" -> bind != null && bind.getStability() != null && bind.getStability() >= 1.2 && !Boolean.TRUE.equals(bind.getIsStubborn());
            case "stubborn" -> bind != null && Boolean.TRUE.equals(bind.getIsStubborn());
            default -> true;
        };
    }

    public WordDTO getWordFsrsState(Long wordId, Long userId) {
        Word word = wordRepository.findById(wordId)
                .orElseThrow(() -> new BusinessException(ResultCode.WORD_NOT_FOUND));
        UserWordBind bind = userWordBindRepository.findByUserIdAndWordId(userId, wordId).orElse(null);
        return toDTO(word, bind);
    }

    private String classifyFamiliarity(UserWordBind bind) {
        if (Boolean.TRUE.equals(bind.getIsStubborn())) return "stubborn";
        if (bind.getStability() != null && bind.getStability() >= 1.2) return "mastered";
        return "learning";
    }

    public WordDTO detail(Long id) {
        Word word = wordRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ResultCode.WORD_NOT_FOUND));
        return toDTO(word);
    }

    public List<WordDTO> search(String keyword) {
        PageRequest pageRequest = PageRequest.of(0, 20, Sort.by("word").ascending());
        return wordRepository.searchByKeywordAndExamType(keyword, null, null, pageRequest)
                .map(this::toDTO)
                .getContent();
    }

    public List<WordDTO> getByIds(List<Long> ids) {
        return wordRepository.findByIdIn(ids).stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    public long count() {
        return wordRepository.count();
    }

    public long countByExamType(String examType) {
        return wordRepository.countByExamType(examType);
    }

    public List<Object[]> countByExamTypeGroup() {
        return wordRepository.countByExamTypeGroup();
    }

    private WordDTO toDTO(Word word) {
        return toDTO(word, null);
    }

    private WordDTO toDTO(Word word, UserWordBind bind) {
        WordDTO dto = new WordDTO();
        dto.setId(word.getId());
        dto.setWord(word.getWord());
        dto.setPhonetic(word.getPhonetic());
        dto.setPos(word.getPos());
        dto.setExamType(word.getExamType());
        dto.setCollins(word.getCollins());
        dto.setBncFreq(word.getBncFreq());
        dto.setFrqFreq(word.getFrqFreq());

        if (bind != null) {
            dto.setStability(bind.getStability());
            dto.setDifficulty(bind.getDifficulty());
            dto.setFamiliarity(classifyFamiliarity(bind));
        } else {
            dto.setFamiliarity("new");
        }

        if (word.getMeanings() != null) {
            dto.setMeanings(word.getMeanings().stream().map(m -> {
                WordDTO.MeaningDTO md = new WordDTO.MeaningDTO();
                md.setId(m.getId());
                md.setMeaning(m.getMeaning());
                md.setPartOfSpeech(m.getPartOfSpeech());
                return md;
            }).collect(Collectors.toList()));
        }

        if (word.getExamples() != null) {
            dto.setExamples(word.getExamples().stream().map(e -> {
                WordDTO.ExampleDTO ed = new WordDTO.ExampleDTO();
                ed.setId(e.getId());
                ed.setSentence(e.getSentence());
                ed.setTranslation(e.getTranslation());
                ed.setSource(e.getSource());
                return ed;
            }).collect(Collectors.toList()));
        }

        return dto;
    }
}
