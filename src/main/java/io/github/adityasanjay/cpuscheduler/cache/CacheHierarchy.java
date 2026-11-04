package io.github.adityasanjay.cpuscheduler.cache;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * First hierarchy increment: a private, LRU-managed L1 cache for every core.
 * The cache stores page-reference metadata, not actual application bytes.
 */
public final class CacheHierarchy {
    private final List<L1Cache> l1Caches;

    public CacheHierarchy(int coreCount, CacheConfig config) {
        l1Caches = new ArrayList<>(coreCount);
        for (int coreId = 0; coreId < coreCount; coreId++) {
            l1Caches.add(new L1Cache(config));
        }
    }

    public CacheAccess access(int coreId, long pid, int pageNumber) {
        return l1Caches.get(coreId).access(new CacheLineKey(pid, pageNumber));
    }

    public List<CacheSnapshot> snapshots() {
        List<CacheSnapshot> snapshots = new ArrayList<>(l1Caches.size());
        for (int coreId = 0; coreId < l1Caches.size(); coreId++) {
            snapshots.add(l1Caches.get(coreId).snapshot(coreId));
        }
        return List.copyOf(snapshots);
    }

    private record CacheLineKey(long pid, int pageNumber) { }

    private static final class L1Cache {
        private final CacheConfig config;
        private final LinkedHashMap<CacheLineKey, Boolean> lines;
        private long hits;
        private long misses;
        private long evictions;

        private L1Cache(CacheConfig config) {
            this.config = config;
            this.lines = new LinkedHashMap<>(config.l1LinesPerCore(), 0.75f, true);
        }

        private synchronized CacheAccess access(CacheLineKey key) {
            if (lines.containsKey(key)) {
                lines.get(key); // updates LRU access order
                hits++;
                return new CacheAccess(true, config.l1HitLatencyMillis());
            }
            misses++;
            if (lines.size() == config.l1LinesPerCore()) {
                CacheLineKey leastRecentlyUsed = lines.keySet().iterator().next();
                lines.remove(leastRecentlyUsed);
                evictions++;
            }
            lines.put(key, Boolean.TRUE);
            return new CacheAccess(false, config.l1MissLatencyMillis());
        }

        private synchronized CacheSnapshot snapshot(int coreId) {
            return new CacheSnapshot(coreId, lines.size(), hits, misses, evictions);
        }
    }

    public record CacheSnapshot(int coreId, int residentLines, long hits, long misses, long evictions) { }
}
