package io.github.adityasanjay.cpuscheduler.runtime;

import io.github.adityasanjay.cpuscheduler.cache.CacheConfig;
import io.github.adityasanjay.cpuscheduler.memory.MemoryConfig;

import java.util.List;

/** All timing and capacity choices are explicit, enabling repeatable experiments. */
public record SimulationConfig(
        int coreCount,
        List<Long> quantumsMillis,
        long agingThresholdMillis,
        long agingScanPeriodMillis,
        int diskDeviceCount,
        int networkDeviceCount,
        boolean ioPriorityBoost,
        MemoryConfig memory,
        CacheConfig cache) {

    public SimulationConfig {
        if (coreCount < 1 || quantumsMillis == null || quantumsMillis.isEmpty()
                || agingThresholdMillis < 1 || agingScanPeriodMillis < 1
                || diskDeviceCount < 1 || networkDeviceCount < 1 || memory == null || cache == null) {
            throw new IllegalArgumentException("Simulation configuration contains an invalid capacity or duration");
        }
        quantumsMillis = List.copyOf(quantumsMillis);
        if (quantumsMillis.stream().anyMatch(quantum -> quantum == null || quantum <= 0)) {
            throw new IllegalArgumentException("Every quantum must be positive");
        }
    }

    public SimulationConfig(int coreCount, List<Long> quantumsMillis, long agingThresholdMillis,
                            long agingScanPeriodMillis, int diskDeviceCount, int networkDeviceCount,
                            boolean ioPriorityBoost) {
        this(coreCount, quantumsMillis, agingThresholdMillis, agingScanPeriodMillis, diskDeviceCount,
                networkDeviceCount, ioPriorityBoost, MemoryConfig.defaults(), CacheConfig.defaults());
    }

    public static SimulationConfig defaults() {
        return new SimulationConfig(4, List.of(10L, 20L, 40L), 500L, 100L, 1, 2, true,
                MemoryConfig.defaults(), CacheConfig.defaults());
    }
}
