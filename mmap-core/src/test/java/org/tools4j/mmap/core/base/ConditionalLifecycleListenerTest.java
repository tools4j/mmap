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

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit test for {@link LifecycleListener#conditional(java.util.function.Predicate, LifecycleListener, LifecycleListener)}
 * and {@link ConditionalLifecycleListener}.
 */
class ConditionalLifecycleListenerTest {

    private static final Describable INTERNAL = new ConstantDescribable("internal");
    private static final Describable EXTERNAL = new ConstantDescribable("external");

    /** Records events as "listener:event:resource" */
    private static LifecycleListener recording(final String name, final List<String> events) {
        return new LifecycleListener() {
            @Override
            public void onCreated(final Describable resource) {
                events.add(name + ":created:" + resource);
            }

            @Override
            public void onOpened(final Describable resource) {
                events.add(name + ":opened:" + resource);
            }

            @Override
            public void onClosed(final Describable resource) {
                events.add(name + ":closed:" + resource);
            }
        };
    }

    @Test
    void passesEachEventToOneListenerByResource() {
        final List<String> events = new ArrayList<>();
        final LifecycleListener listener = LifecycleListener.conditional(resource -> resource == INTERNAL,
                recording("matching", events), recording("other", events));
        listener.onCreated(INTERNAL);
        listener.onCreated(EXTERNAL);
        listener.onOpened(INTERNAL);
        listener.onOpened(EXTERNAL);
        listener.onClosed(INTERNAL);
        listener.onClosed(EXTERNAL);
        assertEquals(List.of(
                "matching:created:internal", "other:created:external",
                "matching:opened:internal", "other:opened:external",
                "matching:closed:internal", "other:closed:external"), events);
    }

    @Test
    void toStringShowsConditionAndListeners() {
        final LifecycleListener listener = LifecycleListener.conditional(new java.util.function.Predicate<Describable>() {
            @Override
            public boolean test(final Describable resource) {
                return false;
            }

            @Override
            public String toString() {
                return "never";
            }
        }, LifecycleListener.NO_OP, LifecycleListener.NO_OP);
        assertTrue(listener.toString().contains("condition=never|matching=LifecycleListener.NO_OP|"),
                listener::toString);
    }

    @Test
    void nullArgumentsAreRejected() {
        assertThrows(NullPointerException.class,
                () -> LifecycleListener.conditional(null, LifecycleListener.NO_OP, LifecycleListener.NO_OP));
        assertThrows(NullPointerException.class,
                () -> LifecycleListener.conditional(resource -> true, null, LifecycleListener.NO_OP));
        assertThrows(NullPointerException.class,
                () -> LifecycleListener.conditional(resource -> true, LifecycleListener.NO_OP, null));
    }
}
