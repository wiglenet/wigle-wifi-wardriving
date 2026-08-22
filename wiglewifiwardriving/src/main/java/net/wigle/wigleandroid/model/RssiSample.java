package net.wigle.wigleandroid.model;

/**
 * Immutable timestamped RSSI observation for the in-memory history cache.
 */
public final class RssiSample {
    public final long timeMs;
    public final int level;

    public RssiSample(final long timeMs, final int level) {
        this.timeMs = timeMs;
        this.level = level;
    }
}
