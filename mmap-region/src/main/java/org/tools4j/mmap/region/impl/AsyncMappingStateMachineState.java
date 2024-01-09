/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2016-2024 tools4j.org (Marco Terzer, Anton Anufriev)
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

import static org.agrona.UnsafeAccess.UNSAFE;

abstract class AsyncMappingStateMachinePadding1 {
    byte p000, p001, p002, p003, p004, p005, p006, p007, p008, p009, p010, p011, p012, p013, p014, p015;
    byte p016, p017, p018, p019, p020, p021, p022, p023, p024, p025, p026, p027, p028, p029, p030, p031;
    byte p032, p033, p034, p035, p036, p037, p038, p039, p040, p041, p042, p043, p044, p045, p046, p047;
    byte p048, p049, p050, p051, p052, p053, p054, p055, p056, p057, p058, p059, p060, p061, p062, p063;
}

abstract class AsyncMappingStateMachineStateValues extends AsyncMappingStateMachinePadding1 {
    volatile long requestedPosition;
    long requestedPositionCache;
    volatile long mappedPosition;
    long mappedPositionCache;
    long mappedRegionAddress;
}

abstract class AsyncMappingStateMachinePadding3 extends AsyncMappingStateMachineStateValues {
    byte p064, p065, p066, p067, p068, p069, p070, p071, p072, p073, p074, p075, p076, p077, p078, p079;
    byte p080, p081, p082, p083, p084, p085, p086, p087, p088, p089, p090, p091, p092, p093, p094, p095;
    byte p096, p097, p098, p099, p100, p101, p102, p103, p104, p105, p106, p107, p108, p109, p110, p111;
    byte p112, p113, p114, p115, p116, p117, p118, p119, p120, p121, p122, p123, p124, p125, p126, p127;
}

/**
 * Base class for {@link AsyncMappingStateMachine} with member fields and padding.
 */
abstract class AsyncMappingStateMachineState extends AsyncMappingStateMachinePadding3 {
    static final long REQUESTED_POSITION_OFFSET;
    static final long MAPPED_POSITION_OFFSET;

    static {
        try {
            REQUESTED_POSITION_OFFSET = UNSAFE.objectFieldOffset(
                    AsyncMappingStateMachineStateValues.class.getDeclaredField("requestedPosition")
            );
            MAPPED_POSITION_OFFSET = UNSAFE.objectFieldOffset(
                    AsyncMappingStateMachineStateValues.class.getDeclaredField("mappedPosition")
            );
        } catch (final Exception e) {
            throw new RuntimeException(e);
        }
    }
}