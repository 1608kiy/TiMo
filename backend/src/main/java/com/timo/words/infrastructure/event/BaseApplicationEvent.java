package com.timo.words.infrastructure.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * Base event carrying userId for all application events.
 */
@Getter
public abstract class BaseApplicationEvent extends ApplicationEvent {

    private final Long userId;

    protected BaseApplicationEvent(Object source, Long userId) {
        super(source);
        this.userId = userId;
    }
}
