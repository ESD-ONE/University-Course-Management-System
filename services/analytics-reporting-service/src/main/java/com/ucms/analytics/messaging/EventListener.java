package com.ucms.analytics.messaging;

import com.ucms.analytics.config.RabbitConfig;
import com.ucms.analytics.service.SnapshotService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Any enrollment / grading / identity change makes the dashboard snapshot stale; the
 *  recompute itself is debounced in SnapshotService. */
@Component
@ConditionalOnProperty(name = "ucms.events.enabled", havingValue = "true", matchIfMissing = true)
public class EventListener {

    private final SnapshotService snapshots;

    public EventListener(SnapshotService snapshots) {
        this.snapshots = snapshots;
    }

    @RabbitListener(queues = RabbitConfig.QUEUE)
    public void onEvent(String message) {
        snapshots.markDirty();
    }
}
