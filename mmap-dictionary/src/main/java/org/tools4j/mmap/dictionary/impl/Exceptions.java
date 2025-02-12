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
package org.tools4j.mmap.dictionary.impl;

import org.tools4j.mmap.region.api.RegionMetrics;

enum Exceptions {
    ;

    static IllegalArgumentException invalidIndexException(final String name, final long index) {
        return new IllegalArgumentException("Invalid index for " + name + ": " + index);
    }

//    static IllegalStateException payloadPositionExceedsMaxException(final UpdaterImpl updater, final long position) {
//        throw new IllegalStateException("Moving " + updater.updaterName() + ".payload to position " + position +
//                " exceeds max allowed position " + MAX_PAYLOAD_POSITION);
//    }
//
//    static IllegalStateException headerMoveException(final UpdaterImpl updater, final long position) {
//        return mappingMoveException(updater.updaterName() + ".header", position);
//    }
//
//    static IllegalStateException payloadMoveException(final UpdaterImpl updater, final long position) {
//        return mappingMoveException(updater.updaterName() + ".payload", position);
//    }
//
//    static IllegalStateException payloadMoveException(final LookupImpl lookup,
//                                                      final int updaterId,
//                                                      final long position) {
//        return mappingMoveException(lookup.lookupName() + "-" + updaterId + ".payload", position);
//    }
//
//    static IllegalStateException payloadMoveException(final DictionaryIteratorImpl iterator,
//                                                      final int updaterId,
//                                                      final long position) {
//        return mappingMoveException(iterator.iteratorName() + "-" + updaterId + ".payload", position);
//    }

    static IllegalStateException mappingMoveToNextRegionException(final String name,
                                                                  final RegionMetrics metrics,
                                                                  final long position) {
        final long regionIndex = metrics.regionIndex(position);
        throw mappingMoveException("data", metrics.regionPositionByIndex(regionIndex + 1));
    }

    static IllegalStateException mappingMoveException(final String name, final long position) {
        return new IllegalStateException("Moving " + name + " mapping to position " + position + " failed");
    }
}
