package net.wigle.wigleandroid.listener;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import net.wigle.wigleandroid.MainActivity;
import net.wigle.wigleandroid.util.Logging;
import net.wigle.wigleandroid.util.ScanAlarmScheduler;

/**
 * Receives AlarmManager alarms for WiFi, Bluetooth, and cell scans.
 * Triggers the scan and schedules the next alarm. Fires even in Doze via setAlarmClock.
 */
public class ScanAlarmReceiver extends BroadcastReceiver {

    public static final String ACTION_WIFI_SCAN_ALARM = "net.wigle.wigleandroid.WIFI_SCAN_ALARM";
    public static final String ACTION_BT_SCAN_ALARM = "net.wigle.wigleandroid.BT_SCAN_ALARM";
    public static final String ACTION_CELL_SCAN_ALARM = "net.wigle.wigleandroid.CELL_SCAN_ALARM";

    @Override
    public void onReceive(final Context context, final Intent intent) {
        if (intent == null || intent.getAction() == null) return;
        final MainActivity main = MainActivity.getMainActivity();
        if (main == null || main.isFinishing()) {
            Logging.info("ScanAlarmReceiver: MainActivity null or finishing, not scheduling next");
            return;
        }
        if (!main.isScanning()) {
            Logging.info("ScanAlarmReceiver: scanning off, not scheduling next");
            return;
        }
        final String action = intent.getAction();
        long period;
        switch (action) {
            case ACTION_WIFI_SCAN_ALARM:
                if (main.getState().wifiReceiver != null) {
                    main.getState().wifiReceiver.triggerWifiScan();
                    period = main.getState().wifiReceiver.getScanPeriod();
                } else {
                    return;
                }
                break;
            case ACTION_BT_SCAN_ALARM:
                if (main.getState().bluetoothReceiver != null) {
                    main.getState().bluetoothReceiver.triggerBluetoothScan();
                    period = main.getState().bluetoothReceiver.getScanPeriod();
                } else {
                    return;
                }
                break;
            case ACTION_CELL_SCAN_ALARM:
                if (main.getState().cellReceiver != null) {
                    main.getState().cellReceiver.triggerCellScan();
                    period = main.getState().cellReceiver.getScanPeriod();
                } else {
                    return;
                }
                break;
            default:
                Logging.warn("ScanAlarmReceiver: unknown action " + action);
                return;
        }
        if (period == 0L) {
            period = MainActivity.SCAN_DEFAULT;
        }
        if (!main.isScanning()) {
            return;
        }
        final ScanAlarmScheduler.ScanType type = getTypeForAction(action);
        ScanAlarmScheduler.scheduleNext(context.getApplicationContext(), type, period);
    }

    private static ScanAlarmScheduler.ScanType getTypeForAction(final String action) {
        switch (action) {
            case ACTION_WIFI_SCAN_ALARM: return ScanAlarmScheduler.ScanType.WIFI;
            case ACTION_BT_SCAN_ALARM: return ScanAlarmScheduler.ScanType.BLUETOOTH;
            case ACTION_CELL_SCAN_ALARM: return ScanAlarmScheduler.ScanType.CELL;
            default: throw new IllegalArgumentException("Unknown action: " + action);
        }
    }
}
