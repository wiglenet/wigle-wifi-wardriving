package net.wigle.wigleandroid.util;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import net.wigle.wigleandroid.MainActivity;
import net.wigle.wigleandroid.listener.ScanAlarmReceiver;

/**
 * Schedules scan alarms via AlarmManager.setAlarmClock() so scans run even in Doze.
 * Ensures at most one alarm per scan type is ever pending.
 */
public final class ScanAlarmScheduler {

    public enum ScanType { WIFI, BLUETOOTH, CELL }

    private static final int REQUEST_WIFI = 1;
    private static final int REQUEST_BT = 2;
    private static final int REQUEST_CELL = 3;

    private ScanAlarmScheduler() {}

    /**
     * Schedule the next alarm for the given scan type. Cancels any existing alarm first.
     *
     * @param context application context
     * @param type    which scan type to schedule
     * @param delayMs delay in ms before the alarm fires
     */
    public static void scheduleNext(final Context context, final ScanType type, final long delayMs) {
        if (delayMs <= 0) {
            Logging.warn("ScanAlarmScheduler: invalid delay " + delayMs + " for " + type);
            return;
        }
        final AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) {
            Logging.warn("ScanAlarmScheduler: AlarmManager is null");
            return;
        }
        cancel(context, type);
        final long triggerTime = System.currentTimeMillis() + delayMs;
        final Intent intent = new Intent(context, ScanAlarmReceiver.class);
        intent.setAction(getActionForType(type));
        final int requestCode = getRequestCodeForType(type);
        final int flags = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                ? PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
                : PendingIntent.FLAG_UPDATE_CURRENT;
        final PendingIntent operation = PendingIntent.getBroadcast(context, requestCode, intent, flags);
        final Intent showIntent = new Intent(context, MainActivity.class);
        showIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        final PendingIntent showPending = PendingIntent.getActivity(context, requestCode, showIntent, flags);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            final AlarmManager.AlarmClockInfo info = new AlarmManager.AlarmClockInfo(triggerTime, showPending);
            alarmManager.setAlarmClock(info, operation);
        } else {
            alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerTime, operation);
        }
        Logging.debug("ScanAlarmScheduler: scheduled " + type + " in " + delayMs + " ms");
    }

    /**
     * Cancel the alarm for the given scan type.
     */
    public static void cancel(final Context context, final ScanType type) {
        final AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) return;
        final Intent intent = new Intent(context, ScanAlarmReceiver.class);
        intent.setAction(getActionForType(type));
        final int requestCode = getRequestCodeForType(type);
        final int flags = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                ? PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
                : PendingIntent.FLAG_UPDATE_CURRENT;
        final PendingIntent pending = PendingIntent.getBroadcast(context, requestCode, intent, flags);
        alarmManager.cancel(pending);
        try {
            pending.cancel();
        } catch (Exception ignored) {}
    }

    /**
     * Cancel all scan alarms.
     */
    public static void cancelAll(final Context context) {
        cancel(context, ScanType.WIFI);
        cancel(context, ScanType.BLUETOOTH);
        cancel(context, ScanType.CELL);
        Logging.info("ScanAlarmScheduler: cancelled all alarms");
    }

    static String getActionForType(final ScanType type) {
        switch (type) {
            case WIFI: return ScanAlarmReceiver.ACTION_WIFI_SCAN_ALARM;
            case BLUETOOTH: return ScanAlarmReceiver.ACTION_BT_SCAN_ALARM;
            case CELL: return ScanAlarmReceiver.ACTION_CELL_SCAN_ALARM;
            default: throw new IllegalArgumentException("Unknown type: " + type);
        }
    }

    static int getRequestCodeForType(final ScanType type) {
        switch (type) {
            case WIFI: return REQUEST_WIFI;
            case BLUETOOTH: return REQUEST_BT;
            case CELL: return REQUEST_CELL;
            default: throw new IllegalArgumentException("Unknown type: " + type);
        }
    }
}
