package io.github.adityasanjay.cpuscheduler.runtime;

import io.github.adityasanjay.cpuscheduler.cpu.CpuCore;
import io.github.adityasanjay.cpuscheduler.domain.Burst;
import io.github.adityasanjay.cpuscheduler.domain.BurstType;
import io.github.adityasanjay.cpuscheduler.domain.ProcessControlBlock;
import io.github.adityasanjay.cpuscheduler.domain.ProcessSnapshot;
import io.github.adityasanjay.cpuscheduler.domain.ProcessSpec;
import io.github.adityasanjay.cpuscheduler.events.EventSink;
import io.github.adityasanjay.cpuscheduler.events.SimulationEvent;
import io.github.adityasanjay.cpuscheduler.io.DeviceController;
import io.github.adityasanjay.cpuscheduler.memory.PagedMemoryManager;
import io.github.adityasanjay.cpuscheduler.scheduling.MlfqScheduler;

import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/** Owns all concurrent simulation services and supplies a controlled lifecycle. */
public final class SimulationEngine implements AutoCloseable {
    private final SimulationConfig config;
    private final SimulationClock clock;
    private final EventSink events;
    private final MlfqScheduler scheduler;
    private final MetricsCollector metrics = new MetricsCollector();
    private final Map<Long, ProcessControlBlock> processes = new ConcurrentHashMap<>();
    private final AtomicInteger activeProcesses = new AtomicInteger();
    private final Object completionMonitor = new Object();
    private final AtomicBoolean started = new AtomicBoolean();
    private final AtomicBoolean accepting = new AtomicBoolean(true);
    private final ExecutorService coreExecutor;
    private final ScheduledExecutorService maintenanceExecutor;
    private final DeviceController disk;
    private final DeviceController network;
    private final PagedMemoryManager memory;

    public SimulationEngine(SimulationConfig config, SimulationClock clock, EventSink events) {
        this.config = Objects.requireNonNull(config, "config");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.events = Objects.requireNonNull(events, "events");
        this.scheduler = new MlfqScheduler(config.quantumsMillis().size());
        this.coreExecutor = Executors.newFixedThreadPool(config.coreCount(), namedFactory("cpu-core-"));
        this.maintenanceExecutor = Executors.newSingleThreadScheduledExecutor(namedFactory("scheduler-maintenance-"));
        this.disk = device("disk", config.diskDeviceCount());
        this.network = device("network", config.networkDeviceCount());
        this.memory = new PagedMemoryManager(config.memory(), clock, this::completePageFault);
    }

    public void start() {
        if (!started.compareAndSet(false, true)) {
            return;
        }
        disk.start();
        network.start();
        for (int coreId = 0; coreId < config.coreCount(); coreId++) {
            coreExecutor.execute(new CpuCore(coreId, this));
        }
        maintenanceExecutor.scheduleAtFixedRate(this::promoteAgedProcesses,
                config.agingScanPeriodMillis(), config.agingScanPeriodMillis(), TimeUnit.MILLISECONDS);
        emit("ENGINE_STARTED", 0, config.coreCount() + " CPU cores online");
    }

    public ProcessControlBlock submit(ProcessSpec spec) {
        if (!started.get()) {
            throw new IllegalStateException("Start the engine before submitting work");
        }
        if (!accepting.get()) {
            throw new IllegalStateException("Engine is no longer accepting work");
        }
        ProcessControlBlock process = new ProcessControlBlock(spec, clock.nowMillis());
        if (processes.putIfAbsent(spec.pid(), process) != null) {
            throw new IllegalArgumentException("PID " + spec.pid() + " already exists");
        }
        activeProcesses.incrementAndGet();
        process.admit(clock.nowMillis());
        scheduler.enqueue(process);
        metrics.submitted();
        emit("ADMITTED", spec.pid(), "queue=0");
        return process;
    }

    /** Called by CpuCore; exposed for that package only, not as client API. */
    public ProcessControlBlock takeNextProcess() throws InterruptedException {
        return scheduler.take();
    }

    /** Called by a CPU worker after it has atomically removed work from the MLFQ. */
    public void executeSlice(int coreId, ProcessControlBlock process) throws InterruptedException {
        long dispatchAt = clock.nowMillis();
        long waiting = process.dispatch(coreId, dispatchAt);
        metrics.dispatched();
        int currentQueue = process.queueLevel();
        long quantum = config.quantumsMillis().get(currentQueue);
        emit("DISPATCHED", process.spec().pid(), "core=" + coreId + ", queue=" + currentQueue + ", waited=" + waiting + "ms");

        int page = process.nextMemoryPage();
        if (!memory.access(process.spec().pid(), page)) {
            int missingPage = process.blockForPageFault();
            metrics.pageFault();
            emit("PAGE_FAULT", process.spec().pid(), "page=" + missingPage);
            memory.requestPage(process, missingPage);
            return;
        }

        long sliceMillis = process.plannedCpuSlice(quantum);
        clock.sleepMillis(sliceMillis);
        ProcessControlBlock.SliceResult result = process.consumeCpu(sliceMillis, clock.nowMillis());
        metrics.consumedCpu(result.consumedMillis());
        if (result.terminated()) {
            metrics.completed(process);
            memory.releaseProcess(process.spec().pid());
            activeProcesses.decrementAndGet();
            emit("TERMINATED", process.spec().pid(), "core=" + coreId);
            signalCompletion();
            return;
        }
        if (result.ioBurst() != null) {
            metrics.ioRequested();
            emit("IO_BLOCKED", process.spec().pid(), result.ioBurst().type().name());
            controllerFor(result.ioBurst().type()).submit(process, result.ioBurst());
            return;
        }
        int demotedQueue = Math.min(currentQueue + 1, scheduler.levels() - 1);
        process.requeue(demotedQueue, clock.nowMillis());
        scheduler.enqueue(process);
        emit("QUANTUM_EXPIRED", process.spec().pid(), "queue=" + demotedQueue);
    }

    public SchedulerMetrics metrics() {
        return metrics.snapshot(scheduler.queueDepths());
    }

    public Collection<ProcessSnapshot> processSnapshots() {
        return processes.values().stream().map(ProcessControlBlock::snapshot).toList();
    }

    public boolean awaitCompletion(Duration timeout) throws InterruptedException {
        long deadlineNanos = System.nanoTime() + timeout.toNanos();
        synchronized (completionMonitor) {
            while (activeProcesses.get() > 0) {
                long remainingNanos = deadlineNanos - System.nanoTime();
                if (remainingNanos <= 0) {
                    return false;
                }
                TimeUnit.NANOSECONDS.timedWait(completionMonitor, remainingNanos);
            }
            return true;
        }
    }

    public void stopAcceptingWork() {
        accepting.set(false);
    }

    public void shutdownGracefully(Duration timeout) throws InterruptedException {
        stopAcceptingWork();
        if (!awaitCompletion(timeout)) {
            throw new IllegalStateException("Simulation did not become idle before timeout");
        }
        close();
    }

    private DeviceController device(String name, int count) {
        ExecutorService workers = Executors.newFixedThreadPool(count, namedFactory(name + "-device-"));
        ExecutorService dispatcher = Executors.newSingleThreadExecutor(namedFactory(name + "-dispatcher-"));
        return new DeviceController(name, count, clock, workers, dispatcher, this::completeIo);
    }

    private void completeIo(ProcessControlBlock process, Burst burst) {
        process.completeIo(config.ioPriorityBoost() ? 0 : process.queueLevel(), clock.nowMillis());
        scheduler.enqueue(process);
        emit("IO_COMPLETED", process.spec().pid(), burst.type().name());
    }

    private void completePageFault(ProcessControlBlock process, Integer page) {
        process.completePageFault(0, clock.nowMillis());
        scheduler.enqueue(process);
        emit("PAGE_LOADED", process.spec().pid(), "page=" + page);
    }

    private DeviceController controllerFor(BurstType type) {
        return switch (type) {
            case DISK_IO -> disk;
            case NETWORK_IO -> network;
            case CPU -> throw new IllegalArgumentException("CPU work has no device controller");
        };
    }

    private void promoteAgedProcesses() {
        int count = scheduler.promoteAged(clock.nowMillis(), config.agingThresholdMillis());
        if (count > 0) {
            emit("AGING_BOOST", 0, count + " processes promoted");
        }
    }

    private void signalCompletion() {
        synchronized (completionMonitor) {
            completionMonitor.notifyAll();
        }
    }

    private void emit(String type, long pid, String detail) {
        events.publish(new SimulationEvent(clock.nowMillis(), type, pid, detail));
    }

    @Override
    public void close() {
        accepting.set(false);
        scheduler.close();
        maintenanceExecutor.shutdownNow();
        coreExecutor.shutdownNow();
        disk.close();
        network.close();
        memory.close();
        emit("ENGINE_STOPPED", 0, "shutdown complete");
    }

    private static ThreadFactory namedFactory(String prefix) {
        AtomicInteger sequence = new AtomicInteger();
        return runnable -> {
            Thread thread = new Thread(runnable, prefix + sequence.incrementAndGet());
            thread.setUncaughtExceptionHandler((ignored, error) -> error.printStackTrace(System.err));
            return thread;
        };
    }
}
