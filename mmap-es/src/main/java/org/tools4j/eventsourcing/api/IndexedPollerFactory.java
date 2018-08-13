package org.tools4j.eventsourcing.api;

import java.io.IOException;

/**
 * Factory of pollers with certain behaviour on the event indexes.
 */
public interface IndexedPollerFactory {
    /**
     * Creates a poller with parametrised index behaviour.
     *
     * @param skipPredicate skip predicate is used to decide when to skip processing the event and move to the next position.
     * @param pausePredicate pause predicate is used to decide when to halt processing and stay at the current position.
     * @param beforeIndexHandler the handler is used to notify before processing of the actual message
     * @param afterIndexHandler the handler is used to notify after processing of the actual message
     * @return new instance of a poller.
     * @throws IOException when a backing file could not be read/mapped.
     */
    Poller createPoller(Poller.IndexPredicate skipPredicate,
                        Poller.IndexPredicate pausePredicate,
                        Poller.IndexConsumer beforeIndexHandler,
                        Poller.IndexConsumer afterIndexHandler) throws IOException;
}
