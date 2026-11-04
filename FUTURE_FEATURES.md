# CPU Scheduler Simulation: Future Features Roadmap

## Implemented foundation (2026 refresh)

The original single-file, polling simulation has been replaced by a modular
engine with explicit process lifecycle transitions, blocking MLFQ dispatch,
graceful shutdown, asynchronous disk/network I/O, demand paging with LRU frame
replacement, events, and metrics. See `README.md` for the current architecture
and how to run the integration check.

## Next: cache hierarchy and multicore locality

Cache simulation should be introduced before page-file serialization because it
directly validates CPU affinity and work-stealing policies. Model private L1,
private/cluster L2, and shared L3 cache *metadata* (tags, state, replacement),
not raw host memory. Start with read-only LRU/Clock behavior and hit latency;
then add MSI/MESI invalidation and write-back behavior. Cache outcomes should
feed metrics and optional affinity scoring, never silently change correctness.

This document outlines the roadmap for taking this CPU scheduler simulation from a high-level logical model to a low-level, hyper-realistic Operating System kernel simulation. 

## 1. True Hardware Swap File & Page Faults
**Concept:** Instead of using a `LinkedList` as a temporary holding area for evicted processes, simulate actual Hard Drive I/O.
**How to build it in Java:**
*   **Object Serialization:** Use Java's `java.io.ObjectOutputStream` and `FileOutputStream` to convert the `PCB` object into raw bytes and write them to a physical `swap.sys` file on your actual computer's hard drive.
*   **Page Faults:** When the CPU picks a process from the MLFQ, it must check if it is in RAM. If it's on disk, the CPU triggers a "Page Fault". 
*   **I/O Interrupts:** The CPU drops the process into a `WAITING_FOR_IO` queue and executes a different process. A dedicated `DiskController` thread reads the file, parses the bytes back into a `PCB` using `ObjectInputStream`, loads it into RAM, and interrupts the CPU to say the data is ready.

## 2. Paging and Page Tables (Real RAM Management)
**Concept:** Stop allocating memory as a single block (e.g., `availablememory -= 50`). Implement true Memory Paging.
**How to build it in Java:**
*   Create a physical representation of RAM: `PCB[] physicalRAM = new PCB[256]`.
*   Break process memory into 4KB "Pages". If a process needs 20 memory, it needs 5 Pages.
*   Create a **Page Table** for every process. The Page Table maps the process's logical pages to the random, non-contiguous physical frames in the `physicalRAM` array.
*   Deal with **Internal Fragmentation** (when a process needs 10KB, but you have to give it three 4KB pages, wasting 2KB).

## 3. Advanced I/O Burst Simulation
**Concept:** Real processes don't just use the CPU; they pause to read from the network, wait for a user to press a key, or read from a disk.
**How to build it in Java:**
*   Add a `generateIOBurst()` method to the `JobQueue`.
*   When the CPU is executing a process, it might hit an I/O request. The CPU immediately halts execution (even if the time quantum isn't over) and throws the process into a `DeviceQueue`.
*   A `DeviceManager` thread simulates the hardware (e.g., waiting 3 seconds for a network response) and then pushes the process back into the MLFQ.

## 4. Mutexes, Locks, and Priority Inversion
**Concept:** Simulate processes fighting over shared resources (like a printer or a shared file).
**How to build it in Java:**
*   Create simulated `Resource` objects that processes can lock.
*   If Process A (Low Priority) locks the Printer, and Process B (High Priority) wants the Printer, Process B must be blocked. 
*   Implement the **Priority Inheritance Protocol**: Process A temporarily steals Process B's high priority so it can finish using the printer faster, preventing starvation!

## 5. Multi-Core Cache Affinity
**Concept:** In modern CPUs, it is faster for a process to run on the exact same core it ran on previously, because its data is still in that core's L1 Cache.
**How to build it in Java:**
*   Add `lastExecutedCoreId` to the PCB.
*   When a CPU Core looks for a job in the MLFQ, it checks if any jobs in the queue have a matching `lastExecutedCoreId`. It gives those jobs a slight priority boost to simulate cache hits!
