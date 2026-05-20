package com.timo.words.modules.word.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Entity
@Table(name = "words")
public class Word {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String word;

    private String phonetic;

    private String pos;

    @Column(name = "exam_type")
    private String examType;

    private Integer collins;

    @Column(name = "bnc_freq")
    private Integer bncFreq;

    @Column(name = "frq_freq")
    private Integer frqFreq;

    @JsonIgnore
    @OneToMany(mappedBy = "word", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<Meaning> meanings;

    @JsonIgnore
    @OneToMany(mappedBy = "word", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<Example> examples;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
