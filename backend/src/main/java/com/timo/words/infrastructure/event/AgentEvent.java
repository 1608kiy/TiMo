package com.timo.words.infrastructure.event;

import lombok.Getter;

/**
 * Event published when an Agent (TiMo) action occurs.
 */
@Getter
public class AgentEvent extends BaseApplicationEvent {

    public enum EventType {
        RECOMMEND,
        CHAT,
        WEEKLY_REPORT
    }

    private final EventType eventType;
    private final String message;

    public AgentEvent(Object source, Long userId, EventType eventType, String message) {
        super(source, userId);
        this.eventType = eventType;
        this.message = message;
    }
}
