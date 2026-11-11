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
package org.tools4j.mmap.core.base;

import java.util.function.Predicate;

import static java.util.Objects.requireNonNull;

/**
 * Lifecycle listener passing each event on to one of two listeners, depending on the resource, created by
 * {@link LifecycleListener#conditional(Predicate, LifecycleListener, LifecycleListener)}.
 */
final class ConditionalLifecycleListener implements LifecycleListener {

    private final Predicate<? super Describable> condition;
    private final LifecycleListener matching;
    private final LifecycleListener other;

    ConditionalLifecycleListener(final Predicate<? super Describable> condition,
                                 final LifecycleListener matching,
                                 final LifecycleListener other) {
        this.condition = requireNonNull(condition);
        this.matching = requireNonNull(matching);
        this.other = requireNonNull(other);
    }

    private LifecycleListener listenerFor(final Describable resource) {
        return condition.test(resource) ? matching : other;
    }

    @Override
    public void onCreated(final Describable resource) {
        listenerFor(resource).onCreated(resource);
    }

    @Override
    public void onOpened(final Describable resource) {
        listenerFor(resource).onOpened(resource);
    }

    @Override
    public void onClosed(final Describable resource) {
        listenerFor(resource).onClosed(resource);
    }

    @Override
    public String toString() {
        return "ConditionalLifecycleListener:condition=" + condition + "|matching=" + matching + "|other=" + other;
    }
}
