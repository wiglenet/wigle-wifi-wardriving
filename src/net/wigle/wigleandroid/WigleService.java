package net.wigle.wigleandroid;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;

public final class WigleService extends Service {
  private static final int NOTIFICATION_ID = 1;

  private GuardThread guardThread;
  private final AtomicBoolean done = new AtomicBoolean( false );

  // copied from javadoc
  private static final Class<?>[] mSetForegroundSignature = new Class[] {
    boolean.class};
  @SuppressWarnings("rawtypes")
  private static final Class[] mStartForegroundSignature = new Class[] {
    int.class, Notification.class};
  @SuppressWarnings("rawtypes")
  private static final Class[] mStopForegroundSignature = new Class[] {
    boolean.class};

  private NotificationManager notificationManager;
  private Method mSetForeground;
  private Method mStartForeground;
  private Method mStopForeground;
  private final Object[] mSetForegroundArgs = new Object[1];
  private final Object[] mStartForegroundArgs = new Object[2];
  private final Object[] mStopForegroundArgs = new Object[1];

  private class GuardThread extends Thread {
    public GuardThread() {
    }

    @Override
    public void run() {
      Thread.currentThread().setName( "GuardThread-" + Thread.currentThread().getName() );
      while ( ! done.get() ) {
        MainActivity.sleep( 15000L );
        setupNotification();
      }
      MainActivity.info("GuardThread done");
    }
  }

  private void setDone() {
    done.set( true );
    guardThread.interrupt();
  }

  @Override
  public IBinder onBind( final Intent intent ) {
    MainActivity.info( "service: onbind. intent: " + intent );
    return null;
  }

  @Override
  public void onRebind( final Intent intent ) {
    MainActivity.info( "service: onRebind. intent: " + intent );
    super.onRebind( intent );
  }

  @Override
  public boolean onUnbind( final Intent intent ) {
    MainActivity.info( "service: onUnbind. intent: " + intent );
    shutdownNotification();
		stopSelf();
    return super.onUnbind( intent );
  }

  @Override
  public void onCreate() {
    MainActivity.info( "service: onCreate" );

    notificationManager = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);
    try {
        mStartForeground = getClass().getMethod("startForeground",
                mStartForegroundSignature);
        mStopForeground = getClass().getMethod("stopForeground",
                mStopForegroundSignature);
    } catch (NoSuchMethodException e) {
        // Running on an older platform.
        mStartForeground = mStopForeground = null;
    }
    try {
        mSetForeground = getClass().getMethod("setForeground",
              mSetForegroundSignature);
    } catch (NoSuchMethodException e) {
        throw new IllegalStateException(
            "OS doesn't have Service.startForeground OR Service.setForeground!");
    }

    setupNotification();

    // don't use guard thread
    guardThread = new GuardThread();
    guardThread.start();
    super.onCreate();
  }

  @Override
  public void onDestroy() {
    MainActivity.info( "service: onDestroy" );
    // Make sure our notification is gone.
    shutdownNotification();
    setDone();
    super.onDestroy();
  }

  @Override
  public void onLowMemory() {
    super.onLowMemory();
    MainActivity.info( "service: onLowMemory" );
  }

  //This is the old onStart method that will be called on the pre-2.0
  //platform.  On 2.0 or later we override onStartCommand() so this
  //method will not be called.
  @Override
  public void onStart( Intent intent, int startId ) {
    MainActivity.info( "service: onStart" );
    handleCommand( intent );
  }

  @Override
  public int onStartCommand( Intent intent, int flags, int startId ) {
    MainActivity.info( "service: onStartCommand" );
    handleCommand( intent );
    // We want this service to continue running until it is explicitly
    // stopped, so return sticky.
    final int START_STICKY = 1;
    return START_STICKY;
  }

  private void handleCommand( Intent intent ) {
    MainActivity.info( "service: handleCommand: intent: " + intent );
  }

  private void shutdownNotification() {
    stopForegroundCompat( NOTIFICATION_ID );
  }

  @SuppressWarnings("deprecation")
  private void setupNotification() {
    if ( ! done.get() ) {
      final int icon = R.drawable.wiglewifi;
      final long when = System.currentTimeMillis();
      final String title = "Wigle Wifi Service";
      final Notification notification = new Notification( icon, title, when );
      notification.flags |= Notification.FLAG_NO_CLEAR | Notification.FLAG_ONGOING_EVENT;

      final Context context = getApplicationContext();
      final Intent notificationIntent = new Intent( this, MainActivity.class );
      final PendingIntent contentIntent = PendingIntent.getActivity( this, 0, notificationIntent, 0 );
      final long dbNets = ListFragment.lameStatic.dbNets;
      String text = "Waiting for info...";
      if ( dbNets > 0 ) {
        text = "Run: " + ListFragment.lameStatic.runNets
          + "  New: " + ListFragment.lameStatic.newNets + "  DB: " + dbNets;
      }
      if (! MainActivity.isScanning(context)) {
        text = "(Scanning Turned Off) " + text;
      }
      notification.setLatestEventInfo( context, title, text, contentIntent );

      startForegroundCompat( NOTIFICATION_ID, notification );
    }
  }

  void invokeMethod(Method method, Object[] args) {
    try {
        method.invoke(this, args);
    } catch (InvocationTargetException e) {
        // Should not happen.
      MainActivity.warn("Unable to invoke method", e);
    } catch (IllegalAccessException e) {
        // Should not happen.
      MainActivity.warn("Unable to invoke method", e);
    }
  }

  /**
   * This is a wrapper around the new startForeground method, using the older
   * APIs if it is not available.
   */
  private void startForegroundCompat(int id, Notification notification) {
    // If we have the new startForeground API, then use it.
    if (mStartForeground != null) {
      mStartForegroundArgs[0] = Integer.valueOf(id);
      mStartForegroundArgs[1] = notification;
      invokeMethod(mStartForeground, mStartForegroundArgs);
      return;
    }

    // Fall back on the old API.
    mSetForegroundArgs[0] = Boolean.TRUE;
    invokeMethod(mSetForeground, mSetForegroundArgs);
    notificationManager.notify(id, notification);
}

  /**
   * This is a wrapper around the new stopForeground method, using the older
   * APIs if it is not available.
   */
  private void stopForegroundCompat(int id) {
    // If we have the new stopForeground API, then use it.
    if (mStopForeground != null) {
      mStopForegroundArgs[0] = Boolean.TRUE;
      invokeMethod(mStopForeground, mStopForegroundArgs);
      return;
    }

    // Fall back on the old API.  Note to cancel BEFORE changing the
    // foreground state, since we could be killed at that point.
    notificationManager.cancel(id);
    mSetForegroundArgs[0] = Boolean.FALSE;
    invokeMethod(mSetForeground, mSetForegroundArgs);
  }

}
