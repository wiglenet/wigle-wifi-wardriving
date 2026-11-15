package net.wigle.wigleandroid.util;

import java.util.Date;
import java.util.Locale;
import java.text.DateFormat;

/**
 * One closed or in-progress minute of WiFi scan counters.
 * Counts are the scan events that landed in the minute. Lifecycle flags are the sample taken
 * when the minute was closed, or the live sample for the open minute.
 */
public final class WifiScanMinute {
    final long wallStartMs;
    final int call;
    final int ok;
    final int reject;
    /** Rejections while this process ranked below foreground service. */
    final int rejectBg;
    /** Highest process importance seen at a rejection, or -1 when none was read. */
    final int rejectImportanceMax;
    final int threw;
    final int result;
    final int stale;
    /** Ticks that skipped {@code startScan()}: {@link #suppressedInFlight} plus held ticks. */
    final int suppressed;
    /** Skipped because a request was still waiting on its results broadcast. */
    final int suppressedInFlight;
    /** startScan calls made as deep-idle probes, and how many of them the platform accepted. */
    final int probe;
    final int probeOk;
    final int latencySumMs;
    final int latencyCount;
    /** Minutes spanned when the timer did not close this bucket on the following minute. Zero when it did. */
    final int gapMin;
    final boolean partial;
    final boolean scanning;
    final boolean foreground;
    final boolean screenOn;
    final boolean idle;
    final boolean batteryUnrestricted;
    final boolean throttle;
    final boolean scanInFlight;
    final long periodMs;
    final boolean serviceForeground;
    final String marks;

    WifiScanMinute(final long wallStartMs,
                   final int call, final int ok, final int reject, final int rejectBg,
                   final int rejectImportanceMax, final int threw,
                   final int result, final int stale, final int suppressed,
                   final int suppressedInFlight, final int probe, final int probeOk,
                   final int latencySumMs, final int latencyCount,
                   final int gapMin, final boolean partial,
                   final boolean scanning, final boolean foreground, final boolean screenOn,
                   final boolean idle, final boolean batteryUnrestricted, final boolean throttle,
                   final boolean scanInFlight, final long periodMs,
                   final boolean serviceForeground, final String marks) {
        this.wallStartMs = wallStartMs;
        this.call = call;
        this.ok = ok;
        this.reject = reject;
        this.rejectBg = rejectBg;
        this.rejectImportanceMax = rejectImportanceMax;
        this.threw = threw;
        this.result = result;
        this.stale = stale;
        this.suppressed = suppressed;
        this.suppressedInFlight = suppressedInFlight;
        this.probe = probe;
        this.probeOk = probeOk;
        this.latencySumMs = latencySumMs;
        this.latencyCount = latencyCount;
        this.gapMin = gapMin;
        this.partial = partial;
        this.scanning = scanning;
        this.foreground = foreground;
        this.screenOn = screenOn;
        this.idle = idle;
        this.batteryUnrestricted = batteryUnrestricted;
        this.throttle = throttle;
        this.scanInFlight = scanInFlight;
        this.periodMs = periodMs;
        this.serviceForeground = serviceForeground;
        this.marks = marks == null ? "" : marks;
    }

    /** True when this minute recorded no scan activity and no lifecycle edge. */
    public boolean isBlank() {
        return call == 0 && ok == 0 && reject == 0 && threw == 0
                && result == 0 && stale == 0 && suppressed == 0 && probe == 0
                && gapMin == 0 && marks.isEmpty();
    }

    public String formatLine(final DateFormat timeFormat) {
        final int lat = latencyCount == 0 ? 0 : latencySumMs / latencyCount;
        final StringBuilder sb = new StringBuilder(180);
        sb.append(timeFormat.format(new Date(wallStartMs)));
        sb.append(String.format(Locale.US,
                "  call %3d  ok %3d  rej %3d  rejbg %3d  imp %4d  throw %3d  result %3d  stale %3d  skip %3d  skfly %3d  skhold %3d  probe %3d  probeok %3d  lat %6d"
                        + "  scan %-3s  fg %d  svc %d  scr %-3s  idle %d  batt %d  thr %d  per %6d  fly %d  gap %dm",
                call, ok, reject, rejectBg, rejectImportanceMax, threw, result, stale,
                suppressed, suppressedInFlight, suppressed - suppressedInFlight, probe, probeOk, lat,
                scanning ? "on" : "off",
                foreground ? 1 : 0,
                serviceForeground ? 1 : 0,
                screenOn ? "on" : "off",
                idle ? 1 : 0,
                batteryUnrestricted ? 1 : 0,
                throttle ? 1 : 0,
                periodMs,
                scanInFlight ? 1 : 0,
                gapMin));
        if (partial) {
            sb.append("  partial");
        }
        if (!marks.isEmpty()) {
            sb.append("  ").append(marks);
        }
        return sb.toString();
    }

    /**
     * Narrow form for the live overlay: counts, mean latency, period in seconds, and one letter per
     * lifecycle flag (S scan, F foreground, G service foreground, O screen on, I idle,
     * B battery unrestricted, T throttle, Y scan in flight; '-' when false).
     * Rejections print as {@code rj<total>/<background>}; skipped ticks as
     * {@code sk<in-flight>/<held>}, held being the throttle floor or rejection backoff.
     * Minutes with idle probes append {@code pr<calls>/<accepted>}. Minutes with a rejection append
     * {@code imp<n>}, the highest process importance read at a rejection.
     */
    public String formatCompactLine(final DateFormat timeFormat) {
        final int lat = latencyCount == 0 ? 0 : latencySumMs / latencyCount;
        final StringBuilder sb = new StringBuilder(96);
        sb.append(timeFormat.format(new Date(wallStartMs)));
        sb.append(String.format(Locale.US,
                " c%3d ok%3d rj%2d/%-2d th%2d rs%3d st%2d sk%2d/%-2d lat%5d p%3ds ",
                call, ok, reject, rejectBg, threw, result, stale,
                suppressedInFlight, suppressed - suppressedInFlight, lat, periodMs / 1000L));
        sb.append(scanning ? 'S' : '-');
        sb.append(foreground ? 'F' : '-');
        sb.append(serviceForeground ? 'G' : '-');
        sb.append(screenOn ? 'O' : '-');
        sb.append(idle ? 'I' : '-');
        sb.append(batteryUnrestricted ? 'B' : '-');
        sb.append(throttle ? 'T' : '-');
        sb.append(scanInFlight ? 'Y' : '-');
        if (probe > 0) {
            sb.append(" pr").append(probe).append('/').append(probeOk);
        }
        if (reject > 0 && rejectImportanceMax != WifiScanRate.IMPORTANCE_UNKNOWN) {
            sb.append(" imp").append(rejectImportanceMax);
        }
        if (gapMin > 0) {
            sb.append(" gap").append(gapMin).append('m');
        }
        if (partial) {
            sb.append(" *");
        }
        if (!marks.isEmpty()) {
            sb.append(' ').append(marks);
        }
        return sb.toString();
    }
}
