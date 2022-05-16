package net.wigle.wigleandroid.util;

import android.util.Log;

import net.wigle.wigleandroid.MainActivity;

/**
 * Logging calls for WiGLE. References static state manip. in MainActivity for dynamic log saving.
 */
public class Logging {
    private static final String LOG_TAG = "wigle";

    public static void info(final String value) {
        Log.i(LOG_TAG, Thread.currentThread().getName() + "] " + value);
        MainActivity.saveLog(value);
    }

    public static void warn(final String value) {
        Log.w(LOG_TAG, Thread.currentThread().getName() + "] " + value);
        MainActivity.saveLog(value);
    }

    public static void error(final String value) {
        Log.e(LOG_TAG, Thread.currentThread().getName() + "] " + value);
        MainActivity.saveLog(value);
    }

    public static void info(final String value, final Throwable t) {
        Log.i(LOG_TAG, Thread.currentThread().getName() + "] " + value, t);
        MainActivity.saveLog(value);
    }

    public static void warn(final String value, final Throwable t) {
        Log.w(LOG_TAG, Thread.currentThread().getName() + "] " + value, t);
        MainActivity.saveLog(value);
    }

    public static void error(final String value, final Throwable t) {
        Log.e(LOG_TAG, Thread.currentThread().getName() + "] " + value, t);
        MainActivity.saveLog(value);
    }
}
