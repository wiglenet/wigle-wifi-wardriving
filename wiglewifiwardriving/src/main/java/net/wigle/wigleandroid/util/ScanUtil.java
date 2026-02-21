package net.wigle.wigleandroid.util;

import android.content.SharedPreferences;
import android.location.Location;

import net.wigle.wigleandroid.MainActivity;

/**
 * scan-period logic for WiFi, cell, and Bluetooth receivers.
 */
public final class ScanUtil {

    /** Speed in m/s above which "fast" period is used (~5 mph). */
    public static final float SPEED_FAST_MPS = 2.2352f;
    /** Speed in m/s below which "still" period is used. */
    public static final float SPEED_STILL_MPS = 0.1f;

    private ScanUtil() {}

    /**
     * Resolve scan period from preferences and current location speed.
     *
     * @param prefs              shared preferences
     * @param location           current location (may be null; null or low speed uses "still" pref)
     * @param prefPeriod         preference key for normal speed
     * @param defaultPeriod      default for normal speed
     * @param prefPeriodFast     preference key for fast (e.g. driving)
     * @param defaultPeriodFast  default for fast
     * @param prefPeriodStill     preference key for still/slow
     * @param defaultPeriodStill default for still
     * @return period in milliseconds
     */
    public static long getScanPeriod(
            final SharedPreferences prefs,
            final Location location,
            final String prefPeriod,
            final long defaultPeriod,
            final String prefPeriodFast,
            final long defaultPeriodFast,
            final String prefPeriodStill,
            final long defaultPeriodStill) {
        String scanPref = prefPeriod;
        long defaultRate = defaultPeriod;
        if (location != null && location.getSpeed() >= SPEED_FAST_MPS) {
            scanPref = prefPeriodFast;
            defaultRate = defaultPeriodFast;
        } else if (location == null || location.getSpeed() < SPEED_STILL_MPS) {
            scanPref = prefPeriodStill;
            defaultRate = defaultPeriodStill;
        }
        return prefs.getLong(scanPref, defaultRate);
    }

    /**
     * WiFi/cell scan period (same preferences and defaults as WifiReceiver/CellReceiver).
     */
    public static long getWifiScanPeriod(final SharedPreferences prefs, final Location location) {
        return getScanPeriod(
                prefs, location,
                PreferenceKeys.PREF_SCAN_PERIOD, MainActivity.SCAN_DEFAULT,
                PreferenceKeys.PREF_SCAN_PERIOD_FAST, MainActivity.SCAN_FAST_DEFAULT,
                PreferenceKeys.PREF_SCAN_PERIOD_STILL, MainActivity.SCAN_STILL_DEFAULT);
    }

    /**
     * Bluetooth scan period (classic/OG BT preferences and defaults).
     */
    public static long getBtScanPeriod(final SharedPreferences prefs, final Location location) {
        return getScanPeriod(
                prefs, location,
                PreferenceKeys.PREF_OG_BT_SCAN_PERIOD, MainActivity.OG_BT_SCAN_DEFAULT,
                PreferenceKeys.PREF_OG_BT_SCAN_PERIOD_FAST, MainActivity.OG_BT_SCAN_FAST_DEFAULT,
                PreferenceKeys.PREF_OG_BT_SCAN_PERIOD_STILL, MainActivity.OG_BT_SCAN_STILL_DEFAULT);
    }
}
