package org.tools4j.eventsourcing.api;

/**
 * Event Processing State provides event details at different stages of processing of the event.
 * There are the following stages when event processing state can be observed:
 * - before an upstream event is processed
 * - after the upstream event is processed
 * - before a sequence of downstream events is processed
 * - after the sequence of downstream events is processed
 */
public interface EventProcessingState {
    /**
     * @return Position of the event in the queue. Starts from 0 and increments by 1 (for each event).
     */
    long id();

    /**
     * @return Source of the event.
     */
    int source();

    /**
     * @return Id of the event within the source.
     */
    long sourceId();

    /**
     * @param source given source
     * @return sourceId of the event from the given source processed so far.
     */
    long sourceId(int source);

    /**
     * @return time of the event in nanos
     */
    long eventTimeNanos();

    /**
     * @return system time when the event is ingested at current stage.
     */
    long ingestionTimeNanos();
}
