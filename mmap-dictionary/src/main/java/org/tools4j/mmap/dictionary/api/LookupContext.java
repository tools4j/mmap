package org.tools4j.mmap.dictionary.api;

import org.agrona.MutableDirectBuffer;

public interface LookupContext extends AutoCloseable {
    /**
     * @return true if the context is closed.
     */
    boolean isClosed();

    /**
     * Closes the lookup context and unwraps the buffer
     */
    @Override
    void close();

    interface Key extends LookupContext {
        void ensureCapacity(int capacity);
        MutableDirectBuffer keyBuffer();
        boolean containsKey(int keyLength);
        Result lookup(int keyLength);
    }

    interface Result extends KeyValueResult {
        boolean isPresent();
    }
}
