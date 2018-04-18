package org.tools4j.process;

public interface IdleStrategy {
    void idle(int workDone);

    default void idle() {
        idle(1);
    }

    default void reset() {}
}
