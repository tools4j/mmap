package org.tools4j.mmap.queue.api;

import org.agrona.MutableDirectBuffer;

/**
 * Flyweight returned by {@link Appender#appending()} to encode a new entry directly to the queue {@link #buffer()}.
 */
public interface AppendingContext extends AutoCloseable {
    /**
     * @return buffer to write the entry data directly to the queue
     */
    MutableDirectBuffer buffer();

    /**
     * Aborts appending the entry
     */
    void abort();

    /**
     * Commits the entry that was encoded into the {@link #buffer()}
     *
     * @param length - length of the entry in bytes
     * @return queue index at which entry was appended
     * @throws IllegalArgumentException if length exceeds the maximum length for an entry allowed by the queue
     * @throws IllegalStateException    if the appender or the underlying queue is closed
     */
    long commit(int length);

    /**
     * @return true if the context is closed.
     */
    boolean isClosed();

    /**
     * Aborts appending if not closed or committed yet.
     */
    @Override
    default void close() {
        if (!isClosed()) {
            abort();
        }
    }
}
