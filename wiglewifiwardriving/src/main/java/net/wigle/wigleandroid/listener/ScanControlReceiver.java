package net.wigle.wigleandroid.listener;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import net.wigle.wigleandroid.MainActivity;

public class ScanControlReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        MainActivity.info("Scan control intent type: " + intent.getAction());
        final MainActivity main = MainActivity.getMainActivity();
        //Intent result = new Intent("net.wigle.wigleandroid.RESULT_ACTION");
        if (main != null) {
            switch (intent.getAction()) {
                case MainActivity.ACTION_PAUSE:
                    MainActivity.info("\tScanning paused");
                    main.handleScanChange(false);
                    break;
                case MainActivity.ACTION_SCAN:
                    MainActivity.info("\tScanning activated");
                    main.handleScanChange(true);
                    break;
                default:
                    setResult(Activity.RESULT_CANCELED, "net.wigle.wigleandroid.RESULT_ACTION", null);
                    break;
            }
        } else {
            MainActivity.error("Unable to handle scan control intent - null MainActivity");
        }
    }
}
