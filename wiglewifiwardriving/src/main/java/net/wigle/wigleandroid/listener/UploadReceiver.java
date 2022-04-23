package net.wigle.wigleandroid.listener;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import net.wigle.wigleandroid.MainActivity;
import net.wigle.wigleandroid.util.Logging;

/**
 * Created by arkasha on 7/31/17.
 */

public class UploadReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        // Figure out what to do based on the intent type
        if (null == intent) {
            Logging.error("null intent in upload onReceive");
            return;
        }
        Logging.info("UploadReceiver intent type: " + intent.getAction());
        switch (intent.getAction()) {
            case MainActivity.ACTION_UPLOAD:
                Logging.info("Received upload action");
                MainActivity ma = MainActivity.getMainActivity();
                if (null != ma) {
                    if (!ma.isTransferring()) {
                        ma.backgroundUploadFile();
                    } else {
                        Logging.info("ignoring upload command - transfer already in progress");
                    }
                }
                return;
            default:
                Logging.info("UploadReceiver: unhandled intent action: " + intent.getAction());
        }
    }
}
