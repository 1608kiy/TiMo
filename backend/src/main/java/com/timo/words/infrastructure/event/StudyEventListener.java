package com.timo.words.infrastructure.event;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Listens for StudyEvent and logs them.
 * Future extensions: fatigue detection, statistics updates, stubborn word tracking.
 */
@Slf4j
@Component
public class StudyEventListener {

    @EventListener
    public void onStudyEvent(StudyEvent event) {
        log.info("StudyEvent received: userId={}, wordId={}, mode={}, grade={}, type={}",
                event.getUserId(), event.getWordId(), event.getStudyMode(),
                event.getGrade(), event.getEventType());

        // TODO: extend with fatigue detection (20min cumulative study)
        // TODO: extend with real-time statistics updates
        // TODO: extend with stubborn word auto-marking callbacks
    }

    @EventListener
    public void onAgentEvent(AgentEvent event) {
        log.info("AgentEvent received: userId={}, type={}, message={}",
                event.getUserId(), event.getEventType(), event.getMessage());
    }
}
