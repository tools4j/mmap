package org.tools4j.eventsourcing.api;

/**
 * Transactional message consumer that allows to handle multiple messages atomically
 * following initialisation with index details and followed by commit method to
 * finalise the transaction.
 */
public interface Transaction extends MessageConsumer {
    /**
     * Init transaction specifying index details.
     * @param source - source of the event
     * @param sourceId - id of the event within a source
     * @param eventTimeNanos - time of the event in nanos
     */
    void init(int source, long sourceId, long eventTimeNanos);

    /**
     * Commit the transaction.
     */
    void commit();
}
