/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2016-2018 mmap (tools4j), Marco Terzer, Anton Anufriev
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
package org.tools4j.eventsourcing.api;

import java.io.IOException;

/**
 * Factory of pollers with certain behaviour on the event indexes.
 */
public interface IndexedPollerFactory {
    /**
     * Creates a poller with parametrised index behaviour.
     *
     * @param skipPredicate skip predicate is used to decide when to skip processing the event and move to the next position.
     * @param pausePredicate pause predicate is used to decide when to halt processing and stay at the current position.
     * @param beforeIndexHandler the handler is used to notify before processing of the actual message
     * @param afterIndexHandler the handler is used to notify after processing of the actual message
     * @return new instance of a poller.
     * @throws IOException when a backing file could not be read/mapped.
     */
    Poller createPoller(Poller.IndexPredicate skipPredicate,
                        Poller.IndexPredicate pausePredicate,
                        Poller.IndexConsumer beforeIndexHandler,
                        Poller.IndexConsumer afterIndexHandler) throws IOException;
}
