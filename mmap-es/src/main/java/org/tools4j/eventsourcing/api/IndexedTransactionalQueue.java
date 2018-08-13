package org.tools4j.eventsourcing.api;

public interface IndexedTransactionalQueue extends IndexedPollerFactory {
    Transaction appender();
}
