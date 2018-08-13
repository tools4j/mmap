package org.tools4j.eventsourcing;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.tools4j.eventsourcing.step.DownstreamWhileDoneThenUpsteamUntilDoneStep;
import org.tools4j.process.ProcessStep;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class DownstreamWhileDoneThenUpsteamUntilDoneStepTest {
    @Mock
    private ProcessStep inStep;
    @Mock
    private ProcessStep outStep;

    @Test
    public void execute() throws Exception {

        when(outStep.execute()).thenReturn(true, true, false, true, false);
        when(inStep.execute()).thenReturn(false, false, true);

        final DownstreamWhileDoneThenUpsteamUntilDoneStep processorStep = new DownstreamWhileDoneThenUpsteamUntilDoneStep(inStep::execute, outStep::execute);


        processorStep.execute();
        processorStep.execute();
        processorStep.execute();
        processorStep.execute();
        processorStep.execute();
        processorStep.execute();
        processorStep.execute();
        processorStep.execute();

        verify(outStep, times(5)).execute();
        verify(inStep, times(3)).execute();
    }

}