package net.wigle.wigleandroid.listener;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import net.wigle.wigleandroid.ListFragment;
import net.wigle.wigleandroid.MainActivity;

public class StartWigleAtBootReciever extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        Intent serviceIntent = new Intent(context, MainActivity.class);
        final SharedPreferences prefs = context.getSharedPreferences( ListFragment.SHARED_PREFS, 0 );
        final boolean mustStart = prefs.getBoolean( ListFragment.PREF_START_AT_BOOT, false );
        if (mustStart && Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            serviceIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(serviceIntent);
        }
    }
}
