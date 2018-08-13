package org.tools4j.eventsourcing.step;

import org.tools4j.eventsourcing.api.MessageConsumer;
import org.tools4j.eventsourcing.api.Poller;
import org.tools4j.process.ProcessStep;

import java.util.Objects;

public final class PollingProcessStep implements ProcessStep {
    private final Poller inputPoller;
    private final MessageConsumer consumer;

    public PollingProcessStep(final Poller inputPoller, final MessageConsumer consumer) {
        this.inputPoller = Objects.requireNonNull(inputPoller);
        this.consumer = Objects.requireNonNull(consumer);
    }

    @Override
    public boolean execute() {
        final int processed = inputPoller.poll(consumer);
        return processed > 0;
    }
}
