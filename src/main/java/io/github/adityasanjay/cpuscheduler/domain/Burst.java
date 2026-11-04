package io.github.adityasanjay.cpuscheduler.domain;

/** One uninterrupted piece of CPU work or device work in a process program. */
public record Burst(BurstType type, long durationMillis) {
    public Burst {
        if (type == null || durationMillis <= 0) {
            throw new IllegalArgumentException("A burst needs a type and a positive duration");
        }
    }

    public static Burst cpu(long millis) {
        return new Burst(BurstType.CPU, millis);
    }

    public static Burst diskIo(long millis) {
        return new Burst(BurstType.DISK_IO, millis);
    }

    public static Burst networkIo(long millis) {
        return new Burst(BurstType.NETWORK_IO, millis);
    }
}
