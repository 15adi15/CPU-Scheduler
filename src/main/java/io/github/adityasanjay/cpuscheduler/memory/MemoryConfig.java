package io.github.adityasanjay.cpuscheduler.memory;

public record MemoryConfig(int physicalFrames, long pageFaultLatencyMillis, int pagerCount) {
    public MemoryConfig {
        if (physicalFrames < 1 || pageFaultLatencyMillis < 1 || pagerCount < 1) {
            throw new IllegalArgumentException("Memory capacities and page-fault latency must be positive");
        }
    }

    public static MemoryConfig defaults() {
        return new MemoryConfig(32, 15, 1);
    }
}
