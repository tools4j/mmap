package org.tools4j.mmap.queue.api;

/**
 * Defines index constants used by Queue {@link Poller} and {@link Appender}
 */
public interface Index {
    /** Null index returned for non-existent entry. */
    long NULL = -1;

    /** Zero index used for first entry in the queue. */
    long FIRST = 0;

    /** The maximum allowed index, which is 1,152,921,504,606,846,975. */
    long MAX = Long.MAX_VALUE / Long.BYTES;//otherwise we cannot express the header position

    /**
     * Pseudo-index used to generically reference the last entry in the queue.
     * <p>
     * This value is returned by {#link {@link Poller#nextIndex()}} when moving to the last entry in the queue before it
     * is reached.  Passing {@code LAST} to {@link Poller#seekNext(long)} is equivalent to calling
     * {@link Poller#seekLast()}
     */
    long LAST = Long.MAX_VALUE - 1;

    /**
     * Pseudo-index used to reference the end of the queue <i>after</i> the last entry.
     * <p>
     * This value is returned by {#link {@link Poller#nextIndex()}} when moving to the end of the queue before reaching
     * it.  Passing {@code END} to {@link Poller#seekNext(long)} is equivalent to calling
     * {@link Poller#seekEnd()}}
     */
    long END = Long.MAX_VALUE;
}
