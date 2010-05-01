package net.wigle.wigleandroid;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;

public class WigleService extends Service {
  private static final int NOTIFICATION_ID = 1;

  @Override
  public IBinder onBind( Intent intent ) {
    WigleAndroid.info("service: onbind");
    return null;
  }
  
  @Override
  public void onRebind( Intent intent ) {
    WigleAndroid.info("service: onRebind");
    super.onRebind( intent );
  }

  @Override
  public boolean onUnbind( Intent intent ) {
    WigleAndroid.info("service: onUnbind");
    shutdownNotification();
		stopSelf();
    return super.onUnbind( intent );
  }

  @Override
  public void onCreate() {
    WigleAndroid.info("service: oncreate");
    setupNotification();
    super.onCreate();
  }
  
  @Override
  public void onDestroy() {
    WigleAndroid.info("service: ondestroy");
    shutdownNotification();
    super.onDestroy();
  }
  
  @Override
  public void onLowMemory() {
    super.onLowMemory();
    WigleAndroid.info("service: onLowMemory");
  }

  private void shutdownNotification() {
    NotificationManager notificationManager = 
      (NotificationManager) getSystemService( Context.NOTIFICATION_SERVICE );
    notificationManager.cancel( NOTIFICATION_ID );
  }
  
  private void setupNotification() {
    NotificationManager notificationManager = 
      (NotificationManager) getSystemService( Context.NOTIFICATION_SERVICE );
    
    int icon = R.drawable.wiglewifi;
    CharSequence tickerText = "Wigle Wifi Service";
    long when = System.currentTimeMillis();
    Notification notification = new Notification(icon, tickerText, when);
    notification.flags |= Notification.FLAG_NO_CLEAR;
    
    Context context = getApplicationContext();
    CharSequence contentTitle = "Wigle Wifi Service";
    CharSequence contentText = "Wigle Wifi Service";
    Intent notificationIntent = new Intent( this, WigleAndroid.class );
    PendingIntent contentIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0);
    notification.setLatestEventInfo(context, contentTitle, contentText, contentIntent);
    
    notificationManager.notify(NOTIFICATION_ID, notification);
  }
  
}
