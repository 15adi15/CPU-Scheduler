package io.github.adityasanjay.cpuscheduler.domain;

/** A process may belong to exactly one lifecycle state at a time. */
public enum ProcessState {
    NEW, ADMITTED, READY, RUNNING, BLOCKED_IO, BLOCKED_PAGE_FAULT, TERMINATED, FAILED
}
