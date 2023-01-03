package net.wigle.wigleandroid.listener;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import net.wigle.wigleandroid.MainActivity;
import net.wigle.wigleandroid.util.PreferenceKeys;

public class StartWigleAtBootReciever extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (null == intent) {
            return;
        }
        Intent serviceIntent = new Intent(context, MainActivity.class);
        final SharedPreferences prefs = context.getSharedPreferences( PreferenceKeys.SHARED_PREFS, 0 );
        final boolean mustStart = prefs.getBoolean( PreferenceKeys.PREF_START_AT_BOOT, false );
        if (mustStart && Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            serviceIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(serviceIntent);
        }
    }
}
