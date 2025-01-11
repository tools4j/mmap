/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2016-2025 tools4j.org (Marco Terzer, Anton Anufriev)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package org.tools4j.mmap.queue.api;

/**
 * Poller for sequential retrieval of {@link Queue} entries with callback to an {@link EntryHandler}.
 */
public interface Poller extends AutoCloseable {
    /** No entry was polled but the cursor was moved towards {@link #nextIndex()} */
    int CURSOR_MOVED = 2;
    /** An entry was polled */
    int ENTRY_POLLED = 1;
    /** No entry was polled with the cursor at the end of the queue waiting for a new entry to be appended */
    int PENDING_NEXT = 0;
    /** The queue has not been opened yet because it does not exist yet */
    int PENDING_OPEN = -1;
    /** The cursor has been moved past the {@link Index#MAX MAX} allowed entry index (use seek method to resume) */
    int AFTER_MAX = -2;
    /** The cursor has been moved backwards and has moved past the first entry (use seek method to resume) */
    int BEFORE_FIRST = -3;
    /** The poller or the underlying queue has been closed */
    int CLOSED = -4;

    /**
     * Polls the queue and invokes the entry handler if one is available.
     *
     * @param entryHandler entry handler callback invoked if an entry is present
     * @return  a positive value if an entry was polled or if the entry cursor was moved,
     *          zero if not polled or moved, and negative if queue is not yet open, closed or if the cursor has been
     *          moved before the first entry (see constants defined in this class)
     */
    int poll(EntryHandler entryHandler);

    /**
     * Returns the index of the current entry, or -1 if no current entry exists.
     * The current entry is the one last polled or last touched when moving the cursor.
     * @return index of current entry, zero for first queue entry
     */
    long currentIndex();

    /**
     * Returns the index of the next entry to be polled, or -1 polling has moved the cursor before the first entry or
     * past {@link Index#MAX}, and {@link Index#END} for the next entry at the end of the queue.
     *
     * @return index of next entry to be polled, zero for first queue entry
     */
    long nextIndex();

    /**
     * Sets the index of the next polled entry to zero. Equivalent to {@code seekNext(0)}.
     *
     * @see #seekNext(long)
     * @see #seekLast()
     * @see #seekEnd()
     */
    void seekStart();

    /**
     * Sets the index of the next polled entry to be the last entry. Equivalent to {@code seekNext(Index.LAST)}.
     * This operation returns fast and does not move the cursor immediately, instead the cursor is moved through
     * subsequent {@link #poll(EntryHandler) poll(..)} invocations.
     * <p>
     * Note that last entry of the queue be a moving target if entries are concurrently appended to the queue.
     *
     * @see #seekEnd()
     * @see #seekStart()
     * @see #seekNext(long)
     * @see Index#LAST
     */
    void seekLast();

    /**
     * Sets the index of the next polled entry to be at the end. Equivalent to {@code seekNext(Index.END)}.
     * This operation returns fast and does not move the cursor immediately, instead the cursor is moved through
     * subsequent {@link #poll(EntryHandler) poll(..)} invocations.
     * <p>
     * Note that queue end may be a moving target if entries are concurrently appended to the queue.
     *
     * @see #seekStart()
     * @see #seekLast()
     * @see #seekNext(long)
     * @see Index#END
     */
    void seekEnd();

    /**
     * Sets the index of the next polled entry. This operation returns fast and does not move the cursor immediately,
     * instead the cursor is moved through subsequent {@link #poll(EntryHandler) poll(..)} invocations.
     *
     * @param index the index of the next entry to poll, 0 for first and {@link Index#END} end of the queue
     * @throws IllegalArgumentException if the provided index is negative
     * @see #seekStart()
     * @see #seekEnd()
     */
    void seekNext(long index);

    /**
     * @return true if this poller is closed
     */
    boolean isClosed();

    /**
     * Closes the poller.
     */
    @Override
    void close();
}
