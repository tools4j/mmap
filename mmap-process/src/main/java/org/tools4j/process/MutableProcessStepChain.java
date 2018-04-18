/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2018 hover-raft (tools4j), Anton Anufriev, Marco Terzer
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

import java.util.function.BiFunction;

public class MutableProcessStepChain {
    private static final BiFunction<ProcessStep, ProcessStep, ProcessStep> THEN = ProcessStep::then;
    private static final BiFunction<ProcessStep, ProcessStep, ProcessStep> THEN_IF_WORK_DONE = ProcessStep::thenIfWorkDone;
    private static final BiFunction<ProcessStep, ProcessStep, ProcessStep> THEN_IF_WORK_NOT_DONE = ProcessStep::thenIfWorkNotDone;

    private ProcessStep runningStep;

    public void addStep(final ProcessStep newStep, final BiFunction<ProcessStep, ProcessStep, ProcessStep> transformation) {
        runningStep = (runningStep == null) ? newStep : transformation.apply(runningStep, newStep);
    }

    public void thenStep(final ProcessStep newStep) {
        addStep(newStep, THEN);
    }

    public void thenStepIfWorkDone(final ProcessStep newStep) {
        addStep(newStep, THEN_IF_WORK_DONE);
    }

    public void thenStepIfWorkNotDone(final ProcessStep newStep) {
        addStep(newStep, THEN_IF_WORK_NOT_DONE);
    }

    public ProcessStep getOrNull() {
        return runningStep;
    }

    public ProcessStep getOrNoop() {
        return runningStep == null ? ProcessStep.NO_OP : runningStep;
    }

    public static MutableProcessStepChain create() {
        return new MutableProcessStepChain();
    }
}