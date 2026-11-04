package io.github.adityasanjay.cpuscheduler.events;

public record SimulationEvent(long timeMillis, String type, long pid, String detail) {
}
