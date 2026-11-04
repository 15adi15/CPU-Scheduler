package io.github.adityasanjay.cpuscheduler.runtime;

import java.util.List;

public record SchedulerMetrics(
        long submitted,
        long completed,
        long contextSwitches,
        long ioRequests,
        long pageFaults,
        long cpuBusyMillis,
        double averageResponseMillis,
        double averageTurnaroundMillis,
        List<Integer> queueDepths) {
}
