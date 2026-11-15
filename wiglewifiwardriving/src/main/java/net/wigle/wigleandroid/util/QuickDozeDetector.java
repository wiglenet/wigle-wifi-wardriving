package net.wigle.wigleandroid.util;

/**
 * Spots quick doze from its symptom: deep Doze arriving within minutes of screen-off while
 * scanning. Stock Doze needs at least {@code inactive_to} (30 minutes) of screen-off before deep
 * idle, so anything well under that means the platform skipped the motion and location checks.
 * On Pixel 9 and later that is adaptive Battery Saver, switched on by the Adaptive Battery
 * setting. In deep Doze the wifi service refuses every {@code startScan}, so it ends wardriving.
 *
 * Apps cannot read the adaptive Battery Saver state, so this watches screen and idle edges.
 * Main thread only. Times are {@code SystemClock.elapsedRealtime()}.
 */
public final class QuickDozeDetector {
    /** Deep idle sooner than this after screen-off counts as quick doze. */
    public static final long QUICK_LIMIT_MS = 10 * 60_000L;

    public enum Verdict {
        /** Nothing learned from this edge. */
        NONE,
        /** Deep Doze came within {@link #QUICK_LIMIT_MS} of screen-off while scanning. */
        QUICK_DOZE,
        /** A screen-off at least {@link #QUICK_LIMIT_MS} long, scanning on battery, with no deep Doze. */
        NORMAL
    }

    private long screenOffAt = Long.MIN_VALUE;
    /** Scanning and on battery when the screen went off, so this off period can clear a warning. */
    private boolean offPeriodCounts;
    private boolean idleSeen;

    public void onScreenOff(final long nowElapsedMs, final boolean scanning, final boolean onBattery) {
        screenOffAt = nowElapsedMs;
        offPeriodCounts = scanning && onBattery;
        idleSeen = false;
    }

    /** Deep idle started. At most one {@link Verdict#QUICK_DOZE} per screen-off. */
    public Verdict onIdleOn(final long nowElapsedMs, final boolean scanning) {
        if (screenOffAt == Long.MIN_VALUE || idleSeen) {
            return Verdict.NONE;
        }
        idleSeen = true;
        if (scanning && nowElapsedMs - screenOffAt <= QUICK_LIMIT_MS) {
            return Verdict.QUICK_DOZE;
        }
        return Verdict.NONE;
    }

    /** Screen came back on. {@code onBattery} false means charging could have held Doze off. */
    public Verdict onScreenOn(final long nowElapsedMs, final boolean scanning, final boolean onBattery) {
        if (screenOffAt == Long.MIN_VALUE) {
            return Verdict.NONE;
        }
        final boolean counts = offPeriodCounts && scanning && onBattery && !idleSeen
                && nowElapsedMs - screenOffAt >= QUICK_LIMIT_MS;
        screenOffAt = Long.MIN_VALUE;
        offPeriodCounts = false;
        idleSeen = false;
        return counts ? Verdict.NORMAL : Verdict.NONE;
    }
}
