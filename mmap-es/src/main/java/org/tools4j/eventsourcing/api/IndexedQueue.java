package org.tools4j.eventsourcing.api;

/**
 * A queue of messages with index details.
 */
public interface IndexedQueue extends IndexedPollerFactory {
    /**
     * @return indexed appender
     */
    IndexedMessageConsumer appender();
}
