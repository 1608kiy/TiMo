package com.timo.words.modules.word.service;

import com.timo.words.common.BusinessException;
import com.timo.words.modules.word.dto.WordDTO;
import com.timo.words.modules.word.entity.Meaning;
import com.timo.words.modules.word.entity.Word;
import com.timo.words.modules.word.repository.WordRepository;
import com.timo.words.modules.study.entity.UserWordBind;
import com.timo.words.modules.study.repository.UserWordBindRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WordServiceTest {

    @Mock private WordRepository wordRepository;
    @Mock private UserWordBindRepository userWordBindRepository;
    @InjectMocks private WordService wordService;

    private Word testWord;

    @BeforeEach
    void setUp() {
        testWord = new Word();
        testWord.setId(1L);
        testWord.setWord("abandon");
        testWord.setPhonetic("/əˈbændən/");
        testWord.setPos("v.");
        testWord.setExamType("cet4");

        Meaning m = new Meaning();
        m.setId(1L);
        m.setMeaning("放弃");
        m.setPartOfSpeech("v.");
        testWord.setMeanings(List.of(m));
    }

    @Test
    void detail_found() {
        when(wordRepository.findById(1L)).thenReturn(Optional.of(testWord));

        WordDTO dto = wordService.detail(1L);

        assertEquals("abandon", dto.getWord());
        assertEquals("/əˈbændən/", dto.getPhonetic());
        assertEquals("new", dto.getFamiliarity());
    }

    @Test
    void detail_notFound_throws() {
        when(wordRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(BusinessException.class, () -> wordService.detail(99L));
    }

    @Test
    void search_returnsResults() {
        Page<Word> page = new PageImpl<>(List.of(testWord));
        when(wordRepository.searchByKeywordAndExamType(eq("aban"), eq(null), eq(null), any(PageRequest.class)))
                .thenReturn(page);

        List<WordDTO> results = wordService.search("aban");
        assertEquals(1, results.size());
        assertEquals("abandon", results.get(0).getWord());
    }

    @Test
    void getByIds_returnsMultiple() {
        Word w2 = new Word();
        w2.setId(2L);
        w2.setWord("basic");

        when(wordRepository.findByIdIn(List.of(1L, 2L))).thenReturn(List.of(testWord, w2));

        List<WordDTO> results = wordService.getByIds(List.of(1L, 2L));
        assertEquals(2, results.size());
    }

    @Test
    void count_delegatesToRepository() {
        when(wordRepository.count()).thenReturn(1000L);
        assertEquals(1000L, wordService.count());
    }

    @Test
    void countByExamType_delegatesToRepository() {
        when(wordRepository.countByExamType("cet4")).thenReturn(500L);
        assertEquals(500L, wordService.countByExamType("cet4"));
    }

    @Test
    void getWordFsrsState_withBind() {
        when(wordRepository.findById(1L)).thenReturn(Optional.of(testWord));

        UserWordBind bind = new UserWordBind();
        bind.setUserId(1L);
        bind.setWordId(1L);
        bind.setStability(1.5);
        bind.setDifficulty(5.0);
        when(userWordBindRepository.findByUserIdAndWordId(1L, 1L)).thenReturn(Optional.of(bind));

        WordDTO dto = wordService.getWordFsrsState(1L, 1L);
        assertEquals("mastered", dto.getFamiliarity());
        assertEquals(1.5, dto.getStability());
    }

    @Test
    void getWordFsrsState_noBind_returnsNew() {
        when(wordRepository.findById(1L)).thenReturn(Optional.of(testWord));
        when(userWordBindRepository.findByUserIdAndWordId(1L, 1L)).thenReturn(Optional.empty());

        WordDTO dto = wordService.getWordFsrsState(1L, 1L);
        assertEquals("new", dto.getFamiliarity());
    }
}
