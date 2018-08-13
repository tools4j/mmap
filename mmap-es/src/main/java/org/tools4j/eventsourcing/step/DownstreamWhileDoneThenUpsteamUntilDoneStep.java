package org.tools4j.eventsourcing.step;

import org.tools4j.process.ProcessStep;

import java.util.Objects;

public final class DownstreamWhileDoneThenUpsteamUntilDoneStep implements ProcessStep {

    private final ProcessStep upstreamProcessStepState;
    private ProcessStep downstreamProcessStepState;
    private ProcessStep currentStep;


    public DownstreamWhileDoneThenUpsteamUntilDoneStep(final ProcessStep upstreamProcessStep, final ProcessStep downstreamProcessStep) {
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
