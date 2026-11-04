package io.github.adityasanjay.cpuscheduler.domain;

public record ProcessSnapshot(
        long pid,
        ProcessState state,
        int queueLevel,
        int burstIndex,
        long remainingBurstMillis,
        int lastExecutedCoreId,
        long createdAtMillis,
        long completedAtMillis) {
}
