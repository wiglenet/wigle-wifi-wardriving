package net.wigle.wigleandroid;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;

public final class WigleService extends Service {
  private static final int NOTIFICATION_ID = 1;

  @Override
  public IBinder onBind( final Intent intent ) {
    WigleAndroid.info( "service: onbind" );
    return null;
  }
  
  @Override
  public void onRebind( final Intent intent ) {
    WigleAndroid.info( "service: onRebind" );
    super.onRebind( intent );
  }

  @Override
  public boolean onUnbind( final Intent intent ) {
    WigleAndroid.info( "service: onUnbind" );
    shutdownNotification();
		stopSelf();
    return super.onUnbind( intent );
  }

  @Override
  public void onCreate() {
    WigleAndroid.info( "service: oncreate" );
    setupNotification();
    super.onCreate();
  }
  
  @Override
  public void onDestroy() {
    WigleAndroid.info( "service: ondestroy" );
    shutdownNotification();
    super.onDestroy();
  }
  
  @Override
  public void onLowMemory() {
    super.onLowMemory();
    WigleAndroid.info( "service: onLowMemory" );
  }

  private void shutdownNotification() {
    final NotificationManager notificationManager = 
      (NotificationManager) getSystemService( Context.NOTIFICATION_SERVICE );
    notificationManager.cancel( NOTIFICATION_ID );
  }
  
  private void setupNotification() {
    final NotificationManager notificationManager = 
      (NotificationManager) getSystemService( Context.NOTIFICATION_SERVICE );
    
    final int icon = R.drawable.wiglewifi;
    final CharSequence tickerText = "Wigle Wifi Service";
    final long when = System.currentTimeMillis();
    final Notification notification = new Notification( icon, tickerText, when );
    notification.flags |= Notification.FLAG_NO_CLEAR | Notification.FLAG_ONGOING_EVENT;
    
    // make the notification be blue, where supported
    notification.ledARGB = 0xff0000ff;
    notification.ledOnMS = 300;
    notification.ledOffMS = 1000;
    notification.flags |= Notification.FLAG_SHOW_LIGHTS;
    
    final Context context = getApplicationContext();
    final Intent notificationIntent = new Intent( this, WigleAndroid.class );
    final PendingIntent contentIntent = PendingIntent.getActivity( this, 0, notificationIntent, 0 );
    notification.setLatestEventInfo( context, tickerText, tickerText, contentIntent );
    
    notificationManager.notify( NOTIFICATION_ID, notification );
  }
  
}
