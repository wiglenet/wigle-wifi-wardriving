package net.wigle.wigleandroid.util;

import org.junit.Assert;
import org.junit.Test;

import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;

public class WifiScanRateTest {
    private static final long WALL = 1_700_000_000_000L;
    private static final WifiScanRate.Sample SAMPLE = new WifiScanRate.Sample(
            true, true, false, false, true, true, false, 2000L, true);

    @Test
    public void rollWaitsForAMinuteThenKeepsCounts() {
        final WifiScanRate rate = new WifiScanRate(0L, WALL);
        rate.noteCall();
        rate.noteReturn(true);
        rate.noteResult(true, 400);
        rate.noteResult(true, 440);
        rate.noteResult(false, -1);
        rate.noteSuppressed(true);
        rate.noteSuppressed(false);
        rate.noteSuppressed(false);

        rate.rollIfDue(59_999L, WALL + 59_999L, SAMPLE);
        Assert.assertEquals(0, rate.size());

        rate.rollIfDue(60_000L, WALL + 60_000L, SAMPLE);
        Assert.assertEquals(1, rate.size());

        final List<WifiScanMinute> minutes = rate.snapshot(SAMPLE);
        Assert.assertEquals(2, minutes.size());
        final WifiScanMinute closed = minutes.get(0);
        Assert.assertEquals(1, closed.call);
        Assert.assertEquals(1, closed.ok);
        Assert.assertEquals(0, closed.reject);
        Assert.assertEquals(2, closed.result);
        Assert.assertEquals(1, closed.stale);
        Assert.assertEquals(3, closed.suppressed);
        Assert.assertEquals(1, closed.suppressedInFlight);
        final String compact = closed.formatCompactLine(new SimpleDateFormat("HH:mm", Locale.US));
        Assert.assertTrue(compact, compact.contains("sk 1/2"));
        final String full = closed.formatLine(new SimpleDateFormat("HH:mm", Locale.US));
        Assert.assertTrue(full, full.contains("skfly   1  skhold   2"));
        Assert.assertEquals(840, closed.latencySumMs);
        Assert.assertEquals(2, closed.latencyCount);
        Assert.assertEquals(0, closed.gapMin);
        Assert.assertFalse(closed.partial);
        Assert.assertEquals(WALL, closed.wallStartMs);

        final WifiScanMinute open = minutes.get(1);
        Assert.assertTrue(open.partial);
        Assert.assertTrue(open.isBlank());
        Assert.assertEquals(WALL + 60_000L, open.wallStartMs);
    }

    @Test
    public void stallIsOneRecordWithGap() {
        final WifiScanRate rate = new WifiScanRate(0L, WALL);
        rate.noteCall();
        rate.noteReturn(false);
        rate.noteThrow();
        rate.rollIfDue(12 * WifiScanRate.MINUTE_MS, WALL + 12 * WifiScanRate.MINUTE_MS, SAMPLE);

        final WifiScanMinute closed = rate.snapshot(SAMPLE).get(0);
        Assert.assertEquals(12, closed.gapMin);
        Assert.assertEquals(1, closed.call);
        Assert.assertEquals(1, closed.reject);
        Assert.assertEquals(1, closed.threw);
        Assert.assertFalse(closed.partial);
    }

    @Test
    public void sealWritesPartialOnce() {
        final WifiScanRate rate = new WifiScanRate(0L, WALL);
        rate.noteLife("screen_off");
        rate.noteLife("screen_off");
        rate.noteLife("scan_off");
        rate.sealPartial(1_000L, WALL + 1_000L, SAMPLE);
        rate.sealPartial(2_000L, WALL + 2_000L, SAMPLE);

        Assert.assertEquals(1, rate.size());
        final WifiScanMinute sealed = rate.snapshot(SAMPLE).get(0);
        Assert.assertTrue(sealed.partial);
        Assert.assertEquals("screen_off scan_off", sealed.marks);
        Assert.assertEquals(0, sealed.gapMin);
    }

    @Test
    public void ringDropsTheOldestMinute() {
        final WifiScanRate rate = new WifiScanRate(0L, WALL);
        final int rolls = WifiScanRate.CAPACITY + 5;
        for (int i = 0; i < rolls; i++) {
            rate.noteCall();
            final long at = (i + 1L) * WifiScanRate.MINUTE_MS;
            rate.rollIfDue(at, WALL + at, SAMPLE);
        }
        Assert.assertEquals(WifiScanRate.CAPACITY, rate.size());
        Assert.assertTrue(rate.isFull());

        final List<WifiScanMinute> minutes = rate.snapshot(SAMPLE);
        Assert.assertEquals(WifiScanRate.CAPACITY + 1, minutes.size());
        Assert.assertEquals(WALL + 5L * WifiScanRate.MINUTE_MS, minutes.get(0).wallStartMs);
        final WifiScanMinute newestClosed = minutes.get(minutes.size() - 2);
        Assert.assertEquals(WALL + (rolls - 1L) * WifiScanRate.MINUTE_MS, newestClosed.wallStartMs);
        Assert.assertTrue(minutes.get(minutes.size() - 1).partial);
    }

    @Test
    public void formatLineUsesMeanLatencyAndMarks() {
        final WifiScanRate rate = new WifiScanRate(0L, WALL);
        rate.noteCall();
        rate.noteReturn(true);
        rate.noteResult(true, 400);
        rate.noteResult(true, 440);
        rate.noteLife("screen_off");
        rate.rollIfDue(60_000L, WALL + 60_000L, SAMPLE);

        final String line = rate.snapshot(SAMPLE).get(0).formatLine(
                new SimpleDateFormat("HH:mm", Locale.US));
        Assert.assertTrue(line.contains("call   1"));
        Assert.assertTrue(line.contains("ok   1"));
        Assert.assertTrue(line.contains("lat    420"));
        Assert.assertTrue(line.contains("scan on"));
        Assert.assertTrue(line.contains("fg 1"));
        Assert.assertTrue(line.contains("scr off"));
        Assert.assertTrue(line.contains("batt 1"));
        Assert.assertTrue(line.contains("thr 1"));
        Assert.assertTrue(line.contains("per   2000"));
        Assert.assertTrue(line.contains("gap 0m"));
        Assert.assertTrue(line.contains("screen_off"));
        Assert.assertFalse(line.contains("partial"));
    }

    @Test
    public void freshSnapshotIsBlank() {
        final WifiScanRate rate = new WifiScanRate(0L, WALL);
        final List<WifiScanMinute> minutes = rate.snapshot(SAMPLE);
        Assert.assertEquals(1, minutes.size());
        Assert.assertTrue(minutes.get(0).isBlank());
        Assert.assertEquals("0s", WifiScanRate.formatUptime(0));
        Assert.assertEquals("45s", WifiScanRate.formatUptime(45_000L));
        Assert.assertEquals("3m", WifiScanRate.formatUptime(3 * WifiScanRate.MINUTE_MS));
        Assert.assertEquals("3h12m", WifiScanRate.formatUptime((3 * 60L + 12) * WifiScanRate.MINUTE_MS));
    }

    @Test
    public void recentReturnsNewestClosedThenOpenAcrossWrap() {
        final WifiScanRate rate = new WifiScanRate(0L, WALL);
        final int rolls = WifiScanRate.CAPACITY + 3;
        for (int i = 1; i <= rolls; i++) {
            rate.noteCall();
            rate.rollIfDue(i * WifiScanRate.MINUTE_MS, WALL + i * WifiScanRate.MINUTE_MS, SAMPLE);
        }
        final List<WifiScanMinute> recent = rate.recent(4, SAMPLE);
        Assert.assertEquals(4, recent.size());
        Assert.assertEquals(WALL + (rolls - 3L) * WifiScanRate.MINUTE_MS, recent.get(0).wallStartMs);
        Assert.assertEquals(WALL + (rolls - 1L) * WifiScanRate.MINUTE_MS, recent.get(2).wallStartMs);
        Assert.assertTrue(recent.get(3).partial);

        final WifiScanRate fresh = new WifiScanRate(0L, WALL);
        Assert.assertEquals(1, fresh.recent(8, SAMPLE).size());
        Assert.assertEquals(1, fresh.recent(0, SAMPLE).size());
    }

    @Test
    public void compactLineShowsFlagLetters() {
        final WifiScanRate rate = new WifiScanRate(0L, WALL);
        rate.noteCall();
        rate.noteReturn(true);
        rate.noteResult(true, 400);
        rate.noteLife("screen_off");
        rate.rollIfDue(60_000L, WALL + 60_000L, SAMPLE);
        final String line = rate.recent(2, SAMPLE).get(0).formatCompactLine(
                new SimpleDateFormat("HH:mm", Locale.US));
        Assert.assertTrue(line, line.contains("c  1 ok  1"));
        Assert.assertTrue(line, line.contains("lat  400"));
        Assert.assertTrue(line, line.contains("p  2s"));
        Assert.assertTrue(line, line.contains("rj 0/0"));
        Assert.assertTrue(line, line.contains("SFG--BT-"));
        Assert.assertTrue(line, line.endsWith("screen_off"));
    }

    @Test
    public void rejectionsSplitByImportance() {
        final WifiScanRate rate = new WifiScanRate(0L, WALL);
        rate.noteReturn(false, 100);
        rate.noteReturn(false, WifiScanRate.IMPORTANCE_FOREGROUND_SERVICE);
        rate.noteReturn(false, 230);
        rate.noteReturn(false, 300);
        rate.noteReturn(false);
        rate.noteReturn(true, 400);
        rate.rollIfDue(60_000L, WALL + 60_000L, SAMPLE);

        final WifiScanMinute minute = rate.snapshot(SAMPLE).get(0);
        Assert.assertEquals(5, minute.reject);
        Assert.assertEquals(2, minute.rejectBg);
        Assert.assertEquals(300, minute.rejectImportanceMax);
        Assert.assertEquals(1, minute.ok);
        final String line = minute.formatCompactLine(new SimpleDateFormat("HH:mm", Locale.US));
        Assert.assertTrue(line, line.contains("rj 5/2"));
        Assert.assertTrue(line, line.contains(" imp300"));
        Assert.assertTrue(minute.formatLine(new SimpleDateFormat("HH:mm", Locale.US)).contains("svc 1"));

        final WifiScanRate quiet = new WifiScanRate(0L, WALL);
        quiet.noteReturn(false);
        final String unknown = quiet.snapshot(SAMPLE).get(0)
                .formatCompactLine(new SimpleDateFormat("HH:mm", Locale.US));
        Assert.assertFalse(unknown, unknown.contains(" imp"));
    }

    @Test
    public void idleProbesCountApartFromCalls() {
        final WifiScanRate rate = new WifiScanRate(0L, WALL);
        rate.noteProbe(false);
        rate.noteProbe(false);
        rate.noteProbe(true);
        rate.rollIfDue(60_000L, WALL + 60_000L, SAMPLE);

        final WifiScanMinute minute = rate.snapshot(SAMPLE).get(0);
        Assert.assertEquals(3, minute.probe);
        Assert.assertEquals(1, minute.probeOk);
        Assert.assertEquals(0, minute.call);
        Assert.assertEquals(0, minute.reject);
        Assert.assertFalse(minute.isBlank());
        final SimpleDateFormat hm = new SimpleDateFormat("HH:mm", Locale.US);
        Assert.assertTrue(minute.formatCompactLine(hm), minute.formatCompactLine(hm).contains(" pr3/1"));
        Assert.assertTrue(minute.formatLine(hm).contains("probe   3  probeok   1"));
        Assert.assertFalse(new WifiScanRate(0L, WALL).snapshot(SAMPLE).get(0)
                .formatCompactLine(hm).contains(" pr"));
    }
}
