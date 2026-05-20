package com.timo.words.modules.word.dto;

import lombok.Data;
import java.util.List;

@Data
public class WordDTO {
    private Long id;
    private String word;
    private String phonetic;
    private String pos;
    private String examType;
    private Integer collins;
    private Integer bncFreq;
    private Integer frqFreq;
    private List<MeaningDTO> meanings;
    private List<ExampleDTO> examples;
    private String familiarity;  // "new" | "learning" | "mastered" | "stubborn"
    private Double stability;
    private Double difficulty;

    @Data
    public static class MeaningDTO {
        private Long id;
        private String meaning;
        private String partOfSpeech;
    }

    @Data
    public static class ExampleDTO {
        private Long id;
        private String sentence;
        private String translation;
        private String source;
    }
}
