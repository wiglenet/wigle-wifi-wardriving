package net.wigle.wigleandroid;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

public class WigleUncaughtExceptionHandler implements Thread.UncaughtExceptionHandler {
  private final Context context; 
  private final Thread.UncaughtExceptionHandler origHandler;
  private final PendingIntent pendingIntent;
  
  public WigleUncaughtExceptionHandler ( Context context, Thread.UncaughtExceptionHandler origHandler ) {
    this.context = context.getApplicationContext();
    this.origHandler = origHandler;
    
    // set up pending email intent to email stacktrace logs if needed
    final Intent errorReportIntent = new Intent( this.context, ErrorReportActivity.class );
    errorReportIntent.putExtra( ListActivity.ERROR_REPORT_DO_EMAIL, true );
    pendingIntent = PendingIntent.getActivity( this.context, 0,
        errorReportIntent, errorReportIntent.getFlags() );
  }
  
  public void uncaughtException( Thread thread, Throwable throwable ) {
    String error = "Thread: " + thread + " throwable: " + throwable;
    ListActivity.error( error );
    throwable.printStackTrace();
    
    ListActivity.writeError( thread, throwable, context );
    // set the email intent to go off in a few seconds
    AlarmManager mgr = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
    mgr.set( AlarmManager.RTC, System.currentTimeMillis() + 5000, pendingIntent );

    // give it to the regular handler
    origHandler.uncaughtException( thread, throwable );
  }
}
