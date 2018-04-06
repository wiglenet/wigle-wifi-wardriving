package net.wigle.wigleandroid;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;
import android.support.v4.app.NotificationCompat;

import java.util.concurrent.atomic.AtomicBoolean;

public final class WigleService extends Service {
    private static final int NOTIFICATION_ID = 1;
    public static final String NOTIFICATION_CHANNEL_ID = "wigle_notification_1";

    private GuardThread guardThread;
    private final AtomicBoolean done = new AtomicBoolean( false );
    private Bitmap largeIcon = null;

    private class GuardThread extends Thread {
        GuardThread() {
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
    @SuppressWarnings("deprecation")
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
        return Service.START_STICKY;
    }

    private void handleCommand( Intent intent ) {
        MainActivity.info( "service: handleCommand: intent: " + intent );
    }

    private void shutdownNotification() {
        stopForeground(true);
    }

    private void setupNotification() {
        if ( done.get() ) {
            // make a dummy call to foreground to keep it from crashing with RemoteServiceException
            // https://developer.android.com/about/versions/oreo/android-8.0-changes.html
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                final Notification.Builder builder = new Notification.Builder(getApplicationContext(), NOTIFICATION_CHANNEL_ID);
                startForeground(NOTIFICATION_ID, builder.build());
            }
        }
        else {
            final long when = System.currentTimeMillis();
            final Context context = getApplicationContext();
            final String title = context.getString(R.string.wigle_service);

            final Intent notificationIntent = new Intent( this, MainActivity.class );
            final PendingIntent contentIntent = PendingIntent.getActivity( this, 0, notificationIntent, 0 );
            final long dbNets = ListFragment.lameStatic.dbNets;
            String text = context.getString(R.string.list_waiting_gps);
            if ( dbNets > 0 ) {
                text = context.getString(R.string.run) + ": " + ListFragment.lameStatic.runNets
                        + "  "+ context.getString(R.string.new_word) + ": " + ListFragment.lameStatic.newNets
                        + "  "+ context.getString(R.string.db) + ": " + dbNets;
            }
            if (! MainActivity.isScanning(context)) {
                text = context.getString(R.string.list_scanning_off) + " " + text;
            }
            if (largeIcon == null) {
                largeIcon = BitmapFactory.decodeResource(getResources(), R.drawable.wiglewifi);
            }

            final Uri uri = Uri.EMPTY;
            final Intent pauseSharedIntent = new Intent(Intent.ACTION_DELETE, uri, this, ShareActivity.class );
            final PendingIntent pauseIntent = PendingIntent.getActivity( this, 0, pauseSharedIntent, 0 );

            final Intent scanSharedIntent = new Intent(Intent.ACTION_INSERT, uri, this, ShareActivity.class );
            final PendingIntent scanIntent = PendingIntent.getActivity( this, 0, scanSharedIntent, 0 );

            // final Intent uploadSharedIntent = new Intent(Intent.ACTION_SYNC, uri, this, ShareActivity.class );
            // final PendingIntent uploadIntent = PendingIntent.getActivity( this, 0, uploadSharedIntent, 0 );

            Notification notification = null;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                notification = getNotification26(title, context, text, when, contentIntent, pauseIntent, scanIntent);
            }
            else {
                final NotificationCompat.Builder builder = new NotificationCompat.Builder(context);
                builder.setContentIntent(contentIntent);
                builder.setNumber((int) ListFragment.lameStatic.newNets);
                builder.setTicker(title);
                builder.setContentTitle(title);
                builder.setContentText(text);
                builder.setWhen(when);
                builder.setLargeIcon(largeIcon);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    builder.setSmallIcon(R.drawable.wiglewifi_small_white);
                } else {
                    builder.setSmallIcon(R.drawable.wiglewifi_small);
                }
                builder.setOngoing(true);
                builder.setCategory("SERVICE");
                builder.setPriority(NotificationCompat.PRIORITY_LOW);
                builder.setVisibility(NotificationCompat.VISIBILITY_PUBLIC);
                builder.addAction(R.drawable.wiglewifi_small_black_white, "Pause", pauseIntent);
                builder.addAction(R.drawable.wiglewifi_small_black_white, "Scan", scanIntent);
                // builder.addAction(R.drawable.wiglewifi_small_black_white, "Upload", uploadIntent);

                try {
                    //ALIBI: https://stackoverflow.com/questions/43123466/java-lang-nullpointerexception-attempt-to-invoke-interface-method-java-util-it
                    notification = builder.build();
                } catch (NullPointerException npe) {
                    MainActivity.error("NPE trying to build notification. "+npe.getMessage());
                }
            }
            if (null != notification) {
                try {
                    startForeground(NOTIFICATION_ID, notification);
                } catch (Exception ex) {
                    MainActivity.error("notification service error: ", ex);
                }
            }
        }
    }

    private Notification getNotification26(final String title, final Context context, final String text,
                                           final long when, final PendingIntent contentIntent,
                                           final PendingIntent pauseIntent, final PendingIntent scanIntent) {
        // new notification channel
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            final NotificationManager notificationManager =
                    (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

            final NotificationChannel channel = new NotificationChannel(NOTIFICATION_CHANNEL_ID,
                    title, NotificationManager.IMPORTANCE_LOW);
            notificationManager.createNotificationChannel(channel);

            // copied from above
            final Notification.Builder builder = new Notification.Builder(context, NOTIFICATION_CHANNEL_ID);
            builder.setContentIntent(contentIntent);
            builder.setNumber((int) ListFragment.lameStatic.newNets);
            builder.setTicker(title);
            builder.setContentTitle(title);
            builder.setContentText(text);
            builder.setWhen(when);
            builder.setLargeIcon(largeIcon);
            builder.setSmallIcon(R.drawable.wiglewifi_small_white);
            builder.setOngoing(true);
            builder.setCategory("SERVICE");
            builder.setVisibility(Notification.VISIBILITY_PUBLIC);
            builder.addAction(R.drawable.wiglewifi_small_black_white, "Pause", pauseIntent);
            builder.addAction(R.drawable.wiglewifi_small_black_white, "Scan", scanIntent);
            // builder.addAction(R.drawable.wiglewifi_small_black_white, "Upload", uploadIntent);

            return builder.build();
        }
        return null;
    }
}
