package net.wigle.wigleandroid.util;

import android.content.SharedPreferences;

import net.wigle.wigleandroid.ListFragment;

public class StatsUtil {
    public static long newNetsSinceUpload(final SharedPreferences prefs) {
        long newSinceUpload = 0;
        final long marker = prefs.getLong( PreferenceKeys.PREF_DB_MARKER, 0L );
        final long uploaded = prefs.getLong( PreferenceKeys.PREF_NETS_UPLOADED, 0L );
        // marker is set but no uploaded, a migration situation, so return zero
        if (marker == 0 || uploaded != 0) {
            newSinceUpload = ListFragment.lameStatic.dbNets - uploaded;
            if ( newSinceUpload < 0 ) {
                newSinceUpload = 0;
            }
        }
        return newSinceUpload;
    }
}
