package com.timo.words.modules.word.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity
@Table(name = "meanings")
public class Meaning {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "word_id", nullable = false)
    private Word word;

    @Column(nullable = false)
    private String meaning;

    @Column(name = "part_of_speech")
    private String partOfSpeech;

    @Column(name = "sort_order")
    private Integer sortOrder = 0;

    /**
     * Meaning priority weight for the multi-meaning rotation in context_deep mode.
     *   1 = primary meaning (always tested first)
     *   2 = secondary
     *   3+ = tertiary etc.
     * Used together with {@code UserWordBind.meaningMasteredMask} (bitmap) to ensure
     * users practice all meanings of polysemous words instead of breezing past via the primary.
     * Defaults to 1; backfilled from sortOrder if available.
     */
    @Column(name = "weight", nullable = false)
    private Integer weight = 1;
}
