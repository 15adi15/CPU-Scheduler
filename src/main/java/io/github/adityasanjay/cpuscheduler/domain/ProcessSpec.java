package io.github.adityasanjay.cpuscheduler.domain;

import java.util.List;
import java.util.Objects;

/** Immutable workload definition. Runtime state lives in ProcessControlBlock. */
public record ProcessSpec(long pid, int memoryPages, int basePriority, List<Burst> bursts) {
    public ProcessSpec {
        if (pid <= 0 || memoryPages <= 0 || basePriority < 0) {
            throw new IllegalArgumentException("pid, memory pages, and priority must be valid");
        }
        bursts = List.copyOf(Objects.requireNonNull(bursts, "bursts"));
        if (bursts.isEmpty() || bursts.getFirst().type() != BurstType.CPU
                || bursts.getLast().type() != BurstType.CPU) {
            throw new IllegalArgumentException("A process must start and end with a CPU burst");
        }
        for (int i = 1; i < bursts.size(); i++) {
            if (isCpu(bursts.get(i - 1)) == isCpu(bursts.get(i))) {
                throw new IllegalArgumentException("CPU and I/O bursts must alternate");
            }
        }
    }

    private static boolean isCpu(Burst burst) {
        return burst.type() == BurstType.CPU;
    }
}
