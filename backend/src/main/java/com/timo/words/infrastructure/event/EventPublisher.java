package com.timo.words.infrastructure.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * Convenience wrapper around Spring's ApplicationEventPublisher.
 * Provides typed publish methods for domain events.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EventPublisher {

    private final ApplicationEventPublisher publisher;

    public void publishStudyEvent(Long userId, Long wordId, String studyMode,
                                  Double grade, StudyEvent.EventType eventType) {
        StudyEvent event = new StudyEvent(this, userId, wordId, studyMode, grade, eventType);
        log.debug("Publishing StudyEvent: user={}, word={}, mode={}, grade={}, type={}",
                userId, wordId, studyMode, grade, eventType);
        publisher.publishEvent(event);
    }

    public void publishAgentEvent(Long userId, AgentEvent.EventType eventType, String message) {
        AgentEvent event = new AgentEvent(this, userId, eventType, message);
        log.debug("Publishing AgentEvent: user={}, type={}, message={}",
                userId, eventType, message);
        publisher.publishEvent(event);
    }
}
