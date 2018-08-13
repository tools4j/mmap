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
package org.tools4j.eventsourcing.step;

import org.tools4j.process.ProcessStep;

import java.util.Objects;

public final class DownstreamWhileDoneThenUpstreamUntilDoneStep implements ProcessStep {

    private final ProcessStep upstreamProcessStepState;
    private ProcessStep downstreamProcessStepState;
    private ProcessStep currentStep;


    public DownstreamWhileDoneThenUpstreamUntilDoneStep(final ProcessStep upstreamProcessStep, final ProcessStep downstreamProcessStep) {
        Objects.requireNonNull(upstreamProcessStep);
        Objects.requireNonNull(downstreamProcessStep);

        upstreamProcessStepState = () -> {
            final boolean workDone = upstreamProcessStep.execute();
            if (workDone) currentStep = downstreamProcessStepState;
            return workDone;
        };

        downstreamProcessStepState = () -> {
            final boolean workDone = downstreamProcessStep.execute();
            if (!workDone) currentStep = upstreamProcessStepState;
            return workDone;
        };

        currentStep = downstreamProcessStepState;
    }

    @Override
    public boolean execute() {
        return currentStep.execute();
    }
}
