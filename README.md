# Concurrent CPU Scheduler Simulator

A Java concurrency simulation of a multi-core operating-system scheduler. The
engine is deliberately framework-free; a future Spring Boot module can expose
its snapshots, events, and metrics without becoming part of scheduling logic.

## Implemented

- Validated process lifecycle: `NEW → ADMITTED → READY → RUNNING → BLOCKED_IO → READY → TERMINATED`.
- Immutable workload specifications and thread-safe process control blocks.
- Configurable multi-level feedback queue (MLFQ), blocking dispatch, quantum
  demotion, and periodic aging promotion.
- Fixed set of CPU-core workers, coordinated with `ExecutorService` rather
  than busy polling.
- Bounded asynchronous disk and network device controllers. A CPU core releases
  its process on I/O and immediately dispatches other ready work.
- Demand-paged memory manager with a synchronized frame table, deterministic
  process working-set references, LRU replacement, asynchronous page-in, and
  `BLOCKED_PAGE_FAULT` lifecycle handling.
- Private per-core L1 caches with configurable metadata-line capacity, LRU
  replacement, access latency, events, and cache hit/miss metrics.
- Graceful engine lifecycle, event stream, process snapshots, and scheduler metrics.
- Dependency-free integration verification runnable with the JDK.

## Architecture

```text
Process submitter → MLFQ scheduler → CPU-core workers
                         ↑                 │
                         │                 ├── quantum expiry → MLFQ
                         │                 ├── termination → metrics
                         │                 └── I/O request
                         │                         │
                         └── device completion ← disk / network controllers
```

Only the MLFQ owns ready-queue membership. A PCB owns its lifecycle transition,
device controllers own queued I/O, and the page manager owns frame metadata.
This gives the simulation explicit
ownership boundaries and prevents a process from being validly scheduled by two
components at once.

## Run

The project currently needs only JDK 21+ (it is verified with JDK 25):

```bash
mkdir -p out
javac -d out $(find src/main/java src/test/java -name '*.java')
java -cp out io.github.adityasanjay.cpuscheduler.EngineVerification
java -cp out io.github.adityasanjay.cpuscheduler.Demo
```

## Cache simulation roadmap

Cache simulation will model latency and coherence consequences, rather than
attempting to emulate hardware bytes or machine instructions.

1. **Locality model:** implemented as a private L1 cache per core with
   configurable metadata-line capacity, LRU replacement, and hit/miss latency.
   It uses deterministic page references; a context switch or migration can
   therefore produce a miss on the new core.
2. **Cache hierarchy:** add private or cluster-local L2 caches and a shared L3.
   Every simulated access walks `L1 → L2 → L3 → RAM`; metrics record hits,
   misses, eviction count, and accumulated memory-stall time.
3. **Multicore coherence:** add line ownership and invalidations for writes,
   initially using MSI and then MESI. Track coherence messages, invalidations,
   false-sharing events, and modified-line write-backs.
4. **Scheduler integration:** use cache-affinity scoring only as a policy option,
   never as a correctness requirement. Compare global queues, work stealing,
   and affinity-aware dispatch using cache-hit rate and average turnaround time.

Out of scope: emulating arbitrary native instructions, actual host CPU caches,
or persisting cache data to disk. The goal is an explainable concurrency and
scheduling experiment with measurable trade-offs.
