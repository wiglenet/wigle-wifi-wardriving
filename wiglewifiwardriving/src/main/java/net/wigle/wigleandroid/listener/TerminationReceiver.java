package net.wigle.wigleandroid.listener;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import net.wigle.wigleandroid.MainActivity;
import net.wigle.wigleandroid.util.Logging;

/**
 * Created by arkasha on 7/31/17.
 */

public class TerminationReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        // Figure out what to do based on the intent type
        if (null == intent) {
            Logging.error("null intent in termination onReceive");
            return;
        }

        Logging.info("TerminationRec intent type: " + intent.getAction());
        switch (intent.getAction()) {
            case MainActivity.ACTION_END:
                Logging.info("Received close action");
                MainActivity ma = MainActivity.getMainActivity();
                if (null != ma) {
                    //ALIBI: multiple terminations in rapid succession can cause NPE
                    ma.finishSoon();
                }
                return;
            default:
                Logging.info("TerminationRec: unhandled intent action: " + intent.getAction());
        }
    }
}
