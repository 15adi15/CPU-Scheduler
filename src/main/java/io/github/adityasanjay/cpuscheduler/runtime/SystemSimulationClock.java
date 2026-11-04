package io.github.adityasanjay.cpuscheduler.runtime;

import java.util.concurrent.TimeUnit;

public final class SystemSimulationClock implements SimulationClock {
    private final long startedAtNanos = System.nanoTime();

    @Override
    public long nowMillis() {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAtNanos);
    }

    @Override
    public void sleepMillis(long durationMillis) throws InterruptedException {
        Thread.sleep(durationMillis);
    }
}
