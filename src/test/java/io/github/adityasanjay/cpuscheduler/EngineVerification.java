package io.github.adityasanjay.cpuscheduler;

import io.github.adityasanjay.cpuscheduler.domain.Burst;
import io.github.adityasanjay.cpuscheduler.domain.ProcessSpec;
import io.github.adityasanjay.cpuscheduler.domain.ProcessState;
import io.github.adityasanjay.cpuscheduler.events.EventSink;
import io.github.adityasanjay.cpuscheduler.runtime.SimulationConfig;
import io.github.adityasanjay.cpuscheduler.runtime.SimulationEngine;
import io.github.adityasanjay.cpuscheduler.runtime.SystemSimulationClock;

import java.time.Duration;
import java.util.List;

/**
 * Dependency-free integration check. It can run with plain javac while JUnit is
 * intentionally introduced together with the later build-tooling phase.
 */
public final class EngineVerification {
    private EngineVerification() { }

    public static void main(String[] args) throws Exception {
        SimulationConfig config = new SimulationConfig(2, List.of(2L, 4L), 25, 5, 1, 1, true);
        try (SimulationEngine engine = new SimulationEngine(config, new SystemSimulationClock(), EventSink.noop())) {
            engine.start();
            engine.submit(new ProcessSpec(11, 1, 0,
                    List.of(Burst.cpu(3), Burst.diskIo(4), Burst.cpu(3))));
            engine.submit(new ProcessSpec(12, 1, 0,
                    List.of(Burst.cpu(8), Burst.networkIo(2), Burst.cpu(2))));
            require(engine.awaitCompletion(Duration.ofSeconds(2)), "simulation should drain");
            require(engine.metrics().submitted() == 2, "every submitted process must be counted");
            require(engine.metrics().completed() == 2, "every submitted process must complete exactly once");
            require(engine.metrics().ioRequests() == 2, "I/O bursts must be dispatched asynchronously");
            require(engine.metrics().pageFaults() >= 2, "each process must demand-page before it can execute");
            require(engine.processSnapshots().stream()
                    .allMatch(process -> process.state() == ProcessState.TERMINATED),
                    "no process may remain queued, running, or blocked");
        }
        System.out.println("Engine verification passed");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
