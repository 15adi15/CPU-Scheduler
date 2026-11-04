package io.github.adityasanjay.cpuscheduler.cache;

/** Configuration for the first cache tier: one private L1 per simulated core. */
public record CacheConfig(int l1LinesPerCore, long l1HitLatencyMillis, long l1MissLatencyMillis) {
    public CacheConfig {
        if (l1LinesPerCore < 1 || l1HitLatencyMillis < 0 || l1MissLatencyMillis < l1HitLatencyMillis) {
            throw new IllegalArgumentException("Invalid L1 capacity or latency configuration");
        }
    }

    public static CacheConfig defaults() {
        return new CacheConfig(8, 0, 1);
    }
}
