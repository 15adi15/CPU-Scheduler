package io.github.adityasanjay.cpuscheduler.runtime;

import io.github.adityasanjay.cpuscheduler.domain.ProcessControlBlock;

import java.util.List;
import java.util.concurrent.atomic.LongAdder;

final class MetricsCollector {
    private final LongAdder submitted = new LongAdder();
    private final LongAdder completed = new LongAdder();
    private final LongAdder contextSwitches = new LongAdder();
    private final LongAdder ioRequests = new LongAdder();
    private final LongAdder pageFaults = new LongAdder();
    private final LongAdder l1CacheHits = new LongAdder();
    private final LongAdder l1CacheMisses = new LongAdder();
    private final LongAdder cpuBusyMillis = new LongAdder();
    private final LongAdder totalResponseMillis = new LongAdder();
    private final LongAdder totalTurnaroundMillis = new LongAdder();

    void submitted() { submitted.increment(); }
    void dispatched() { contextSwitches.increment(); }
    void consumedCpu(long millis) { cpuBusyMillis.add(millis); }
    void ioRequested() { ioRequests.increment(); }
    void pageFault() { pageFaults.increment(); }
    void l1CacheHit() { l1CacheHits.increment(); }
    void l1CacheMiss() { l1CacheMisses.increment(); }

    void completed(ProcessControlBlock process) {
        completed.increment();
        totalResponseMillis.add(process.responseTimeMillis());
        totalTurnaroundMillis.add(process.turnaroundTimeMillis());
    }

    SchedulerMetrics snapshot(List<Integer> queueDepths) {
        long completedCount = completed.sum();
        return new SchedulerMetrics(submitted.sum(), completedCount, contextSwitches.sum(), ioRequests.sum(),
                pageFaults.sum(), l1CacheHits.sum(), l1CacheMisses.sum(), cpuBusyMillis.sum(), completedCount == 0 ? 0 : (double) totalResponseMillis.sum() / completedCount,
                completedCount == 0 ? 0 : (double) totalTurnaroundMillis.sum() / completedCount,
                queueDepths);
    }
}
