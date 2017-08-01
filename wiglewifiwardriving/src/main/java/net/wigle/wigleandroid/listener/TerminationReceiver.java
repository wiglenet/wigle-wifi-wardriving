package net.wigle.wigleandroid.listener;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import net.wigle.wigleandroid.MainActivity;

/**
 * Created by arkasha on 7/31/17.
 */

public class TerminationReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        // Figure out what to do based on the intent type
        MainActivity.info("TerminationRec intent type: " + intent.getAction());
        switch (intent.getAction()) {
            case MainActivity.ACTION_END:
                MainActivity.info("Received close action");
                MainActivity.getMainActivity().finish();
                return;
            default:
                MainActivity.info("TerminationRec: unhandled intent action: " + intent.getAction());
        }
    }
}
