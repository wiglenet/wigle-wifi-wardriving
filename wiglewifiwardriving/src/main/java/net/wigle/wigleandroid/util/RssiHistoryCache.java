package net.wigle.wigleandroid.util;

import net.wigle.wigleandroid.model.RssiSample;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Dense in-memory RSSI time series keyed by BSSID.
 * Retains at most {@link #WINDOW_MS} of samples and stays under
 * {@link #MEM_FRACTION} of {@link Runtime#maxMemory()}.
 * When disabled, {@link #record} is a no-op and series storage is cleared.
 */
public final class RssiHistoryCache {
    public static final long WINDOW_MS = 120_000L;
    public static final double MEM_FRACTION = 0.05d;
    public static final int MAX_SAMPLES_PER_BSSID = 128;
    public static final long DEDUP_MS = 250L;
    private static final int ABSOLUTE_MAX_BSSIDS = 32_000;
    private static final int BYTES_PER_SAMPLE = 12;
    private static final int SERIES_OVERHEAD = 64;

    private final ConcurrentHashMap<String, Series> byBssid = new ConcurrentHashMap<>();
    private final int maxBssids;
    private final int maxSamples;
    private final long budgetBytes;
    private volatile boolean enabled;

    public RssiHistoryCache(final boolean enabled) {
        this(enabled, Runtime.getRuntime().maxMemory());
    }

    RssiHistoryCache(final boolean enabled, final long maxMemory) {
        this.enabled = enabled;
        this.budgetBytes = Math.max(64L * 1024L, (long) (maxMemory * MEM_FRACTION));
        this.maxSamples = MAX_SAMPLES_PER_BSSID;
        final int estSeriesBytes = SERIES_OVERHEAD + BYTES_PER_SAMPLE * maxSamples;
        this.maxBssids = (int) Math.max(64, Math.min(budgetBytes / estSeriesBytes, ABSOLUTE_MAX_BSSIDS));
        Logging.info("RssiHistoryCache: enabled=" + enabled
                + " budgetBytes=" + budgetBytes
                + " maxBssids=" + maxBssids
                + " maxSamples=" + maxSamples);
    }

    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Enable or disable recording. Disabling clears all stored series.
     */
    public void setEnabled(final boolean enabled) {
        this.enabled = enabled;
        if (!enabled) {
            clear();
        }
        Logging.info("RssiHistoryCache: setEnabled=" + enabled);
    }

    public void clear() {
        byBssid.clear();
    }

    /**
     * Record a sample for {@code bssid}. No-op when disabled.
     * {@code level} is clamped to the signed-byte dBm range used by the ring.
     */
    public void record(final String bssid, final int level) {
        record(bssid, level, System.currentTimeMillis());
    }

    public void record(final String bssid, final int level, final long timeMs) {
        if (!enabled || bssid == null || bssid.isEmpty()) {
            return;
        }
        final String key = bssid.toLowerCase(Locale.ROOT);
        final byte packedLevel = clampLevel(level);
        Series series = byBssid.get(key);
        if (series == null) {
            final Series created = new Series(maxSamples);
            final Series raced = byBssid.putIfAbsent(key, created);
            series = raced != null ? raced : created;
        }
        series.append(timeMs, packedLevel);
        if (byBssid.size() > maxBssids) {
            trimToBudget(timeMs);
        }
    }

    /**
     * Returns a pruned copy of samples for {@code bssid}, never null.
     */
    public List<RssiSample> getSeries(final String bssid) {
        if (!enabled || bssid == null) {
            return Collections.emptyList();
        }
        final Series series = byBssid.get(bssid.toLowerCase(Locale.ROOT));
        if (series == null) {
            return Collections.emptyList();
        }
        return series.copyPruned(System.currentTimeMillis());
    }

    public RssiSample getLatest(final String bssid) {
        if (!enabled || bssid == null) {
            return null;
        }
        final Series series = byBssid.get(bssid.toLowerCase(Locale.ROOT));
        if (series == null) {
            return null;
        }
        return series.latest(System.currentTimeMillis());
    }

    public Stats stats() {
        int samples = 0;
        for (final Series series : byBssid.values()) {
            samples += series.size();
        }
        final long bytesEst = (long) byBssid.size() * SERIES_OVERHEAD
                + (long) samples * BYTES_PER_SAMPLE;
        return new Stats(byBssid.size(), samples, bytesEst, budgetBytes, maxBssids, enabled);
    }

    /**
     * Drop idle series and, if still over capacity, the coldest by last write.
     */
    public void trimToBudget() {
        trimToBudget(System.currentTimeMillis());
    }

    private void trimToBudget(final long nowMs) {
        final long cutoff = nowMs - WINDOW_MS;
        final Iterator<Map.Entry<String, Series>> it = byBssid.entrySet().iterator();
        while (it.hasNext()) {
            final Map.Entry<String, Series> entry = it.next();
            if (entry.getValue().lastWriteMs() < cutoff || entry.getValue().prune(nowMs) == 0) {
                it.remove();
            }
        }
        while (byBssid.size() > maxBssids) {
            String coldestKey = null;
            long coldestWrite = Long.MAX_VALUE;
            for (final Map.Entry<String, Series> entry : byBssid.entrySet()) {
                final long last = entry.getValue().lastWriteMs();
                if (last < coldestWrite) {
                    coldestWrite = last;
                    coldestKey = entry.getKey();
                }
            }
            if (coldestKey == null) {
                break;
            }
            byBssid.remove(coldestKey);
        }
    }

    private static byte clampLevel(final int level) {
        if (level > Byte.MAX_VALUE) {
            return Byte.MAX_VALUE;
        }
        if (level < Byte.MIN_VALUE) {
            return Byte.MIN_VALUE;
        }
        return (byte) level;
    }

    public static final class Stats {
        public final int bssids;
        public final int samples;
        public final long bytesEst;
        public final long budgetBytes;
        public final int maxBssids;
        public final boolean enabled;

        Stats(final int bssids, final int samples, final long bytesEst,
              final long budgetBytes, final int maxBssids, final boolean enabled) {
            this.bssids = bssids;
            this.samples = samples;
            this.bytesEst = bytesEst;
            this.budgetBytes = budgetBytes;
            this.maxBssids = maxBssids;
            this.enabled = enabled;
        }
    }

    /**
     * Primitive ring buffer of (timeMs, level) for one BSSID.
     */
    private static final class Series {
        private final long[] times;
        private final byte[] levels;
        private final int capacity;
        private int head;
        private int size;
        private long lastWriteMs;
        private int lastLevel = Integer.MIN_VALUE;

        Series(final int capacity) {
            this.capacity = capacity;
            this.times = new long[capacity];
            this.levels = new byte[capacity];
        }

        synchronized void append(final long timeMs, final byte level) {
            pruneUnlocked(timeMs);
            if (size > 0 && level == (byte) lastLevel && (timeMs - lastWriteMs) < DEDUP_MS) {
                // refresh timestamp of identical recent sample
                final int idx = indexOfNewest();
                times[idx] = timeMs;
                lastWriteMs = timeMs;
                return;
            }
            if (size == capacity) {
                head = (head + 1) % capacity;
                size--;
            }
            final int idx = (head + size) % capacity;
            times[idx] = timeMs;
            levels[idx] = level;
            size++;
            lastWriteMs = timeMs;
            lastLevel = level;
        }

        synchronized int prune(final long nowMs) {
            return pruneUnlocked(nowMs);
        }

        private int pruneUnlocked(final long nowMs) {
            final long cutoff = nowMs - WINDOW_MS;
            while (size > 0 && times[head] < cutoff) {
                head = (head + 1) % capacity;
                size--;
            }
            if (size == 0) {
                lastLevel = Integer.MIN_VALUE;
            }
            return size;
        }

        synchronized int size() {
            return size;
        }

        synchronized long lastWriteMs() {
            return lastWriteMs;
        }

        synchronized List<RssiSample> copyPruned(final long nowMs) {
            pruneUnlocked(nowMs);
            if (size == 0) {
                return Collections.emptyList();
            }
            final List<RssiSample> out = new ArrayList<>(size);
            for (int i = 0; i < size; i++) {
                final int idx = (head + i) % capacity;
                out.add(new RssiSample(times[idx], levels[idx]));
            }
            return out;
        }

        synchronized RssiSample latest(final long nowMs) {
            pruneUnlocked(nowMs);
            if (size == 0) {
                return null;
            }
            final int idx = indexOfNewest();
            return new RssiSample(times[idx], levels[idx]);
        }

        private int indexOfNewest() {
            return (head + size - 1) % capacity;
        }
    }
}
