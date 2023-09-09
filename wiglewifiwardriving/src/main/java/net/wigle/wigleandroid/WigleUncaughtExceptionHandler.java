package net.wigle.wigleandroid;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;

import net.wigle.wigleandroid.util.Logging;
import net.wigle.wigleandroid.util.PreferenceKeys;

public class WigleUncaughtExceptionHandler implements Thread.UncaughtExceptionHandler {
    private final Context applicationContext;
    private final Thread.UncaughtExceptionHandler origHandler;

    public WigleUncaughtExceptionHandler ( Context applicationContext, Thread.UncaughtExceptionHandler origHandler ) {
        this.applicationContext = applicationContext.getApplicationContext();
        this.origHandler = origHandler;
    }

    @SuppressLint("ApplySharedPref")
    public void uncaughtException(@NonNull Thread thread, Throwable throwable ) {
        String error = "Thread: " + thread + " throwable: " + throwable;
        Logging.error( error );
        throwable.printStackTrace();

        MainActivity.writeError( thread, throwable, applicationContext );
        // set flag for error activity on next run
        final SharedPreferences prefs = applicationContext.getSharedPreferences(PreferenceKeys.SHARED_PREFS, 0);
        prefs.edit().putBoolean(PreferenceKeys.PREF_BLOWED_UP, true).commit();

        // give it to the regular handler
        origHandler.uncaughtException( thread, throwable );
    }
}
