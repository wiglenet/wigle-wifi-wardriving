package net.wigle.wigleandroid;

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
    setupNotification();
    
    // don't use guard thread
    guardThread = new GuardThread();
    guardThread.start();
    super.onCreate();
  }
  
  @Override
  public void onDestroy() {
    ListActivity.info( "service: onDestroy" );
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
    final NotificationManager notificationManager = 
      (NotificationManager) getSystemService( Context.NOTIFICATION_SERVICE );
    notificationManager.cancel( NOTIFICATION_ID );
  }
  
  private void setupNotification() {
    if ( ! done.get() ) {
      final NotificationManager notificationManager = 
        (NotificationManager) getSystemService( Context.NOTIFICATION_SERVICE );
      
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
      notification.setLatestEventInfo( context, title, text, contentIntent );
      
      notificationManager.notify( NOTIFICATION_ID, notification );
    }
  }
  
}
