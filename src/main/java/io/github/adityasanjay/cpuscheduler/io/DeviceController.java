package io.github.adityasanjay.cpuscheduler.io;

import io.github.adityasanjay.cpuscheduler.domain.Burst;
import io.github.adityasanjay.cpuscheduler.domain.ProcessControlBlock;
import io.github.adityasanjay.cpuscheduler.runtime.SimulationClock;

import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;

/**
 * A device has one dispatcher and a bounded number of physical slots. Device
 * work is deliberately asynchronous: CPU cores never wait for disk/network I/O.
 */
public final class DeviceController implements AutoCloseable {
    private final String name;
    private final SimulationClock clock;
    private final BlockingQueue<IoRequest> requests = new LinkedBlockingQueue<>();
    private final Semaphore availableSlots;
    private final ExecutorService deviceWorkers;
    private final ExecutorService dispatcherExecutor;
    private final BiConsumer<ProcessControlBlock, Burst> completion;
    private final AtomicBoolean running = new AtomicBoolean();

    public DeviceController(String name, int deviceCount, SimulationClock clock,
                            ExecutorService deviceWorkers, ExecutorService dispatcherExecutor,
                            BiConsumer<ProcessControlBlock, Burst> completion) {
        if (deviceCount < 1) {
            throw new IllegalArgumentException("A device controller needs at least one device");
        }
        this.name = Objects.requireNonNull(name, "name");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.availableSlots = new Semaphore(deviceCount, true);
        this.deviceWorkers = Objects.requireNonNull(deviceWorkers, "deviceWorkers");
        this.dispatcherExecutor = Objects.requireNonNull(dispatcherExecutor, "dispatcherExecutor");
        this.completion = Objects.requireNonNull(completion, "completion");
    }

    public void start() {
        if (running.compareAndSet(false, true)) {
            dispatcherExecutor.execute(this::dispatchLoop);
        }
    }

    public void submit(ProcessControlBlock process, Burst burst) {
        if (!running.get()) {
            throw new IllegalStateException(name + " controller is not running");
        }
        requests.add(new IoRequest(process, burst));
    }

    public int pendingRequests() {
        return requests.size();
    }

    private void dispatchLoop() {
        try {
            while (running.get() || !requests.isEmpty()) {
                IoRequest request = requests.take();
                availableSlots.acquire();
                deviceWorkers.execute(() -> perform(request));
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private void perform(IoRequest request) {
        try {
            clock.sleepMillis(request.burst.durationMillis());
            completion.accept(request.process, request.burst);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        } finally {
            availableSlots.release();
        }
    }

    @Override
    public void close() {
        running.set(false);
        dispatcherExecutor.shutdownNow();
        deviceWorkers.shutdownNow();
    }

    private record IoRequest(ProcessControlBlock process, Burst burst) { }
}
