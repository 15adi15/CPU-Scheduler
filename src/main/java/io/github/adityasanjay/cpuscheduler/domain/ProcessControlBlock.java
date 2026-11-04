package io.github.adityasanjay.cpuscheduler.domain;

import java.util.Objects;

/**
 * Thread-safe mutable runtime state for one ProcessSpec. State changes are kept
 * here so cores, device controllers, and the scheduler cannot each invent their
 * own lifecycle rules.
 */
public final class ProcessControlBlock {
    private final ProcessSpec spec;
    private final long createdAtMillis;
    private ProcessState state = ProcessState.NEW;
    private int queueLevel;
    private int burstIndex;
    private long remainingBurstMillis;
    private long readySinceMillis;
    private long firstRunAtMillis = -1;
    private long completedAtMillis = -1;
    private int lastExecutedCoreId = -1;
    private int nextMemoryPage;

    public ProcessControlBlock(ProcessSpec spec, long createdAtMillis) {
        this.spec = Objects.requireNonNull(spec, "spec");
        this.createdAtMillis = createdAtMillis;
        this.remainingBurstMillis = spec.bursts().getFirst().durationMillis();
    }

    public ProcessSpec spec() {
        return spec;
    }

    public synchronized void admit(long now) {
        transition(ProcessState.NEW, ProcessState.ADMITTED);
        makeReady(0, now);
    }

    public synchronized long dispatch(int coreId, long now) {
        transition(ProcessState.READY, ProcessState.RUNNING);
        lastExecutedCoreId = coreId;
        if (firstRunAtMillis < 0) {
            firstRunAtMillis = now;
        }
        return now - readySinceMillis;
    }

    /** Must be called after dispatch and before the core begins its timed work. */
    public synchronized long plannedCpuSlice(long quantumMillis) {
        requireState(ProcessState.RUNNING);
        if (currentBurst().type() != BurstType.CPU) {
            throw new IllegalStateException("A non-CPU burst was dispatched");
        }
        return Math.min(quantumMillis, remainingBurstMillis);
    }

    /** Consume one CPU time slice. The caller must have dispatched the process. */
    public synchronized SliceResult consumeCpu(long requestedMillis, long now) {
        requireState(ProcessState.RUNNING);
        Burst current = currentBurst();
        if (current.type() != BurstType.CPU) {
            throw new IllegalStateException("A non-CPU burst was dispatched");
        }
        long consumed = Math.min(requestedMillis, remainingBurstMillis);
        remainingBurstMillis -= consumed;
        if (remainingBurstMillis > 0) {
            return new SliceResult(consumed, false, null, false);
        }

        burstIndex++;
        if (burstIndex == spec.bursts().size()) {
            state = ProcessState.TERMINATED;
            completedAtMillis = now;
            return new SliceResult(consumed, true, null, true);
        }
        Burst ioBurst = currentBurst();
        if (ioBurst.type() == BurstType.CPU) {
            throw new IllegalStateException("Burst sequence must alternate");
        }
        remainingBurstMillis = ioBurst.durationMillis();
        state = ProcessState.BLOCKED_IO;
        return new SliceResult(consumed, true, ioBurst, false);
    }

    public synchronized void requeue(int nextQueueLevel, long now) {
        requireState(ProcessState.RUNNING);
        makeReady(nextQueueLevel, now);
    }

    /** Called only after a device controller has completed the current I/O burst. */
    public synchronized void completeIo(int readyQueueLevel, long now) {
        requireState(ProcessState.BLOCKED_IO);
        remainingBurstMillis = 0;
        burstIndex++;
        if (burstIndex >= spec.bursts().size() || currentBurst().type() != BurstType.CPU) {
            throw new IllegalStateException("I/O completion did not lead to CPU work");
        }
        remainingBurstMillis = currentBurst().durationMillis();
        makeReady(readyQueueLevel, now);
    }

    public synchronized int blockForPageFault() {
        requireState(ProcessState.RUNNING);
        state = ProcessState.BLOCKED_PAGE_FAULT;
        return nextMemoryPage;
    }

    public synchronized void completePageFault(int readyQueueLevel, long now) {
        transition(ProcessState.BLOCKED_PAGE_FAULT, ProcessState.READY);
        queueLevel = readyQueueLevel;
        readySinceMillis = now;
    }

    /** Generates a deterministic working-set trace until richer traces are added. */
    public synchronized int nextMemoryPage() {
        return nextMemoryPage;
    }

    /** Advance only after the current logical page has actually been accessed. */
    public synchronized void completeMemoryAccess() {
        nextMemoryPage = (nextMemoryPage + 1) % spec.memoryPages();
    }

    public synchronized boolean promoteIfReady(int targetQueueLevel) {
        if (state != ProcessState.READY || queueLevel <= targetQueueLevel) {
            return false;
        }
        queueLevel = targetQueueLevel;
        return true;
    }

    public synchronized int queueLevel() {
        return queueLevel;
    }

    public synchronized long readySinceMillis() {
        return readySinceMillis;
    }

    public synchronized long responseTimeMillis() {
        return firstRunAtMillis < 0 ? -1 : firstRunAtMillis - createdAtMillis;
    }

    public synchronized long turnaroundTimeMillis() {
        return completedAtMillis < 0 ? -1 : completedAtMillis - createdAtMillis;
    }

    public synchronized ProcessSnapshot snapshot() {
        return new ProcessSnapshot(spec.pid(), state, queueLevel, burstIndex, remainingBurstMillis,
                lastExecutedCoreId, createdAtMillis, completedAtMillis);
    }

    private void makeReady(int targetQueueLevel, long now) {
        state = ProcessState.READY;
        queueLevel = targetQueueLevel;
        readySinceMillis = now;
    }

    private Burst currentBurst() {
        return spec.bursts().get(burstIndex);
    }

    private void transition(ProcessState expected, ProcessState target) {
        requireState(expected);
        state = target;
    }

    private void requireState(ProcessState expected) {
        if (state != expected) {
            throw new IllegalStateException("PID " + spec.pid() + " is " + state + ", expected " + expected);
        }
    }

    public record SliceResult(long consumedMillis, boolean burstFinished, Burst ioBurst, boolean terminated) {
    }
}
