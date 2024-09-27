package org.tools4j.mmap.queue.api;

import org.agrona.DirectBuffer;

/**
 * Representation of a queue entry used by {@link Reader}, with data accessible through {@link #buffer()}.
 */
public interface Entry {
    /**
     * @return entry index, non-negative for existing entries
     */
    long index();

    /**
     * The buffer with entry data; valid bytes are in the range {@code [0..(n-1)]} where {@code n} is equal to the
     * buffer's {@link DirectBuffer#capacity() capacity}.
     *
     * @return buffer with entry data, with zero capacity if entry has no data or no entry is available
     */
    DirectBuffer buffer();
}
