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
  private AtomicBoolean done = new AtomicBoolean( false );
  
  // copied from javadoc
  @SuppressWarnings("unchecked")
  private static final Class[] mStartForegroundSignature = new Class[] {
    int.class, Notification.class};
  @SuppressWarnings("unchecked")
  private static final Class[] mStopForegroundSignature = new Class[] {
    boolean.class};

  private NotificationManager notificationManager;
  private Method mStartForeground;
  private Method mStopForeground;
  private Object[] mStartForegroundArgs = new Object[2];
  private Object[] mStopForegroundArgs = new Object[1];
  
  private class GuardThread extends Thread {
    public GuardThread() {      
    }
    
    public void run() {
      Thread.currentThread().setName( "GuardThread-" + Thread.currentThread().getName() );
      while ( ! done.get() ) {
        ListActivity.sleep( 15000L );
        setupNotification();
      }
      ListActivity.info("GuardThread done");
    }    
  }
  
  private void setDone() {
    done.set( true );
    guardThread.interrupt();
  }

  @Override
  public IBinder onBind( final Intent intent ) {
    ListActivity.info( "service: onbind. intent: " + intent );
    return null;
  }
  
  @Override
  public void onRebind( final Intent intent ) {
    ListActivity.info( "service: onRebind. intent: " + intent );
    super.onRebind( intent );
  }

  @Override
  public boolean onUnbind( final Intent intent ) {
    ListActivity.info( "service: onUnbind. intent: " + intent );
    shutdownNotification();
		stopSelf();
    return super.onUnbind( intent );
  }

  @Override
  public void onCreate() {
    ListActivity.info( "service: onCreate" );
    
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
    setupNotification();
    
    // don't use guard thread
    guardThread = new GuardThread();
    guardThread.start();
    super.onCreate();
  }
  
  @Override
  public void onDestroy() {
    ListActivity.info( "service: onDestroy" );
    // Make sure our notification is gone.
    shutdownNotification();
    setDone();
    super.onDestroy();
  }
  
  @Override
  public void onLowMemory() {
    super.onLowMemory();
    ListActivity.info( "service: onLowMemory" );
  }
  
  //This is the old onStart method that will be called on the pre-2.0
  //platform.  On 2.0 or later we override onStartCommand() so this
  //method will not be called.
  @Override
  public void onStart( Intent intent, int startId ) {
    ListActivity.info( "service: onStart" );
    handleCommand( intent );
  }

  public int onStartCommand( Intent intent, int flags, int startId ) {
    ListActivity.info( "service: onStartCommand" );
    handleCommand( intent );
    // We want this service to continue running until it is explicitly
    // stopped, so return sticky.
    final int START_STICKY = 1;
    return START_STICKY;
  }
  
  private void handleCommand( Intent intent ) {
    ListActivity.info( "service: handleCommand: intent: " + intent );
  }

  private void shutdownNotification() {
    stopForegroundCompat( NOTIFICATION_ID );
  }
  
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
      final long dbNets = ListActivity.lameStatic.dbNets;
      String text = "Waiting for info...";
      if ( dbNets > 0 ) {
        text = "Run: " + ListActivity.lameStatic.runNets
          + "  New: " + ListActivity.lameStatic.newNets + "  DB: " + dbNets;
      }      
      if (! ListActivity.isScanning(context)) {
        text = "(Scanning Turned Off) " + text;
      }
      notification.setLatestEventInfo( context, title, text, contentIntent );
      
      startForegroundCompat( NOTIFICATION_ID, notification );
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
      try {       
        mStartForeground.invoke(this, mStartForegroundArgs);
      } catch (InvocationTargetException e) {
        // Should not happen.
        ListActivity.warn("Unable to invoke startForeground", e);
      } catch (IllegalAccessException e) {
        // Should not happen.
        ListActivity.warn("Unable to invoke startForeground", e);
      }
      return;
    }

    // Fall back on the old API.
    setForeground(true);
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
      try {
        mStopForeground.invoke(this, mStopForegroundArgs);
      } catch (InvocationTargetException e) {
        // Should not happen.
        ListActivity.warn("Unable to invoke stopForeground", e);
      } catch (IllegalAccessException e) {
        // Should not happen.
        ListActivity.warn("Unable to invoke stopForeground", e);
      }
      return;
    }

    // Fall back on the old API.  Note to cancel BEFORE changing the
    // foreground state, since we could be killed at that point.
    notificationManager.cancel(id);
    setForeground(false);
  }
  
}
