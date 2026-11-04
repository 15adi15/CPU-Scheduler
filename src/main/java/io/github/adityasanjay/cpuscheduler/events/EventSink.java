package io.github.adityasanjay.cpuscheduler.events;

import java.util.Objects;
import java.util.function.Consumer;

@FunctionalInterface
public interface EventSink {
    void publish(SimulationEvent event);

    static EventSink from(Consumer<SimulationEvent> consumer) {
        Objects.requireNonNull(consumer, "consumer");
        return consumer::accept;
    }

    static EventSink noop() {
        return ignored -> { };
    }
}
