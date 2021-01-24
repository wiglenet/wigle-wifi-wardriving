package net.wigle.wigleandroid.listener;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import net.wigle.wigleandroid.MainActivity;

/**
 * Created by arkasha on 7/31/17.
 */

public class UploadReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        // Figure out what to do based on the intent type
        if (null == intent) {
            MainActivity.error("null intent in upload onReceive");
            return;
        }
        MainActivity.info("UploadReceiver intent type: " + intent.getAction());
        switch (intent.getAction()) {
            case MainActivity.ACTION_UPLOAD:
                MainActivity.info("Received upload action");
                MainActivity ma = MainActivity.getMainActivity();
                if (null != ma) {
                    if (!ma.isTransferring()) {
                        ma.backgroundUploadFile();
                    } else {
                        MainActivity.info("ignoring upload command - transfer already in progress");
                    }
                }
                return;
            default:
                MainActivity.info("UploadReceiver: unhandled intent action: " + intent.getAction());
        }
    }
}
