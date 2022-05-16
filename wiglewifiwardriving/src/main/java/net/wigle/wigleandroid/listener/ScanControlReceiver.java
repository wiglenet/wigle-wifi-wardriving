package net.wigle.wigleandroid.listener;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import net.wigle.wigleandroid.MainActivity;
import net.wigle.wigleandroid.util.Logging;

public class ScanControlReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (null == intent) {
            Logging.error("null intent in scan control onReceive");
            return;
        }
        Logging.info("Scan control intent type: " + intent.getAction());
        final MainActivity main = MainActivity.getMainActivity();
        //Intent result = new Intent("net.wigle.wigleandroid.RESULT_ACTION");
        if (main != null) {
            switch (intent.getAction()) {
                case MainActivity.ACTION_PAUSE:
                    Logging.info("\tScanning paused");
                    main.handleScanChange(false);
                    break;
                case MainActivity.ACTION_SCAN:
                    Logging.info("\tScanning activated");
                    main.handleScanChange(true);
                    break;
                default:
                    setResult(Activity.RESULT_CANCELED, "net.wigle.wigleandroid.RESULT_ACTION", null);
                    break;
            }
        } else {
            Logging.error("Unable to handle scan control intent - null MainActivity");
        }
    }
}
