package io.github.adityasanjay.cpuscheduler.memory;

import io.github.adityasanjay.cpuscheduler.domain.ProcessControlBlock;
import io.github.adityasanjay.cpuscheduler.runtime.SimulationClock;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;

/**
 * Demand-paged frame table. It models metadata and latency, not host RAM bytes.
 * One synchronized frame table makes page ownership and LRU replacement atomic.
 */
public final class PagedMemoryManager implements AutoCloseable {
    private final List<Frame> frames;
    private final Map<PageId, Integer> pageTable = new HashMap<>();
    private final SimulationClock clock;
    private final MemoryConfig config;
    private final ExecutorService pager;
    private final BiConsumer<ProcessControlBlock, Integer> pageLoaded;

    public PagedMemoryManager(MemoryConfig config, SimulationClock clock,
                              BiConsumer<ProcessControlBlock, Integer> pageLoaded) {
        this.config = config;
        this.clock = clock;
        this.pageLoaded = pageLoaded;
        this.frames = new ArrayList<>(config.physicalFrames());
        for (int i = 0; i < config.physicalFrames(); i++) {
            frames.add(new Frame(i));
        }
        pager = Executors.newFixedThreadPool(config.pagerCount(), namedFactory());
    }

    /** True is an LRU cache hit; false means the caller must block for a page fault. */
    public synchronized boolean access(long pid, int pageNumber) {
        Integer frame = pageTable.get(new PageId(pid, pageNumber));
        if (frame == null) {
            return false;
        }
        frames.get(frame).lastAccessMillis = clock.nowMillis();
        return true;
    }

    /** Page-in happens away from CPU workers and invokes completion only after installation. */
    public void requestPage(ProcessControlBlock process, int pageNumber) {
        pager.execute(() -> {
            try {
                clock.sleepMillis(config.pageFaultLatencyMillis());
                install(process.spec().pid(), pageNumber);
                pageLoaded.accept(process, pageNumber);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
        });
    }

    public synchronized void releaseProcess(long pid) {
        for (Frame frame : frames) {
            if (frame.page != null && frame.page.pid == pid) {
                pageTable.remove(frame.page);
                frame.page = null;
            }
        }
    }

    public synchronized MemorySnapshot snapshot() {
        int resident = (int) frames.stream().filter(frame -> frame.page != null).count();
        return new MemorySnapshot(frames.size(), resident, frames.size() - resident);
    }

    private synchronized void install(long pid, int pageNumber) {
        PageId requested = new PageId(pid, pageNumber);
        Integer existing = pageTable.get(requested);
        if (existing != null) {
            frames.get(existing).lastAccessMillis = clock.nowMillis();
            return;
        }
        Frame victim = frames.stream().filter(frame -> frame.page == null).findFirst()
                .orElseGet(() -> frames.stream().min((left, right) ->
                        Long.compare(left.lastAccessMillis, right.lastAccessMillis)).orElseThrow());
        if (victim.page != null) {
            pageTable.remove(victim.page);
        }
        victim.page = requested;
        victim.lastAccessMillis = clock.nowMillis();
        pageTable.put(requested, victim.index);
    }

    @Override
    public void close() {
        pager.shutdownNow();
    }

    private static ThreadFactory namedFactory() {
        AtomicInteger sequence = new AtomicInteger();
        return runnable -> new Thread(runnable, "page-fault-worker-" + sequence.incrementAndGet());
    }

    private record PageId(long pid, int pageNumber) { }

    private static final class Frame {
        private final int index;
        private PageId page;
        private long lastAccessMillis;

        private Frame(int index) {
            this.index = index;
        }
    }

    public record MemorySnapshot(int frameCapacity, int residentFrames, int freeFrames) { }
}
