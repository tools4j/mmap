/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2016 mmap (tools4j), Marco Terzer
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
package org.tools4j.mmap.io;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Async mapper for regions.
 */
public class AsyncMapper {

    private static volatile int instance = 0;

    private enum State {
        Idle, Initialising, Preparing
    }

    private final Thread thread = new Thread(this::run);
    private final AtomicBoolean stop = new AtomicBoolean(false);
    private final AtomicReference<State> state = new AtomicReference<>(State.Idle);

    private volatile MappedRegion region;
    private volatile long position;
    private volatile AtomicBoolean ready;

    private AsyncMapper() {
        thread.setName(instance == 0 ? "async-mapper" : "async-mapper-" + instance);
        thread.setDaemon(true);
        instance++;
    }

    public static AsyncMapper start() {
        final AsyncMapper asyncMapper = new AsyncMapper();
        asyncMapper.thread.start();
        return asyncMapper;
    }

    public void stop() {
        stop.set(true);
    }

    public void prepare(final MappedRegion region, final long position, final AtomicBoolean ready) {
        while (true) {
            if (state.compareAndSet(State.Idle, State.Initialising)) {
                this.region = region;
                this.position = position;
                this.ready = ready;
                state.set(State.Preparing);
                return;
            }
        }
    }

    private void run() {
        while (!stop.get()) {
            if (state.get() == State.Preparing) {
                region.map(position);
                ready.set(true);
                region = null;
                position = -1;
                ready = null;
                state.set(State.Idle);
            }
        }
    }

}
