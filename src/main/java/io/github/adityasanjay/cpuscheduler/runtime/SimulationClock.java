package io.github.adityasanjay.cpuscheduler.runtime;

/** Separates simulation time from scheduler policy; tests can provide a fake clock. */
public interface SimulationClock {
    long nowMillis();

    void sleepMillis(long durationMillis) throws InterruptedException;
}
