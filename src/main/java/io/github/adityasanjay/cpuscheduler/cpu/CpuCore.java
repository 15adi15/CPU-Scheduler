package io.github.adityasanjay.cpuscheduler.cpu;

import io.github.adityasanjay.cpuscheduler.domain.ProcessControlBlock;
import io.github.adityasanjay.cpuscheduler.runtime.SimulationEngine;

/** A core only dispatches and runs a quantum; the engine owns state transitions. */
public final class CpuCore implements Runnable {
    private final int coreId;
    private final SimulationEngine engine;

    public CpuCore(int coreId, SimulationEngine engine) {
        this.coreId = coreId;
        this.engine = engine;
    }

    @Override
    public void run() {
        try {
            while (!Thread.currentThread().isInterrupted()) {
                ProcessControlBlock process = engine.takeNextProcess();
                if (process == null) {
                    return;
                }
                engine.executeSlice(coreId, process);
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }
}
