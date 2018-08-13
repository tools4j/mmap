package org.tools4j.eventsourcing.api;

import org.tools4j.process.ProcessStep;

import java.io.IOException;

/**
 * The Event Processing Queue provides an API to a composition of upstream and downstream queues and a processor step
 * that processes one event from upstream queue and appends one or more resulting events atomically to the downstream
 * queue.
 *
 * The appender() method returns an appender to the upstream queue.
 * The createPoller() method creates a poller for the downstream queue.
 */
public interface EventProcessingQueue extends IndexedQueue {
    /**
     * @return a processor step that processes one event from upstream queue and appends one or more
     *         resulting events atomically to the downstream queue
     */
    ProcessStep processorStep();
}
