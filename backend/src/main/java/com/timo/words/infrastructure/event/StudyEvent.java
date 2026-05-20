package com.timo.words.infrastructure.event;

import lombok.Getter;

/**
 * Event published when a study action occurs (answer, stubborn mark/clear, etc.).
 */
@Getter
public class StudyEvent extends BaseApplicationEvent {

    public enum EventType {
        CORRECT,
        WRONG,
        STUBBORN_MARKED,
        STUBBORN_CLEARED
    }

    private final Long wordId;
    private final String studyMode;
    private final Double grade;
    private final EventType eventType;

    public StudyEvent(Object source, Long userId, Long wordId, String studyMode,
                      Double grade, EventType eventType) {
        super(source, userId);
        this.wordId = wordId;
        this.studyMode = studyMode;
        this.grade = grade;
        this.eventType = eventType;
    }
}
