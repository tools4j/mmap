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
package org.tools4j.process;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;

public class ProcessLoop implements Runnable {
    private static final Logger LOGGER = LoggerFactory.getLogger(ProcessLoop.class);

    private final String name;
    private final Runnable onStartHandler;
    private final Runnable onStopHandler;
    private final BooleanSupplier shutdownCondition;
    private final BooleanSupplier shutdownAbortCondition;
    private final IdleStrategy idleStrategy;
    private final BiConsumer<? super String, ? super Exception> exceptionHandler;
    private final ProcessStep[] steps;

    public ProcessLoop(final String name,
                       final Runnable onStartHandler,
                       final Runnable onStopHandler,
                       final BooleanSupplier shutdownCondition,
                       final BooleanSupplier shutdownAbortCondition,
                       final IdleStrategy idleStrategy,
                       final BiConsumer<? super String, ? super Exception> exceptionHandler,
                       final ProcessStep... steps) {
        this.name = Objects.requireNonNull(name);
        this.onStartHandler = Objects.requireNonNull(onStartHandler);
        this.onStopHandler = Objects.requireNonNull(onStopHandler);
        this.shutdownCondition = Objects.requireNonNull(shutdownCondition);
        this.shutdownAbortCondition = Objects.requireNonNull(shutdownAbortCondition);
        this.idleStrategy = Objects.requireNonNull(idleStrategy);
        this.exceptionHandler = Objects.requireNonNull(exceptionHandler);
        this.steps = Objects.requireNonNull(steps);
    }

    @Override
    public void run() {
        onStartHandler.run();
        executeLoop();
        onStopHandler.run();
    }

    private boolean executeLoop() {
        LOGGER.info("Started {} process loop", name);
        while (!shutdownCondition.getAsBoolean()) {
            idleStrategy.idle(executeSteps());
        }
        LOGGER.info("Shutting down {} process loop", name);
        boolean aborted = shutdownAbortCondition.getAsBoolean();
        boolean finalised = false;
        while(!finalised && !aborted) {
            finalised = finaliseSteps();
            aborted = shutdownAbortCondition.getAsBoolean() && !finalised;
        }
        LOGGER.info("Finished {} process loop, finalised={}, aborted={}", name, finalised, aborted);
        return finalised;
    }

    private boolean executeSteps() {
        boolean workDone = false;
        for (final ProcessStep step : steps) {
            try {
                workDone |= step.execute();
            } catch (final Exception ex) {
                exceptionHandler.accept(name, ex);
                workDone |= false;
            }
        }
        return workDone;
    }

    private boolean finaliseSteps() {
        boolean finalised = true;
        for (final ProcessStep step : steps) {
            try {
                finalised &= step.finalise();
            } catch (final Exception ex) {
                exceptionHandler.accept(name, ex);
            }
        }
        return finalised;
    }

    public String name() {
        return name;
    }
}
