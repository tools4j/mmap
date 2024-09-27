package org.tools4j.mmap.queue.api;

/**
 * Flyweight return by {@link Reader} with access to entry data and index.
 */
public interface ReadingContext extends Entry, AutoCloseable {
    /**
     * @return non-negative index if entry is available, and {@link Index#NULL} otherwise
     */
    @Override
    long index();

    /**
     * @return true if entry is available, and false otherwise
     */
    boolean hasEntry();

    /**
     * Closes the reading context and unwraps the buffer
     */
    @Override
    void close();
}
