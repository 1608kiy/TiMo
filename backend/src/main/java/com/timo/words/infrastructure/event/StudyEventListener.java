package com.timo.words.infrastructure.event;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Listens for StudyEvent and logs them.
 * Fatigue detection is handled by frontend useFatigueCheck composable.
 * Statistics are computed on-demand via StatisticsService.
 * Stubborn word marking is handled by FSRS ErrorReinforcementHandler.
 */
@Slf4j
@Component
public class StudyEventListener {

    @EventListener
    public void onStudyEvent(StudyEvent event) {
        log.info("StudyEvent received: userId={}, wordId={}, mode={}, grade={}, type={}",
                event.getUserId(), event.getWordId(), event.getStudyMode(),
                event.getGrade(), event.getEventType());
    }

    @EventListener
    public void onAgentEvent(AgentEvent event) {
        log.info("AgentEvent received: userId={}, type={}, message={}",
                event.getUserId(), event.getEventType(), event.getMessage());
    }
}
