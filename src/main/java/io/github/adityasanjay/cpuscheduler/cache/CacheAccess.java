package io.github.adityasanjay.cpuscheduler.cache;

public record CacheAccess(boolean hit, long latencyMillis) {
}
