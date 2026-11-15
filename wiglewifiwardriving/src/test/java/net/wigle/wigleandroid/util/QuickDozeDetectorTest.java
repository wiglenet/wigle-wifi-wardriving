package net.wigle.wigleandroid.util;

import net.wigle.wigleandroid.util.QuickDozeDetector.Verdict;

import org.junit.Assert;
import org.junit.Test;

public class QuickDozeDetectorTest {
    private static final long MIN = 60_000L;

    @Test
    public void deepIdleTwoMinutesAfterScreenOffIsQuickDoze() {
        // the Pixel trace: screen off, quick doze delay, deep idle about 2 minutes later
        final QuickDozeDetector d = new QuickDozeDetector();
        d.onScreenOff(0L, true, true);
        Assert.assertEquals(Verdict.QUICK_DOZE, d.onIdleOn(2 * MIN, true));
        // maintenance windows bring idle back on; one verdict per screen-off
        Assert.assertEquals(Verdict.NONE, d.onIdleOn(3 * MIN, true));
        Assert.assertEquals(Verdict.NONE, d.onScreenOn(20 * MIN, true, true));
    }

    @Test
    public void stockDozeTimingIsNotQuick() {
        final QuickDozeDetector d = new QuickDozeDetector();
        d.onScreenOff(0L, true, true);
        Assert.assertEquals(Verdict.NONE, d.onIdleOn(35 * MIN, true));
        // deep idle was seen this off period, so it can't clear a warning either
        Assert.assertEquals(Verdict.NONE, d.onScreenOn(40 * MIN, true, true));
    }

    @Test
    public void idleWhileNotScanningIsIgnored() {
        final QuickDozeDetector d = new QuickDozeDetector();
        d.onScreenOff(0L, false, true);
        Assert.assertEquals(Verdict.NONE, d.onIdleOn(MIN, false));
    }

    @Test
    public void idleWithoutAScreenOffIsIgnored() {
        // e.g. the app started with the screen already off
        Assert.assertEquals(Verdict.NONE, new QuickDozeDetector().onIdleOn(MIN, true));
        Assert.assertEquals(Verdict.NONE, new QuickDozeDetector().onScreenOn(MIN, true, true));
    }

    @Test
    public void longScreenOffOnBatteryWithoutIdleIsNormal() {
        final QuickDozeDetector d = new QuickDozeDetector();
        d.onScreenOff(0L, true, true);
        Assert.assertEquals(Verdict.NORMAL, d.onScreenOn(QuickDozeDetector.QUICK_LIMIT_MS, true, true));
        // state resets with the screen on
        Assert.assertEquals(Verdict.NONE, d.onIdleOn(QuickDozeDetector.QUICK_LIMIT_MS + 1, true));
    }

    @Test
    public void screenOffThatCannotProveNormalIsNone() {
        final QuickDozeDetector shortOff = new QuickDozeDetector();
        shortOff.onScreenOff(0L, true, true);
        Assert.assertEquals(Verdict.NONE, shortOff.onScreenOn(QuickDozeDetector.QUICK_LIMIT_MS - 1, true, true));

        final QuickDozeDetector charging = new QuickDozeDetector();
        charging.onScreenOff(0L, true, false);
        Assert.assertEquals(Verdict.NONE, charging.onScreenOn(30 * MIN, true, true));

        final QuickDozeDetector pluggedIn = new QuickDozeDetector();
        pluggedIn.onScreenOff(0L, true, true);
        Assert.assertEquals(Verdict.NONE, pluggedIn.onScreenOn(30 * MIN, true, false));

        final QuickDozeDetector notScanning = new QuickDozeDetector();
        notScanning.onScreenOff(0L, false, true);
        Assert.assertEquals(Verdict.NONE, notScanning.onScreenOn(30 * MIN, false, true));

        final QuickDozeDetector stoppedScanning = new QuickDozeDetector();
        stoppedScanning.onScreenOff(0L, true, true);
        Assert.assertEquals(Verdict.NONE, stoppedScanning.onScreenOn(30 * MIN, false, true));
    }

    @Test
    public void eachScreenOffStartsFresh() {
        final QuickDozeDetector d = new QuickDozeDetector();
        d.onScreenOff(0L, true, true);
        Assert.assertEquals(Verdict.QUICK_DOZE, d.onIdleOn(MIN, true));
        d.onScreenOn(5 * MIN, true, true);
        d.onScreenOff(10 * MIN, true, true);
        Assert.assertEquals(Verdict.QUICK_DOZE, d.onIdleOn(12 * MIN, true));
    }
}
