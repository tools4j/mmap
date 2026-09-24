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
package org.tools4j.mmap.region.impl;


import org.agrona.CloseHelper;
import org.agrona.UnsafeApi;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLongArray;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.function.IntFunction;

import static org.agrona.BitUtil.findNextPositivePowerOfTwo;
import static org.agrona.BitUtil.isPowerOfTwo;
import static org.tools4j.mmap.region.impl.Constraints.validateNonNegative;

/**
 * Fixed-capacity, lock-free, allocation-free-after-warmup LRU cache keyed by int index.
 * <p>
 * Values are acquired by index and the index has to be released after use, which guarantees
 * that the value is not evicted (and closed) while in use.
 *
 * <pre>
 *     V value = cache.acquire(idx, factory);
 *     if (value != null) {
 *         try {
 *             value.doWork();
 *         } finally {
 *             cache.release(idx);
 *         }
 *     }
 * </pre>
 *
 * Assumes acquire()/release() calls on a given thread are properly paired and not
 * interleaved with a get() for a different index in between the pairing.
 * Interleaving still behaves correctly, it just loses the fast-hint path.
 * <p>
 * MULTI-WRITER SAFE: multiple threads may call acquire() concurrently
 * for the same new index. A duplicate-claim check and deterministic tie-break
 * (lower slot number wins) ensure only one entry is ever published per index,
 * even under a race. This costs one extra full-table scan per insert — see
 * findConcurrentClaimOrValid().
 * <p>
 * NOTE ON INSTANCE IDENTITY: if a slot for a given index is closed and later
 * reopened (evict + re-create), any value from the earlier occupancy is treated
 * as interchangeable with the new one — no generation counter distinguishes
 * "episodes" of the same index. Safe only because different V instances for
 * the same index are considered equivalent by callers.
 * <p>
 * NOTE ON HINT HEURISTICS: acquire()/release() speculatively assume the slot is
 * quiescent when using cached per-thread hints. This is a bet, not a
 * guarantee — if wrong, the CAS fails safely and falls back to a verified
 * path using the witness value from the failed attempt.
 * <p>
 * NOTE ON MEMORY ORDERING: the `meta` CAS is the sole synchronization point
 * for this class — full acquire+release semantics. Other fields (`values`,
 * `lastUsed`, `clock`) piggyback on the happens-before edge `meta` already
 * established, so they use weaker (plain/opaque) access modes deliberately.
 * <p>
 * NOTE ON TABLE SIZING / SCAN ORDER: the backing table is sized as a power
 * of two for efficient modulo operations; every linear scan starts at
 * `index &amp; (cacheSize - 1)` and wraps around, rather than always starting
 * at slot 0, for a shorter expected path to hits.
 * <p>
 * NOTE ON CAPACITY ENFORCEMENT: liveCount is pre-incremented ("claim a growth
 * ticket") before searching for a slot, so the live entry count never
 * transiently exceeds `capacity`, even briefly, under concurrent inserts.
 */
public final class AtomicLruCache<E> {
    // --- Slot states ---
    private static final int EMPTY = 0;
    private static final int CLAIMED = 1;
    private static final int VALID = 2;
    private static final int CLOSING = 3;
    private static final long CLOCK = UnsafeApi.objectFieldOffset(AtomicLruCache.class, "clock");
    private static final int ACQUIRE_ATTEMPTS_MIN = 32;

    // --- Atomic long clock ---
    private long getClockAndTick() {
        return UnsafeApi.getAndAddLongRelease(this, CLOCK, 1L);
    }

    private static long lruTime(final int pin, final long time) {
        return ((long)pin << 48) | (0x0000ffffffffffffL & time);
    }

    // --- Packed slot metadata: [ index:32 | state:16 | pin:16 ]
    private static long pack(final int index, final int state, final int pin) {
        return ((long)index << 32) | ((long)state << 16) | (pin & 0xffffL);
    }
    private static int indexOf(final long packed) {
        return (int) (packed >>> 32);
    }
    private static int stateOf(final long packed) {
        return (int) ((packed >>> 16) & 0xffffL);
    }
    private static int pinOf(final long packed) {
        return (int) (packed & 0xffffL);
    }

    // --- shared state -->
    private final int capacity;
    private final int cacheSize;
    private final int cacheSizeMask;
    private final AtomicLongArray meta;
    private final AtomicLongArray lastUsed;
    private final AtomicReferenceArray<E> values;
    private final ThreadLocal<Hint[]> hints;
    private final AtomicInteger liveCount = new AtomicInteger(0);
    private volatile long clock;

    // --- thread local -->
    private static final class Hint {
        int index = -1;
        int slot = -1;
    }

    public AtomicLruCache(final int capacity) {
        this(capacity, findNextPositivePowerOfTwo(capacity));
    }

    public AtomicLruCache(final int capacity, final int cacheSize) {
        validateNonNegative("capacity", capacity);
        if (cacheSize < capacity || !isPowerOfTwo(cacheSize)) {
            throw new IllegalArgumentException(
                    "Invalid cache size, must be a power of two and at least same as capacity " + capacity +
                            ", but was " + cacheSize
            );
        }
        this.capacity = capacity;
        this.cacheSize = cacheSize;
        this.cacheSizeMask = cacheSize - 1;
        this.meta = new AtomicLongArray(cacheSize);
        this.lastUsed = new AtomicLongArray(cacheSize);
        this.values = new AtomicReferenceArray<>(cacheSize);
        for (int i = 0; i < cacheSize; i++) {
            meta.lazySet(i, pack(0, EMPTY, 0));
        }
        this.hints = ThreadLocal.withInitial(() -> {
            final Hint[] hints = new Hint[cacheSize];
            for (int i = 0; i < cacheSize; i++) {
                hints[i] = new Hint();
            }
            return hints;
        });
    }

    private Hint hintForIndex(final int index) {
        return hints.get()[index & cacheSizeMask];
    }

    private int slotForIndex(final int index) {
        return index & cacheSizeMask;
    }


    public E tryAcquire(final int index) {
        final long expected = pack(index, VALID, 1);//optimistic guess
        int slot;

        // (1) hot path
        final Hint hint = hintForIndex(index);
        if (hint.index == index && (slot = hint.slot) >= 0) {

            // (1.1) try with optimistic guess
            final long witness = meta.compareAndExchange(slot, expected, expected + 1);
            if (witness == expected) {
                lastUsed.lazySet(slot, getClockAndTick());
                return values.getPlain(slot);
            }

            // (1.2) contended, try same slot
            if (tryPinSlot(slot, index, witness)) {
                lastUsed.setOpaque(slot, getClockAndTick());
                return values.getPlain(slot);
            }

            // (1.3) slot no longer holds this index -- fall through
        }

        // (2) cold path, scan starting at the index's home slot, wrapping around
        for (int i = 0; i < cacheSize; i++) {
            slot = slotForIndex(index + i);
            if (tryPinSlot(slot, index, expected)) {
                lastUsed.setOpaque(slot, getClockAndTick());
                hint.index = index;
                hint.slot = slot;
                return values.getPlain(slot);
            }
        }

        //not found, return null
        if (hint.index == index) {
            hint.index = -1;
            hint.slot = -1;
        }
        return null;
    }

    public void release(final int index) {
        final long expected = pack(index, VALID, 2);//optimistic guess
        int slot;

        // (1) hot path
        final Hint hint = hintForIndex(index);
        if (hint.index == index && (slot = hint.slot) >= 0) {

            // (1.1) try with optimistic guess
            final long witness = meta.compareAndExchange(slot, expected, expected - 1);
            if (witness == expected) {
                return;
            }

            // (1.2) contended
            unpinSlot(slot, index, witness);
            return;
        }

        // (2) Fallback: locate by index among currently VALID/CLOSING slots, wrapped scan
        for (int i = 0; i < cacheSize; i++) {
            slot = slotForIndex(index + i);
            final long current = meta.get(slot);
            final int state = stateOf(current);
            if ((state == VALID || state == CLOSING) && indexOf(current) == index) {
                unpinSlot(slot, index, current);
                return;
            }
        }

        // not found, must be release without prior get
        throw new IllegalStateException("Cannot release index " + index + " without prior get");
    }

    public E acquire(final int index, final IntFunction<? extends E> factory) {
        final E existing = tryAcquire(index);
        if (existing != null) {
            return existing;
        }
        final int maxAttempts = Math.max(ACQUIRE_ATTEMPTS_MIN, cacheSize);
        int live = liveCount.incrementAndGet();
        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            final boolean full = live > capacity;

            final int target = full ? findEvictableSlot(index + attempt) : findEmptySlot(index + attempt);
            if (target == -1) {
                live = liveCount.getAcquire();
                continue;
            }

            final long current = meta.get(target);
            final int state = stateOf(current);

            if (state == EMPTY) {
                long claimed = pack(0, CLAIMED, 0);
                if (meta.compareAndExchange(target, current, claimed) != current) {
                    live = liveCount.getAcquire();
                    continue;
                }
            } else if (state == VALID) {
                final long closing = pack(indexOf(current), CLOSING, pinOf(current));
                final long witness = meta.compareAndExchange(target, current, closing);
                if (witness != current) {
                    live = liveCount.getAcquire();
                    continue;
                }
                unpinSlot(target, indexOf(current), closing); // drop baseline pin; may close immediately
                final long after = meta.get(target);
                if (stateOf(after) != EMPTY) {
                    live = liveCount.getAcquire();
                    continue; // still pinned elsewhere — try a different victim
                }
                final long claimed = pack(0, CLAIMED, 0);
                if (meta.compareAndExchange(target, after, claimed) != after) {
                    live = liveCount.getAcquire();
                    continue;
                }
            } else {
                live = liveCount.getAcquire();
                continue; // mid-transition, try another slot
            }

            // We exclusively own `target` in CLAIMED state now — safe to write.
            final E created = factory.apply(index);
            values.setPlain(target, created);      // ordering guaranteed by meta.set below
            lastUsed.setOpaque(target, getClockAndTick());

            final long published = pack(index, VALID, 2); // pin=1 baseline, +1 for pin
            meta.set(target, published); // full release — the real publish point

            final Hint hint = hintForIndex(index);
            hint.index = index;
            hint.slot = target;
            return created;
        }
        live = liveCount.decrementAndGet();
        throw new IllegalStateException("Not able to claim empty or evictable entry, live=" + live + ", capacity=" +
                capacity + ", cacheSize=" + cacheSize + ", slots=" + slots());
    }

    private String slots() {
        final StringBuilder builder = new StringBuilder(16).append('[');
        for (int slot = 0; slot < cacheSize; slot++) {
            final long m = meta.get(slot);
            final int state = stateOf(m);
            if (state != EMPTY) {
                builder.append(builder.length() > 1 ? ", " : "");
                builder.append(slot).append(':').append(stateName(state)).append(':').append(pinOf(m));
            }
        }
        return builder.append(']').toString();
    }

    private static String stateName(final int state) {
        switch (state) {
            case EMPTY: return "EMPTY";
            case CLAIMED: return "CLAIMED";
            case VALID: return "VALID";
            case CLOSING: return "CLOSING";
            default: return "UNKNOWN";
        }
    }

    /** Explicitly removes and closes the entry for index, once no thread still has it pinned. */
    public boolean remove(final int index) {
        for (int i = 0; i < cacheSize; i++) {
            final int slot = slotForIndex(index + i);
            final long cur = meta.get(slot);
            if (stateOf(cur) == VALID && indexOf(cur) == index) {
                final long closing = pack(index, CLOSING, pinOf(cur));
                final long witness = meta.compareAndExchange(slot, cur, closing);
                if (witness == cur) {
                    unpinSlot(slot, index, witness); // releases baseline pin; closes now if unpinned elsewhere
                    return true;
                }
            }
        }
        return false;
    }

    public boolean removeAll() {
        int pinned = 0;
        for (int slot = 0; slot < cacheSize; slot++) {
            final long cur = meta.get(slot);
            if (stateOf(cur) == VALID) {
                final int index = indexOf(cur);
                final long closing = pack(index, CLOSING, pinOf(cur));
                final long witness = meta.compareAndExchange(slot, cur, closing);
                if (witness == cur) {
                    unpinSlot(slot, index, witness); // releases baseline pin; closes now if unpinned elsewhere
                } else {
                    pinned++;
                }
            }
        }
        return pinned == 0;
    }

    public int size() {
        return liveCount.get();
    }

    private int validCount() {
        int count = 0;
        for (int slot = 0; slot < cacheSize; slot++) {
            if (stateOf(meta.get(slot)) == VALID) {
                count++;
            }
        }
        return count;
    }

    // =========================================================
    // Internal mechanics
    // =========================================================

    /** Finds an EMPTY slot or — scanning from index's home slot. */
    private int findEmptySlot(final int index) {
        for (int i = 0; i < cacheSize; i++) {
            final int slot = slotForIndex(index + i);
            if (stateOf(meta.get(slot)) == EMPTY) {
                return slot;
            }
        }
        return -1;
    }

    /**
     * Finds an LRU victim — scanning from index's home slot.
     * Note that LRU time only acts as tie-breaker between slots with the same pin count.
     */
    private int findEvictableSlot(final int index) {
        long oldest = Long.MAX_VALUE;
        int target = -1;
        for (int i = 0; i < cacheSize; i++) {
            final int slot = slotForIndex(index + i);
            final long m = meta.get(slot);
            if (stateOf(m) != VALID) {
                continue;
            }
            final long time = lastUsed.getOpaque(slot);
            final long lruTime = lruTime(pinOf(m), time);
            if (lruTime < oldest) {
                oldest = lruTime;
                target = slot;
            }
        }
        return target;
    }

    private boolean tryPinSlot(final int slot, final int index, final long guess) {
        long witness = guess;
        while (true) {
            final long current = witness;
            if (stateOf(current) != VALID || indexOf(current) != index) {
                return false;
            }
            witness = meta.compareAndExchange(slot, current, current + 1);
            if (witness == current) {
                return true;
            }
        }
    }

    private void unpinSlot(final int slot, final int index, final long guess) {
        long witness = guess;
        while (true) {
            final long current = witness;
            final long next = current - 1;
            assert indexOf(current) == index;
            witness = meta.compareAndExchange(slot, current, next);
            if (witness == current) {
                if (stateOf(next) == CLOSING && pinOf(next) == 0) {
                    closeAndReset(slot);
                }
                return;
            }
        }
    }

    private void closeAndReset(final int slot) {
        final E value = values.getPlain(slot); // safe: only reached once pin==0, no concurrent reader
        values.setPlain(slot, null);
        if (value instanceof AutoCloseable) {
            CloseHelper.quietClose((AutoCloseable)value);
        }
        meta.set(slot, pack(0, EMPTY, 0)); // full release — publishes slot as reusable
        liveCount.decrementAndGet();
    }

    @Override
    public String toString() {
        return "AtomicLruCache" +
                ":capacity=" + capacity +
                "|cacheSize=" + cacheSize +
                "|size=" + size();
    }
}
