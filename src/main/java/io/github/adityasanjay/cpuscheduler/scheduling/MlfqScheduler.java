package io.github.adityasanjay.cpuscheduler.scheduling;

import io.github.adityasanjay.cpuscheduler.domain.ProcessControlBlock;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/** Blocking multi-level feedback queue. Its lock protects all queue membership. */
public final class MlfqScheduler {
    private final List<ArrayDeque<ProcessControlBlock>> queues;
    private final ReentrantLock lock = new ReentrantLock(true);
    private final Condition workAvailable = lock.newCondition();
    private boolean accepting = true;

    public MlfqScheduler(int levels) {
        if (levels < 1) {
            throw new IllegalArgumentException("MLFQ needs at least one level");
        }
        queues = new ArrayList<>(levels);
        for (int i = 0; i < levels; i++) {
            queues.add(new ArrayDeque<>());
        }
    }

    public void enqueue(ProcessControlBlock process) {
        lock.lock();
        try {
            if (!accepting) {
                throw new IllegalStateException("Scheduler is closed");
            }
            queues.get(normalize(process.queueLevel())).addLast(process);
            workAvailable.signal();
        } finally {
            lock.unlock();
        }
    }

    /** Returns null only after close() and after every queue is drained. */
    public ProcessControlBlock take() throws InterruptedException {
        lock.lockInterruptibly();
        try {
            while (isEmpty() && accepting) {
                workAvailable.await();
            }
            if (isEmpty()) {
                return null;
            }
            for (ArrayDeque<ProcessControlBlock> queue : queues) {
                if (!queue.isEmpty()) {
                    return queue.removeFirst();
                }
            }
            return null;
        } finally {
            lock.unlock();
        }
    }

    /** Aging is atomic with queue membership, so a process cannot appear twice. */
    public int promoteAged(long nowMillis, long ageThresholdMillis) {
        lock.lock();
        try {
            int promoted = 0;
            for (int level = 1; level < queues.size(); level++) {
                ArrayDeque<ProcessControlBlock> queue = queues.get(level);
                int queueSize = queue.size();
                for (int i = 0; i < queueSize; i++) {
                    ProcessControlBlock process = queue.removeFirst();
                    if (nowMillis - process.readySinceMillis() >= ageThresholdMillis
                            && process.promoteIfReady(0)) {
                        queues.getFirst().addLast(process);
                        promoted++;
                    } else {
                        queue.addLast(process);
                    }
                }
            }
            if (promoted > 0) {
                workAvailable.signalAll();
            }
            return promoted;
        } finally {
            lock.unlock();
        }
    }

    public List<Integer> queueDepths() {
        lock.lock();
        try {
            return queues.stream().map(ArrayDeque::size).toList();
        } finally {
            lock.unlock();
        }
    }

    public int levels() {
        return queues.size();
    }

    public void close() {
        lock.lock();
        try {
            accepting = false;
            workAvailable.signalAll();
        } finally {
            lock.unlock();
        }
    }

    private int normalize(int level) {
        return Math.max(0, Math.min(level, queues.size() - 1));
    }

    private boolean isEmpty() {
        return queues.stream().allMatch(ArrayDeque::isEmpty);
    }
}
