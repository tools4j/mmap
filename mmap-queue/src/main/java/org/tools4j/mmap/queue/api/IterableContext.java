package org.tools4j.mmap.queue.api;

import java.util.Iterator;

/**
 * Flyweight return by {@link Reader} to iterate over queue entries.
 */
public interface IterableContext extends Iterable<Entry>, AutoCloseable {
    /**
     * @return the index of the first entry returned by iterators, or {@link Index#NULL} if unavailable.
     */
    long startIndex();

    /**
     * Returns an iterator for queue entries starting at {@link #startIndex()}.
     * <p>
     * Note that the iterator continues to function if queue entries are appended in the background, and new entries are
     * returned even if {@link Iterator#hasNext()} has previously returned false at some point.
     */
    @Override
    Iterator<Entry> iterator();

    /**
     * Returns an iterable for queue entries starting at {@link #startIndex()} returning entries in reverse order
     * finishing with the first queue entry.
     */
    IterableContext reverse();

    /**
     * Closes any iterator associated with this iterable and unwraps the current entry's buffer
     */
    @Override
    void close();
}
