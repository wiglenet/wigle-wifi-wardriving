package net.wigle.wigleandroid;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

public class WigleUncaughtExceptionHandler implements Thread.UncaughtExceptionHandler {
    private final Context applicationContext;
    private final Thread.UncaughtExceptionHandler origHandler;
    private final PendingIntent pendingIntent;

    public WigleUncaughtExceptionHandler ( Context applicationContext, Thread.UncaughtExceptionHandler origHandler ) {
        this.applicationContext = applicationContext.getApplicationContext();
        this.origHandler = origHandler;

        // set up pending email intent to email stacktrace logs if needed
        final Intent errorReportIntent = new Intent( this.applicationContext, ErrorReportActivity.class );
        errorReportIntent.putExtra( MainActivity.ERROR_REPORT_DO_EMAIL, true );
        //noinspection ResourceType
        pendingIntent = PendingIntent.getActivity( this.applicationContext, 0,
                errorReportIntent, errorReportIntent.getFlags() );
    }

    public void uncaughtException( Thread thread, Throwable throwable ) {
        String error = "Thread: " + thread + " throwable: " + throwable;
        MainActivity.error( error );
        throwable.printStackTrace();

        MainActivity.writeError( thread, throwable, applicationContext );
        // set the email intent to go off in a few seconds
        AlarmManager mgr = (AlarmManager) applicationContext.getSystemService(Context.ALARM_SERVICE);
        mgr.set( AlarmManager.RTC, System.currentTimeMillis() + 5000, pendingIntent );

        // give it to the regular handler
        origHandler.uncaughtException( thread, throwable );
    }
}
