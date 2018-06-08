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
        oreoSimpleNotification();
        shutdownNotification();
        stopSelf();
        return super.onUnbind( intent );
    }

    /**
     * This is called if the user force-kills the app
     */
    @Override
    public void onTaskRemoved(Intent rootIntent) {
        MainActivity.info("service: onTaskRemoved.");
        if (! done.get()) {
            final MainActivity mainActivity = MainActivity.getMainActivity();
            if (mainActivity != null) {
                mainActivity.finishSoon();
            }
            setDone();
        }
        oreoSimpleNotification();
        shutdownNotification();
        stopSelf();
        super.onTaskRemoved(rootIntent);
        MainActivity.info("service: onTaskRemoved complete.");
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
        // avoid stupid android bug causing:
        // android.app.RemoteServiceException: Context.startForegroundService() did not then call Service.startForeground()
        oreoSimpleNotification();
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
        setupNotification();
    }

    @Override
    public int onStartCommand( Intent intent, int flags, int startId ) {
        MainActivity.info( "service: onStartCommand" );
        handleCommand( intent );
        setupNotification();
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

    private void oreoSimpleNotification() {
        // make a dummy call to foreground to keep it from crashing with RemoteServiceException
        // https://developer.android.com/about/versions/oreo/android-8.0-changes.html
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            MainActivity.info("oreoSimpleNotification");
            final NotificationManager notificationManager =
                    (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            final String title = getApplicationContext().getString(R.string.wigle_service);
            final NotificationChannel channel = new NotificationChannel(NOTIFICATION_CHANNEL_ID,
                    title, NotificationManager.IMPORTANCE_LOW);
            notificationManager.createNotificationChannel(channel);

            final Notification.Builder builder = new Notification.Builder(getApplicationContext(), NOTIFICATION_CHANNEL_ID);
            startForeground(NOTIFICATION_ID, builder.build());
            MainActivity.info("oreoSimpleNotification done");
        }
    }

    private void setupNotification() {
        if ( done.get() ) {
            oreoSimpleNotification();
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

            final Intent pauseSharedIntent = new Intent();
            pauseSharedIntent.setAction("net.wigle.wigleandroid.PAUSE");
            final PendingIntent pauseIntent = PendingIntent.getBroadcast(MainActivity.getMainActivity(), 0, pauseSharedIntent ,PendingIntent.FLAG_CANCEL_CURRENT);

            final Intent scanSharedIntent = new Intent();
            scanSharedIntent.setAction("net.wigle.wigleandroid.SCAN");
            final PendingIntent scanIntent = PendingIntent.getBroadcast(MainActivity.getMainActivity(), 0, scanSharedIntent ,PendingIntent.FLAG_CANCEL_CURRENT);

            final Intent uploadSharedIntent = new Intent();
            uploadSharedIntent.setAction("net.wigle.wigleandroid.UPLOAD");
            final PendingIntent uploadIntent = PendingIntent.getBroadcast(MainActivity.getMainActivity(), 0, uploadSharedIntent ,PendingIntent.FLAG_CANCEL_CURRENT);

            Notification notification = null;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                notification = getNotification26(title, context, text, when, contentIntent, pauseIntent, scanIntent, uploadIntent);
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
                builder.addAction(android.R.drawable.ic_media_pause, "Pause", pauseIntent);
                builder.addAction(android.R.drawable.ic_media_play, "Scan", scanIntent);
                builder.addAction(android.R.drawable.ic_menu_upload, "Upload", uploadIntent);

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
                                           final PendingIntent pauseIntent, final PendingIntent scanIntent,
                                           final PendingIntent uploadIntent) {
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
            builder.addAction(android.R.drawable.ic_media_pause, "Pause", pauseIntent);
            builder.addAction(android.R.drawable.ic_media_play, "Scan", scanIntent);
            builder.addAction(android.R.drawable.ic_menu_upload, "Upload", uploadIntent);

            return builder.build();
        }
        return null;
    }
}
