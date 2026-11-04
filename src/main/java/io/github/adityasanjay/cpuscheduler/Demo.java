package io.github.adityasanjay.cpuscheduler;

import io.github.adityasanjay.cpuscheduler.domain.Burst;
import io.github.adityasanjay.cpuscheduler.domain.ProcessSpec;
import io.github.adityasanjay.cpuscheduler.events.EventSink;
import io.github.adityasanjay.cpuscheduler.runtime.SimulationConfig;
import io.github.adityasanjay.cpuscheduler.runtime.SimulationEngine;
import io.github.adityasanjay.cpuscheduler.runtime.SystemSimulationClock;

import java.time.Duration;
import java.util.List;

/** A runnable demonstration of CPU/I-O overlap across cores. */
public final class Demo {
    private Demo() { }

    public static void main(String[] args) throws InterruptedException {
        EventSink console = EventSink.from(event -> System.out.printf("[%04dms] %-16s PID-%d %s%n",
                event.timeMillis(), event.type(), event.pid(), event.detail()));
        try (SimulationEngine engine = new SimulationEngine(
                SimulationConfig.defaults(), new SystemSimulationClock(), console)) {
            engine.start();
            engine.submit(new ProcessSpec(1, 4, 0,
                    List.of(Burst.cpu(18), Burst.diskIo(35), Burst.cpu(8))));
            engine.submit(new ProcessSpec(2, 2, 0,
                    List.of(Burst.cpu(6), Burst.networkIo(20), Burst.cpu(15))));
            engine.submit(new ProcessSpec(3, 1, 0, List.of(Burst.cpu(45))));
            if (!engine.awaitCompletion(Duration.ofSeconds(3))) {
                throw new IllegalStateException("Demo timed out");
            }
            System.out.println(engine.metrics());
        }
    }
}
