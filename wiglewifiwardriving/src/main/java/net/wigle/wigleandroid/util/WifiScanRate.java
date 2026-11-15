package net.wigle.wigleandroid.util;

import android.os.SystemClock;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Process-lifetime ring of per-minute WiFi {@code startScan} counts.
 * Scan threads only increment the open minute. The main thread closes minutes and reads the ring.
 */
public final class WifiScanRate {
    public static final int CAPACITY = 24 * 60;
    static final long MINUTE_MS = 60_000L;
    /**
     * {@code RunningAppProcessInfo.IMPORTANCE_FOREGROUND_SERVICE}. The wifi service treats a caller
     * whose importance is numerically above this as a background app (one scan per 30 minutes).
     */
    public static final int IMPORTANCE_FOREGROUND_SERVICE = 125;
    /** Importance not read, e.g. the platform call threw. */
    public static final int IMPORTANCE_UNKNOWN = -1;

    private final WifiScanMinute[] closed = new WifiScanMinute[CAPACITY];
    private int write;
    private int count;
    private final long processStartElapsedMs;
    private volatile OpenMinute open;

    public WifiScanRate() {
        this(SystemClock.elapsedRealtime(), System.currentTimeMillis());
    }

    /** Visible for tests. Times are injected so a minute boundary can be simulated. */
    WifiScanRate(final long startElapsedMs, final long startWallMs) {
        this.processStartElapsedMs = startElapsedMs;
        this.open = new OpenMinute(startElapsedMs, startWallMs);
    }

    public long getProcessStartElapsedMs() {
        return processStartElapsedMs;
    }

    public boolean isFull() {
        return count == CAPACITY;
    }

    int size() {
        return count;
    }

    /** Binder thread, before {@code WifiManager.startScan()}. */
    public void noteCall() {
        open.call.incrementAndGet();
    }

    /** Binder thread, when {@code startScan()} returns. {@code ok} false counts as a rejection. */
    public void noteReturn(final boolean ok) {
        noteReturn(ok, IMPORTANCE_UNKNOWN);
    }

    /**
     * Binder thread, when {@code startScan()} returns. {@code importance} is this process's
     * {@code RunningAppProcessInfo.importance} read right after the call, or
     * {@link #IMPORTANCE_UNKNOWN}. A rejection while ranked below foreground service counts as a
     * background rejection, and the highest importance seen at a rejection is kept.
     */
    public void noteReturn(final boolean ok, final int importance) {
        final OpenMinute current = open;
        if (ok) {
            current.ok.incrementAndGet();
            return;
        }
        current.reject.incrementAndGet();
        if (importance > IMPORTANCE_FOREGROUND_SERVICE) {
            current.rejectBg.incrementAndGet();
        }
        current.rejectImportanceMax.accumulateAndGet(importance, Math::max);
    }

    /** Binder thread, when {@code startScan()} throws. */
    public void noteThrow() {
        open.threw.incrementAndGet();
    }

    /**
     * Main thread, from the scan-results broadcast.
     * {@code latencyMs} is negative when no request was outstanding.
     */
    public void noteResult(final boolean updated, final int latencyMs) {
        final OpenMinute current = open;
        if (updated) {
            current.result.incrementAndGet();
        } else {
            current.stale.incrementAndGet();
        }
        if (latencyMs >= 0) {
            current.latencySumMs.addAndGet(latencyMs);
            current.latencyCount.incrementAndGet();
        }
    }

    /**
     * Main thread, when a timer tick wanted a scan and did not call {@code startScan()}.
     * {@code inFlight} true: an earlier request had not returned results yet. False: the request
     * was held by the throttle floor or the post-rejection backoff.
     */
    public void noteSuppressed(final boolean inFlight) {
        final OpenMinute current = open;
        current.suppressed.incrementAndGet();
        if (inFlight) {
            current.suppressedInFlight.incrementAndGet();
        }
    }

    /**
     * Binder thread, when an idle-probe {@code startScan()} returns. Probes are the one call per
     * probe interval made while the device is in deep idle, kept apart from the regular counts.
     * A throw counts as a refused probe.
     */
    public void noteProbe(final boolean ok) {
        final OpenMinute current = open;
        current.probe.incrementAndGet();
        if (ok) {
            current.probeOk.incrementAndGet();
        }
    }

    /** Main thread. Repeated marks in the same minute are kept once. */
    public void noteLife(final String mark) {
        if (mark == null || mark.isEmpty()) {
            return;
        }
        open.addMark(mark);
    }

    /**
     * Main thread, from the WiFi timer. Closes the open minute once a minute of elapsed
     * time has passed. A stall of more than one minute is a single record whose {@code gapMin}
     * is the number of minutes spanned.
     */
    public void rollIfDue(final long nowElapsedMs, final long nowWallMs, final Sample sample) {
        final long elapsed = nowElapsedMs - open.elapsedStartMs;
        if (elapsed < MINUTE_MS) {
            return;
        }
        final int spanned = (int) (elapsed / MINUTE_MS);
        final int gapMin = spanned <= 1 ? 0 : spanned;
        append(open.freeze(gapMin, false, sample));
        open = new OpenMinute(nowElapsedMs, nowWallMs);
    }

    /**
     * Main thread. Writes the open minute if it has anything in it, then starts a fresh one.
     * Used when the scan timer is going away so the last incomplete minute is kept.
     */
    public void sealPartial(final long nowElapsedMs, final long nowWallMs, final Sample sample) {
        final OpenMinute closing = open;
        if (closing.isEmpty()) {
            open = new OpenMinute(nowElapsedMs, nowWallMs);
            return;
        }
        final long elapsed = nowElapsedMs - closing.elapsedStartMs;
        final int spanned = elapsed < MINUTE_MS ? 0 : (int) (elapsed / MINUTE_MS);
        final int gapMin = spanned <= 1 ? 0 : spanned;
        append(closing.freeze(gapMin, true, sample));
        open = new OpenMinute(nowElapsedMs, nowWallMs);
    }

    /**
     * Main thread. Closed minutes in order, then the still-open minute marked partial.
     * Does not remove the open minute.
     */
    public List<WifiScanMinute> snapshot(final Sample sample) {
        final List<WifiScanMinute> out = new ArrayList<>(count + 1);
        final int start = count == CAPACITY ? write : 0;
        for (int i = 0; i < count; i++) {
            out.add(closed[(start + i) % CAPACITY]);
        }
        out.add(open.freeze(0, true, sample));
        return out;
    }

    /**
     * Main thread. Up to {@code max - 1} newest closed minutes in order, then the open minute
     * marked partial. {@code max} below one is treated as one.
     */
    public List<WifiScanMinute> recent(final int max, final Sample sample) {
        final int closedWanted = Math.max(0, Math.min(count, max - 1));
        final List<WifiScanMinute> out = new ArrayList<>(closedWanted + 1);
        for (int i = closedWanted; i > 0; i--) {
            out.add(closed[((write - i) % CAPACITY + CAPACITY) % CAPACITY]);
        }
        out.add(open.freeze(0, true, sample));
        return out;
    }

    public static String formatUptime(final long elapsedMs) {
        if (elapsedMs < 0) {
            return "0s";
        }
        final long totalMin = elapsedMs / MINUTE_MS;
        final long hours = totalMin / 60;
        final long minutes = totalMin % 60;
        if (hours > 0) {
            return hours + "h" + minutes + "m";
        }
        if (minutes > 0) {
            return minutes + "m";
        }
        return (elapsedMs / 1000L) + "s";
    }

    private void append(final WifiScanMinute minute) {
        closed[write] = minute;
        write = (write + 1) % CAPACITY;
        if (count < CAPACITY) {
            count++;
        }
    }

    /** Lifecycle and radio state copied onto a minute when it is closed or drawn. */
    public static final class Sample {
        public final boolean scanning;
        public final boolean foreground;
        public final boolean screenOn;
        public final boolean idle;
        public final boolean batteryUnrestricted;
        public final boolean throttle;
        public final boolean scanInFlight;
        public final long periodMs;
        /** WigleService is running with a foreground service type. False before API 29. */
        public final boolean serviceForeground;

        public Sample(final boolean scanning, final boolean foreground, final boolean screenOn,
                      final boolean idle, final boolean batteryUnrestricted, final boolean throttle,
                      final boolean scanInFlight, final long periodMs,
                      final boolean serviceForeground) {
            this.serviceForeground = serviceForeground;
            this.scanning = scanning;
            this.foreground = foreground;
            this.screenOn = screenOn;
            this.idle = idle;
            this.batteryUnrestricted = batteryUnrestricted;
            this.throttle = throttle;
            this.scanInFlight = scanInFlight;
            this.periodMs = periodMs;
        }
    }

    private static final class OpenMinute {
        final long elapsedStartMs;
        final long wallStartMs;
        final AtomicInteger call = new AtomicInteger();
        final AtomicInteger ok = new AtomicInteger();
        final AtomicInteger reject = new AtomicInteger();
        final AtomicInteger rejectBg = new AtomicInteger();
        final AtomicInteger rejectImportanceMax = new AtomicInteger(IMPORTANCE_UNKNOWN);
        final AtomicInteger threw = new AtomicInteger();
        final AtomicInteger result = new AtomicInteger();
        final AtomicInteger stale = new AtomicInteger();
        final AtomicInteger suppressed = new AtomicInteger();
        final AtomicInteger suppressedInFlight = new AtomicInteger();
        final AtomicInteger probe = new AtomicInteger();
        final AtomicInteger probeOk = new AtomicInteger();
        final AtomicInteger latencySumMs = new AtomicInteger();
        final AtomicInteger latencyCount = new AtomicInteger();
        private final StringBuilder marks = new StringBuilder();

        OpenMinute(final long elapsedStartMs, final long wallStartMs) {
            this.elapsedStartMs = elapsedStartMs;
            this.wallStartMs = wallStartMs;
        }

        void addMark(final String mark) {
            synchronized (marks) {
                final String existing = marks.toString();
                if (containsToken(existing, mark)) {
                    return;
                }
                if (marks.length() > 0) {
                    marks.append(' ');
                }
                marks.append(mark);
            }
        }

        boolean isEmpty() {
            synchronized (marks) {
                if (marks.length() > 0) {
                    return false;
                }
            }
            return call.get() == 0 && ok.get() == 0 && reject.get() == 0 && threw.get() == 0
                    && result.get() == 0 && stale.get() == 0 && suppressed.get() == 0
                    && latencyCount.get() == 0 && probe.get() == 0;
        }

        WifiScanMinute freeze(final int gapMin, final boolean partial, final Sample sample) {
            final String markText;
            synchronized (marks) {
                markText = marks.toString();
            }
            return new WifiScanMinute(
                    wallStartMs,
                    call.get(), ok.get(), reject.get(), rejectBg.get(),
                    rejectImportanceMax.get(), threw.get(),
                    result.get(), stale.get(), suppressed.get(), suppressedInFlight.get(),
                    probe.get(), probeOk.get(),
                    latencySumMs.get(), latencyCount.get(),
                    gapMin, partial,
                    sample.scanning, sample.foreground, sample.screenOn, sample.idle,
                    sample.batteryUnrestricted, sample.throttle, sample.scanInFlight,
                    sample.periodMs, sample.serviceForeground, markText);
        }

        private static boolean containsToken(final String existing, final String mark) {
            if (existing.isEmpty()) {
                return false;
            }
            return (" " + existing + " ").contains(" " + mark + " ");
        }
    }
}
