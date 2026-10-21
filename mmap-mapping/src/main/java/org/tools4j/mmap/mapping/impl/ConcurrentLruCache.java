/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2016-2026 tools4j.org (Marco Terzer, Anton Anufriev)
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
package org.tools4j.mmap.mapping.impl;

import org.agrona.CloseHelper;
import org.tools4j.mmap.mapping.api.Closeable;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicIntegerArray;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicLongArray;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.function.IntFunction;

/**
 * Fixed-capacity, lock-free, allocation-free-after-setup LRU cache keyed by int index, for at most {@code capacity}
 * concurrent borrowers, each holding at most one entry at a time.
 * <p>
 * Entries are acquired and released through a {@link Borrower}, typically created once per thread:
 * <pre>
 *     final ConcurrentLruCache.Borrower&lt;V&gt; borrower = cache.newBorrower(); // once per thread
 *     ...
 *     final V value = borrower.acquire(idx, factory);
 *     try {
 *         value.doWork();
 *     } finally {
 *         borrower.release();
 *     }
 * </pre>
 * At most {@code capacity} borrowers exist at a time, which enforces the threading assumptions below.
 * <p>
 * HAZARD SLOTS INSTEAD OF PIN COUNTS: an entry is held by announcing its slot in the borrower's own (padded) hazard
 * cell, and then validating that the slot still holds the entry. Evicting an entry is the mirror image: mark the slot
 * as {@code EVICTING}, then check that no hazard cell announces it. Both sides store before they load with volatile
 * semantics (Dekker), so at least one of them sees the other: the reader backs off, or the evictor rolls back. A hit
 * hence costs a single StoreLoad fence on a cache line owned by the reading thread, and a release a single release
 * store — no shared cache line is written on the hit path, except for the advisory LRU stamp at most once per epoch.
 * <p>
 * NOTE ON MEMORY ORDERING: `meta` (slot state) and `hazards` are the only synchronization points. `values` are
 * published by the release store of {@code VALID} into `meta`; `stamps` and `epoch` are advisory and only affect LRU
 * accuracy, not correctness. Releasing a hazard is a release store, so all use of a value happens-before an evictor
 * observes the cleared hazard and closes the value.
 * <p>
 * NOTE ON WAITING: a reader that finds its index {@code CLAIMED} (being created) or {@code EVICTING} keeps its
 * hazard and busy-spins until the slot resolves. The outcome is already determined at that point (the creator
 * publishes or yields, the evictor either saw the hazard and rolls back or it did not and proceeds), so there is no
 * retry loop and no livelock. No time-based bound is applied: a slow factory or close() only delays threads waiting
 * for that one index.
 * <p>
 * NOTE ON LRU ACCURACY: `epoch` advances by two on every miss only. Created entries are stamped with the new epoch
 * and hits with epoch + 1, so creates and hits are ordered exactly relative to each other, while hits within the same
 * epoch are indistinguishable and evicted in arbitrary order.
 * <p>
 * MULTI-WRITER SAFE: concurrent misses for the same index create a single entry. Each claimer publishes its claim
 * before scanning for competitors; a VALID entry or lower-slot claim wins, higher-slot claims and pending evictions
 * of the same index are waited for — see awaitCompetitor().
 * <p>
 * NOTE ON INSTANCE IDENTITY: different V instances for the same index (from an earlier and a later occupancy of the
 * cache) are assumed to be interchangeable, no generation counter distinguishes them.
 * <p>
 * THREADING ASSUMPTIONS: at most `capacity` borrowers exist (enforced by newBorrower()), each holding at most one
 * slot at a time — one acquired entry, or one claim while creating, or one slot it is evicting (no nested acquire on the
 * same borrower). A borrower is used by one thread at a time, as its own fields are plain; a thread may own several
 * borrowers, and a borrower may be handed over to another thread with a happens-before edge. The factory must not call
 * back into the cache, not even via another borrower: the thread could end up waiting for its own pending claim. A
 * borrower claiming a slot hence holds nothing itself, and the other borrowers hold at most capacity - 1 slots, so an
 * EMPTY slot or an unheld entry always exists; the bound on claim attempts relies on this, see
 * CLAIM_ATTEMPTS_PER_CAPACITY.
 */
public final class ConcurrentLruCache<E> {
    // --- Slot states ---
    private static final int EMPTY = 0;
    private static final int CLAIMED = 1;
    private static final int VALID = 2;
    private static final int EVICTING = 3;
    private static final long EMPTY_META = pack(0, EMPTY);
    private static final int NONE = -1;
    /** Longs between hazard cells: 128 bytes, avoiding false sharing also with adjacent-line prefetching */
    private static final int HAZARD_STRIDE = 16;
    /**
     * Claim attempts per unit of capacity in claimSlot(). Relies on the THREADING ASSUMPTIONS in the class doc: an
     * EMPTY slot or unheld entry always exists, so every failed attempt is caused by a state change of another
     * borrower (it took the empty slot, acquired or evicted the victim, or won the CAS), not by merely waiting for one.
     * Exceeding this bound indicates a violation of those assumptions, e.g. a borrower used by threads concurrently.
     */
    private static final int CLAIM_ATTEMPTS_PER_CAPACITY = 16;

    // --- Packed slot metadata: [ index:32 | state:32 ]
    private static long pack(final int index, final int state) {
        return ((long) index << 32) | state;
    }
    private static int indexOf(final long packed) {
        return (int) (packed >>> 32);
    }
    private static int stateOf(final long packed) {
        return (int) packed;
    }

    private final int capacity;
    private final int maxClaimAttempts;
    private final AtomicLongArray meta;
    private final AtomicReferenceArray<E> values;
    private final AtomicLongArray stamps;
    /** slot + 1 of the held entry per borrower, or 0 if none; padded, see hazardIndex() */
    private final AtomicLongArray hazards;
    private final AtomicIntegerArray borrowersInUse;
    private final AtomicLong epoch = new AtomicLong();

    /**
     * Handle for borrowing entries from a cache, created via {@link #newBorrower()}, holding at most one entry at a
     * time. Not thread-safe: a borrower is used by one thread at a time, handing it over to another thread requires a
     * happens-before edge.
     *
     * @param <E> the cached value type
     */
    public static final class Borrower<E> implements Closeable {
        private final ConcurrentLruCache<E> cache;
        private final int cell;
        private final int hazard;
        private final long[] heldBySlot;
        private int heldSlot = NONE;
        private int hintIndex;
        private int hintSlot = NONE;
        private boolean closed;

        private Borrower(final ConcurrentLruCache<E> cache, final int cell) {
            this.cache = cache;
            this.cell = cell;
            this.hazard = hazardIndex(cell);
            this.heldBySlot = new long[(cache.capacity + Long.SIZE - 1) / Long.SIZE];
        }

        /**
         * Acquires the cached value for index if present, without creating it.
         *
         * @param index the index of the value to acquire
         * @return the value, to be released via {@link #release()}, or null if not cached
         */
        public E tryAcquire(final int index) {
            return cache.lookup(this, index, false);
        }

        /**
         * Acquires the cached value for index, creating it via factory if absent, evicting the least recently used
         * entry not held by any borrower if the cache is full.
         *
         * @param index   the index of the value to acquire
         * @param factory factory to create a new value if none is currently cached for this index
         * @return the value, to be released via {@link #release()}
         */
        public E acquire(final int index, final IntFunction<? extends E> factory) {
            final E existing = cache.lookup(this, index, true);
            return existing != null ? existing : cache.createAndAcquire(this, index, factory, true);
        }

        /** Releases the entry held by this borrower */
        public void release() {
            if (heldSlot == NONE) {
                throw new IllegalStateException("Cannot release without prior acquire: " + this);
            }
            heldSlot = NONE;
            cache.hazards.setRelease(hazard, 0);
        }

        /**
         * Ensures a value for the given index is cached, creating it via the factory if necessary, without acquiring
         * it. The entry may be evicted at any time after this method returns, even immediately.
         *
         * @param index   the index of the value to create if absent
         * @param factory factory to create a new value if none is currently cached for this index
         */
        public void createIfAbsent(final int index, final IntFunction<? extends E> factory) {
            assertUsable();
            if (!cache.isPresent(index)) {
                cache.createAndAcquire(this, index, factory, false);
            }
        }

        private void assertUsable() {
            assert !closed : "borrower closed: " + this;
            assert heldSlot == NONE : "acquire is not reentrant, release first: " + this;
        }

        @Override
        public boolean isClosed() {
            return closed;
        }

        /** Returns this borrower's hazard cell to the cache; must not hold an entry */
        @Override
        public void close() {
            if (!closed) {
                cache.closeBorrower(this);
            }
        }

        @Override
        public String toString() {
            return "ConcurrentLruCache.Borrower:cell=" + cell + "|heldSlot=" + heldSlot + "|closed=" + closed;
        }
    }

    public ConcurrentLruCache(final int capacity) {
        if (capacity < 1) {
            throw new IllegalArgumentException("Invalid capacity " + capacity + ", must be positive");
        }
        this.capacity = capacity;
        this.maxClaimAttempts = (int) Math.min(Integer.MAX_VALUE, (long) CLAIM_ATTEMPTS_PER_CAPACITY * capacity);
        this.meta = new AtomicLongArray(capacity);
        this.values = new AtomicReferenceArray<>(capacity);
        this.stamps = new AtomicLongArray(capacity);
        this.hazards = new AtomicLongArray(hazardIndex(capacity) + HAZARD_STRIDE);
        this.borrowersInUse = new AtomicIntegerArray(capacity);
    }

    private static int hazardIndex(final int cell) {
        return (cell + 1) * HAZARD_STRIDE;
    }

    /**
     * Creates a new borrower.
     *
     * @return a new borrower, to be closed when no longer used
     * @throws IllegalStateException if {@code capacity} borrowers are already in use
     */
    public Borrower<E> newBorrower() {
        for (int cell = 0; cell < capacity; cell++) {
            if (borrowersInUse.get(cell) == 0 && borrowersInUse.compareAndSet(cell, 0, 1)) {
                return new Borrower<>(this, cell);
            }
        }
        throw new IllegalStateException("All " + capacity + " borrowers in use: " + this);
    }

    private void closeBorrower(final Borrower<E> borrower) {
        if (borrower.heldSlot != NONE) {
            throw new IllegalStateException("Cannot close borrower while holding an entry: " + borrower);
        }
        borrower.closed = true;
        borrowersInUse.set(borrower.cell, 0);
    }

    private E lookup(final Borrower<E> borrower, final int index, final boolean awaitPending) {
        borrower.assertUsable();
        final int hint = borrower.hintSlot;
        if (hint != NONE && borrower.hintIndex == index && isCandidate(meta.getOpaque(hint), index, awaitPending) &&
                pin(borrower, hint, index)) {
            return values.getPlain(hint);
        }
        for (int slot = 0; slot < capacity; slot++) {
            if (isCandidate(meta.getOpaque(slot), index, awaitPending) && pin(borrower, slot, index)) {
                return values.getPlain(slot);
            }
        }
        return null;
    }

    private static boolean isCandidate(final long current, final int index, final boolean awaitPending) {
        return indexOf(current) == index &&
                (stateOf(current) == VALID || (awaitPending && stateOf(current) != EMPTY));
    }

    /**
     * Announces a hazard for slot, then validates that the slot holds a VALID entry for index, waiting out a pending
     * creation or eviction of index. Clears the hazard again if validation fails.
     */
    private boolean pin(final Borrower<E> borrower, final int slot, final int index) {
        hazards.set(borrower.hazard, slot + 1); // volatile: the one StoreLoad fence of the hit path
        final long claimed = pack(index, CLAIMED);
        final long evicting = pack(index, EVICTING);
        long current = meta.get(slot);
        while (current == claimed || current == evicting) {
            Thread.onSpinWait();
            current = meta.get(slot);
        }
        if (current != pack(index, VALID)) {
            hazards.setRelease(borrower.hazard, 0);
            return false;
        }
        borrower.heldSlot = slot;
        borrower.hintIndex = index;
        borrower.hintSlot = slot;
        final long stamp = epoch.getOpaque() + 1; // hits rank above entries created in the same epoch
        if (stamps.getOpaque(slot) < stamp) {
            stamps.setOpaque(slot, stamp);
        }
        return true;
    }

    private boolean isPresent(final int index) {
        final long valid = pack(index, VALID);
        final long claimed = pack(index, CLAIMED);
        for (int slot = 0; slot < capacity; slot++) {
            final long current = meta.get(slot);
            if (current == valid || current == claimed) {
                return true;
            }
        }
        return false;
    }

    private E createAndAcquire(final Borrower<E> borrower, final int index, final IntFunction<? extends E> factory,
                               final boolean acquire) {
        while (true) {
            final int slot = claimSlot(borrower, index);
            final int competitor = awaitCompetitor(slot, index);
            if (competitor == NONE) {
                return createAndPublish(borrower, slot, index, factory, acquire);
            }
            meta.set(slot, EMPTY_META); // yield to competitor
            if (acquire) {
                if (pin(borrower, competitor, index)) {
                    return values.getPlain(competitor);
                }
                final E existing = lookup(borrower, index, true);
                if (existing != null) {
                    return existing;
                }
            } else {
                awaitResolved(competitor, pack(index, CLAIMED));
                if (isPresent(index)) {
                    return null;
                }
            }
            // competitor was evicted or rolled back meanwhile -- try again
        }
    }

    /**
     * Claims an EMPTY slot, or evicts the least recently used entry that is not held by any borrower, and marks it as
     * {@code CLAIMED} for index with volatile semantics.
     */
    private int claimSlot(final Borrower<E> borrower, final int index) {
        final long claimed = pack(index, CLAIMED);
        for (int attempt = 0; attempt < maxClaimAttempts; attempt++) {
            final int victim = findVictim(borrower);
            if (victim == NONE) {
                continue;
            }
            final long current = meta.getOpaque(victim);
            final int state = stateOf(current);
            if (state == EMPTY) {
                if (meta.compareAndSet(victim, current, claimed)) {
                    return victim;
                }
            } else if (state == VALID) {
                if (meta.compareAndSet(victim, current, pack(indexOf(current), EVICTING))) {
                    if (isHeld(victim)) {
                        meta.set(victim, current); // roll back, holder keeps using the value
                        continue;
                    }
                    // The victim may be an entry for index itself, published since our lookup missed: evicting and
                    // re-creating it is rare (it must be LRU) and harmless (instances are interchangeable).
                    closeValue(victim);
                    meta.set(victim, claimed);
                    return victim;
                }
            }
        }
        throw new IllegalStateException("Not able to claim empty or evictable entry after " + maxClaimAttempts +
                " attempts, borrower used by threads concurrently or factory calling back into the cache: " + this + ", slots=" +
                slots());
    }

    /**
     * Finds an EMPTY slot — starting at the borrower's own cell to spread concurrent claims — or else the VALID slot
     * with the oldest stamp among those not held by any borrower. Held slots have to be excluded up front: a long-held
     * entry would otherwise remain the oldest and be picked (and rolled back) over and over.
     */
    private int findVictim(final Borrower<E> borrower) {
        final long[] held = borrower.heldBySlot;
        Arrays.fill(held, 0);
        for (int cell = 0; cell < capacity; cell++) {
            final long hazard = hazards.getOpaque(hazardIndex(cell));
            if (hazard != 0) {
                final int slot = (int) hazard - 1;
                held[slot / Long.SIZE] |= 1L << slot;
            }
        }
        int victim = NONE;
        long oldest = Long.MAX_VALUE;
        for (int i = 0, slot = borrower.cell; i < capacity; i++, slot = slot + 1 == capacity ? 0 : slot + 1) {
            final int state = stateOf(meta.getOpaque(slot));
            if (state == EMPTY) {
                return slot;
            }
            if (state == VALID && (held[slot / Long.SIZE] & (1L << slot)) == 0) {
                final long stamp = stamps.getOpaque(slot);
                if (stamp < oldest) {
                    oldest = stamp;
                    victim = slot;
                }
            }
        }
        return victim;
    }

    /** Volatile scan of all hazards, called after marking slot as EVICTING: the evictor side of the Dekker pair */
    private boolean isHeld(final int slot) {
        final long hazard = slot + 1;
        for (int cell = 0; cell < capacity; cell++) {
            if (hazards.get(hazardIndex(cell)) == hazard) {
                return true;
            }
        }
        return false;
    }

    /**
     * Resolves competing creation of index after claiming slot. Returns the slot we have to yield to — one with a
     * VALID entry or a claim in a lower slot for index — or NONE if no competitor exists and we can create the entry.
     * Claims in higher slots and evictions of index are waited for, as they may still publish or restore an entry.
     * <p>
     * Every claimer publishes its claim with volatile semantics before this scan, so at least one of any two
     * competitors sees the other. The higher one yields when it sees the lower one; the lower one waits for the higher
     * one when it sees it. Hence at most one entry is created per index, and waits never form a cycle.
     */
    private int awaitCompetitor(final int slot, final int index) {
        final long valid = pack(index, VALID);
        final long claimed = pack(index, CLAIMED);
        final long evicting = pack(index, EVICTING);
        while (true) {
            int pending = NONE;
            long pendingMeta = EMPTY_META;
            for (int other = 0; other < capacity; other++) {
                if (other == slot) {
                    continue;
                }
                final long current = meta.get(other);
                if (current == valid || (current == claimed && other < slot)) {
                    return other;
                }
                if (current == claimed || current == evicting) {
                    pending = other;
                    pendingMeta = current;
                }
            }
            if (pending == NONE) {
                return NONE;
            }
            awaitResolved(pending, pendingMeta);
        }
    }

    private void awaitResolved(final int slot, final long pending) {
        while (meta.get(slot) == pending) {
            Thread.onSpinWait();
        }
    }

    private E createAndPublish(final Borrower<E> borrower, final int slot, final int index,
                               final IntFunction<? extends E> factory, final boolean acquire) {
        // We exclusively own `slot` in CLAIMED state now — safe to write.
        final E created;
        try {
            created = factory.apply(index);
        } catch (final RuntimeException | Error e) {
            meta.set(slot, EMPTY_META);
            throw e;
        }
        values.setPlain(slot, created);
        stamps.setOpaque(slot, epoch.addAndGet(2));
        if (acquire) {
            // no fence needed: published with VALID below, which any evictor reads before scanning hazards
            hazards.setOpaque(borrower.hazard, slot + 1);
            borrower.heldSlot = slot;
            borrower.hintIndex = index;
            borrower.hintSlot = slot;
        }
        meta.setRelease(slot, pack(index, VALID)); // the publish point
        return acquire ? created : null;
    }

    /**
     * Removes and closes the entry for index unless it is currently held by a borrower.
     *
     * @return true if the entry was removed, false if absent or held
     */
    public boolean remove(final int index) {
        final long valid = pack(index, VALID);
        for (int slot = 0; slot < capacity; slot++) {
            if (meta.get(slot) == valid) {
                return tryRemove(slot, valid);
            }
        }
        return false;
    }

    /**
     * Removes and closes all entries not currently held by a borrower.
     *
     * @return true if all entries were removed, false if some were held or in transition
     */
    public boolean removeAll() {
        boolean all = true;
        for (int slot = 0; slot < capacity; slot++) {
            final long current = meta.get(slot);
            final int state = stateOf(current);
            if (state == VALID) {
                all &= tryRemove(slot, current);
            } else if (state != EMPTY) {
                all = false;
            }
        }
        return all;
    }

    private boolean tryRemove(final int slot, final long current) {
        if (!meta.compareAndSet(slot, current, pack(indexOf(current), EVICTING))) {
            return false;
        }
        if (isHeld(slot)) {
            meta.set(slot, current);
            return false;
        }
        closeValue(slot);
        meta.set(slot, EMPTY_META);
        return true;
    }

    /** Closes and clears the value in slot; caller must own slot exclusively */
    private void closeValue(final int slot) {
        final E value = values.getPlain(slot);
        values.setPlain(slot, null);
        if (value instanceof AutoCloseable) {
            CloseHelper.quietClose((AutoCloseable) value);
        }
    }

    public int capacity() {
        return capacity;
    }

    /** @return number of occupied slots, including those in transition */
    public int size() {
        int size = 0;
        for (int slot = 0; slot < capacity; slot++) {
            if (stateOf(meta.get(slot)) != EMPTY) {
                size++;
            }
        }
        return size;
    }

    private String slots() {
        final StringBuilder builder = new StringBuilder(16).append('[');
        for (int slot = 0; slot < capacity; slot++) {
            final long current = meta.get(slot);
            final int state = stateOf(current);
            if (state != EMPTY) {
                builder.append(builder.length() > 1 ? ", " : "");
                builder.append(slot).append(':').append(stateName(state)).append(':').append(indexOf(current));
            }
        }
        return builder.append(']').toString();
    }

    private static String stateName(final int state) {
        return switch (state) {
            case EMPTY -> "EMPTY";
            case CLAIMED -> "CLAIMED";
            case VALID -> "VALID";
            case EVICTING -> "EVICTING";
            default -> "UNKNOWN";
        };
    }

    @Override
    public String toString() {
        return "ConcurrentLruCache:capacity=" + capacity + "|size=" + size();
    }
}
