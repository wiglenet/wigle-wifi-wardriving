package net.wigle.wigleandroid;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Icon;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.widget.RemoteViews;

import androidx.annotation.RequiresApi;
import androidx.core.app.NotificationCompat;

import net.wigle.wigleandroid.ui.UINumberFormat;
import net.wigle.wigleandroid.util.Logging;
import net.wigle.wigleandroid.util.PreferenceKeys;

import java.lang.ref.WeakReference;
import java.text.NumberFormat;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

import static android.app.Notification.VISIBILITY_PUBLIC;
import static android.app.PendingIntent.FLAG_IMMUTABLE;
import static android.app.PendingIntent.FLAG_UPDATE_CURRENT;
import static android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_NONE;
import static android.os.Build.VERSION.SDK_INT;

public final class WigleService extends Service {
    private static final int NOTIFICATION_ID = 1;

    // NOTIFICATION_CHANNEL_ID must be updated for any channel change to take effect on an
    // existing device.
    public static final String NOTIFICATION_CHANNEL_ID = "wigle_notification_9";

    private GuardThread guardThread;
    private final AtomicBoolean done = new AtomicBoolean( false );
    private Bitmap largeIcon = null;
    private RemoteViews smallRemoteViews;

    // Binder given to clients
    private final IBinder wigleServiceBinder = new WigleServiceBinder(this);
    private final NumberFormat countFormat = NumberFormat.getIntegerInstance();

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
            Logging.info("GuardThread done");
        }
    }

    /**
     * Class used for the client Binder.  Because we know this service always
     * runs in the same process as its clients, we don't need to deal with IPC.
     */
    static class WigleServiceBinder extends Binder {
        //ALIBI: using WeakRef instead of WiGLEService.this because it leaks.
        // see https://stackoverflow.com/questions/3243215/how-to-use-weakreference-in-java-and-android-development
        WeakReference<WigleService> service;
        public WigleServiceBinder(final WigleService wigleService) {
            this.service = new WeakReference<>(wigleService);
        }

        WigleService getService() {
            // Return this instance of LocalService so clients can call public methods
            return service.get();
        }
    }

    private void setDone() {
        done.set( true );
        guardThread.interrupt();
    }

    @Override
    public IBinder onBind( final Intent intent ) {
        Logging.info( "service: onbind. intent: " + intent );
        return wigleServiceBinder;
    }

    @Override
    public void onRebind( final Intent intent ) {
        Logging.info( "service: onRebind. intent: " + intent );
        super.onRebind( intent );
    }

    @Override
    public boolean onUnbind( final Intent intent ) {
        Logging.info( "service: onUnbind. intent: " + intent );
        shutdownNotification();
        stopSelf();
        return super.onUnbind( intent );
    }

    /**
     * This is called if the user force-kills the app
     */
    @Override
    public void onTaskRemoved(Intent rootIntent) {
        Logging.info("service: onTaskRemoved.");
        if (! done.get()) {
            final MainActivity mainActivity = MainActivity.getMainActivity();
            if (mainActivity != null) {
                mainActivity.finishSoon();
            }
            setDone();
        }
        shutdownNotification();
        stopSelf();
        super.onTaskRemoved(rootIntent);
        Logging.info("service: onTaskRemoved complete.");
    }

    @Override
    public void onCreate() {
        Logging.info( "service: onCreate" );

        setupNotification();

        // don't use guard thread
        guardThread = new GuardThread();
        guardThread.start();
        super.onCreate();
    }

    @Override
    public void onDestroy() {
        Logging.info( "service: onDestroy" );
        // Make sure our notification is gone.
        shutdownNotification();
        setDone();
        super.onDestroy();
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        Logging.info( "service: onLowMemory" );
    }

    //This is the old onStart method that will be called on the pre-2.0
    //platform.  On 2.0 or later we override onStartCommand() so this
    //method will not be called.
    @SuppressWarnings("deprecation")
    @Override
    public void onStart( Intent intent, int startId ) {
        Logging.info( "service: onStart" );
        handleCommand( intent );
        setupNotification();
    }

    @Override
    public int onStartCommand( Intent intent, int flags, int startId ) {
        Logging.info( "service: onStartCommand" );
        handleCommand( intent );
        setupNotification();
        // We want this service to continue running until it is explicitly
        // stopped, so return sticky.
        return Service.START_STICKY;
    }

    private void handleCommand( Intent intent ) {
        Logging.info( "service: handleCommand: intent: " + intent );
        setupNotification();
    }

    private void shutdownNotification() {
        stopForeground(true);
    }

    public void setupNotification() {
        try {
            if (!done.get()) {
                final long when = System.currentTimeMillis();
                final Context context = getApplicationContext();
                final String title = context.getString(R.string.wigle_service);

                final Intent notificationIntent = new Intent(this, MainActivity.class);
                final int flags = SDK_INT >= Build.VERSION_CODES.S?(FLAG_UPDATE_CURRENT | FLAG_IMMUTABLE): FLAG_UPDATE_CURRENT;
                final PendingIntent contentIntent = PendingIntent.getActivity(this, 0, notificationIntent, flags);
                final long dbNets = ListFragment.lameStatic.dbNets;
                String text = context.getString(R.string.list_waiting_gps);

                String distString = "";
                String distStringShort = "";
                String wrappedDistString = "";
                SharedPreferences prefs = getSharedPreferences(PreferenceKeys.SHARED_PREFS, 0);
                if (prefs != null) {
                    final Configuration conf = getResources().getConfiguration();
                    Locale locale = null;
                    if (null != conf && null != conf.getLocales()) {
                        locale = conf.getLocales().get(0);
                    }
                    if (null == locale) {
                        locale = Locale.US;
                    }
                    NumberFormat longDistNumberFormat = NumberFormat.getNumberInstance(locale);
                    longDistNumberFormat.setMaximumFractionDigits(2);
                    NumberFormat shortDistNumberFormat = NumberFormat.getNumberInstance(locale);
                    shortDistNumberFormat.setMaximumFractionDigits(0);

                    final float dist = prefs.getFloat(PreferenceKeys.PREF_DISTANCE_RUN, 0f);
                    distString = UINumberFormat.metersToString(prefs, longDistNumberFormat, this, dist, true);
                    distStringShort = UINumberFormat.metersToShortString(prefs, shortDistNumberFormat, this, dist);
                    wrappedDistString = " ("+ distString + ")";
                }
                if (dbNets > 0) {
                    long runNets = ListFragment.lameStatic.runNets + ListFragment.lameStatic.runBt;
                    long newNets = ListFragment.lameStatic.newNets;
                    text = context.getString(R.string.run) + ": " + runNets
                            + "  " + context.getString(R.string.new_word) + ": " + newNets
                            + "  " + context.getString(R.string.db) + ": " + dbNets
                            + wrappedDistString;
                }
                if (!MainActivity.isScanning(context)) {
                    text = context.getString(R.string.list_scanning_off) + " " + text;
                }
                if (largeIcon == null) {
                    largeIcon = BitmapFactory.decodeResource(getResources(), R.drawable.wiglewifi);
                }

                final Intent pauseSharedIntent = new Intent();
                pauseSharedIntent.setAction("net.wigle.wigleandroid.PAUSE");
                pauseSharedIntent.setClass(getApplicationContext(), net.wigle.wigleandroid.listener.ScanControlReceiver.class);

                final MainActivity ma = MainActivity.getMainActivity();
                Notification notification = null;

                if (null != ma) {
                    final PendingIntent pauseIntent = PendingIntent.getBroadcast(MainActivity.getMainActivity(), 0, pauseSharedIntent, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_CANCEL_CURRENT);
                    final Intent scanSharedIntent = new Intent();
                    scanSharedIntent.setAction("net.wigle.wigleandroid.SCAN");
                    scanSharedIntent.setClass(getApplicationContext(), net.wigle.wigleandroid.listener.ScanControlReceiver.class);
                    final PendingIntent scanIntent = PendingIntent.getBroadcast(MainActivity.getMainActivity(), 0, scanSharedIntent, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_CANCEL_CURRENT);

                    final Intent uploadSharedIntent = new Intent();
                    uploadSharedIntent.setAction("net.wigle.wigleandroid.UPLOAD");
                    uploadSharedIntent.setClass(getApplicationContext(), net.wigle.wigleandroid.listener.UploadReceiver.class);
                    final PendingIntent uploadIntent = PendingIntent.getBroadcast(MainActivity.getMainActivity(), 0, uploadSharedIntent, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_CANCEL_CURRENT);
                    if (SDK_INT >= 31) {
                        notification = getNotification31(title, context, text,
                                ListFragment.lameStatic.newWifi, (ListFragment.lameStatic.runNets-ListFragment.lameStatic.runCells),
                                ListFragment.lameStatic.newCells, ListFragment.lameStatic.runCells,
                                ListFragment.lameStatic.newBt, ListFragment.lameStatic.runBt,
                                distString, distStringShort, dbNets,
                                MainActivity.isScanning(context)?context.getString(R.string.list_scanning_on):context.getString(R.string.list_scanning_off),
                                when, contentIntent, pauseIntent, scanIntent, uploadIntent);
                    } else if (SDK_INT >= Build.VERSION_CODES.O) {
                        notification = getNotification26(title, context, text, ListFragment.lameStatic.newWifi,
                                ListFragment.lameStatic.newCells, ListFragment.lameStatic.newBt,
                                distStringShort, when, contentIntent, pauseIntent,
                                scanIntent, uploadIntent);
                    } else {
                        notification = getNotification16(title, context, text, when, contentIntent, pauseIntent, scanIntent, uploadIntent);
                    }
                }

                if (null != notification) {
                    try {
                        if (isServiceForeground()) {
                            final NotificationManager notificationManager =
                                    (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
                            notificationManager.notify(NOTIFICATION_ID, notification);
                        }
                        else {
                            Logging.info("service startForeground");
                            startForeground(NOTIFICATION_ID, notification);
                        }
                    } catch (Exception ex) {
                        Logging.error("notification service error: ", ex);
                    }
                } else {
                    Logging.info("null notification - skipping startForeground");
                }
            }
        } catch (Exception ex) {
            Logging.error("trapped notification exception out outer level - ",ex);
        }
    }

    private boolean isServiceForeground() {
        if (SDK_INT < Build.VERSION_CODES.Q) {
            // no such thing as foreground back then
            return false;
        }
        final boolean isForeground = getForegroundServiceType() != FOREGROUND_SERVICE_TYPE_NONE;
        Logging.info("Service is foreground: " + isForeground);
        return isForeground;
    }

    private Notification getNotification16(final String title, final Context context, final String text, final long when,
                                            final PendingIntent contentIntent,
                                            final PendingIntent pauseIntent,
                                            final PendingIntent scanIntent,
                                            final PendingIntent uploadIntent) {
        @SuppressWarnings("deprecation") final NotificationCompat.Builder builder =
                new NotificationCompat.Builder(context);
        builder.setContentIntent(contentIntent);
        builder.setNumber((int) ListFragment.lameStatic.newNets);
        builder.setTicker(title);
        builder.setContentTitle(title);
        builder.setContentText(text);
        builder.setWhen(when);
        builder.setLargeIcon(largeIcon);
        builder.setSmallIcon(R.drawable.wiglewifi_small);
        builder.setOngoing(true);
        builder.setCategory("SERVICE");
        builder.setPriority(NotificationCompat.PRIORITY_LOW);
        builder.setVisibility(NotificationCompat.VISIBILITY_PUBLIC);
        builder.addAction(android.R.drawable.ic_media_pause, "Pause", pauseIntent);
        builder.addAction(android.R.drawable.ic_media_play, "Scan", scanIntent);
        builder.addAction(android.R.drawable.ic_menu_upload, "Upload", uploadIntent);

        try {
            //ALIBI: https://stackoverflow.com/questions/43123466/java-lang-nullpointerexception-attempt-to-invoke-interface-method-java-util-it
            return builder.build();
        } catch (NullPointerException npe) {
            Logging.error("NPE trying to build notification. " + npe.getMessage());
            return null;
        }

    }

    @RequiresApi(Build.VERSION_CODES.O)
    private Notification getNotification26(final String title, final Context context, final String text,
                                           final long newWiFi,
                                           final long newCell,
                                           final long newBt, final String distStringShort,
                                           final long when, final PendingIntent contentIntent,
                                           final PendingIntent pauseIntent, final PendingIntent scanIntent,
                                           final PendingIntent uploadIntent) {
        // new notification channel
        if (SDK_INT >= Build.VERSION_CODES.O) {
            final NotificationManager notificationManager =
                    (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (notificationManager == null) return null;
            final NotificationChannel channel = new NotificationChannel(NOTIFICATION_CHANNEL_ID,
                    title, NotificationManager.IMPORTANCE_DEFAULT);
            channel.setSound(null, null); // turns off notification sound
            channel.setLockscreenVisibility(VISIBILITY_PUBLIC);
            notificationManager.createNotificationChannel(channel);

            this.smallRemoteViews = new RemoteViews(this.getApplicationContext().getPackageName(),R.layout.small_notification_content);
            smallRemoteViews.setTextViewText(R.id.wifi_new_notif_sm, UINumberFormat.counterFormat(newWiFi));
            smallRemoteViews.setTextViewText(R.id.cell_new_notif_sm, UINumberFormat.counterFormat(newCell));
            smallRemoteViews.setTextViewText(R.id.bt_new_notif_sm, UINumberFormat.counterFormat(newBt));
            smallRemoteViews.setTextViewText(R.id.dist_notif_sm, distStringShort);

            final Notification.Builder builder = new Notification.Builder(context, NOTIFICATION_CHANNEL_ID);
            builder.setContentIntent(contentIntent);
            builder.setNumber((int) ListFragment.lameStatic.newNets);
            builder.setTicker(title);
            builder.setContentTitle(title);
            builder.setTicker(title);
            builder.setContentTitle(title);
            builder.setContentText(text);
            builder.setWhen(when);
            builder.setSmallIcon(R.drawable.ic_w_logo_simple);
            builder.setOngoing(true);
            builder.setCategory("SERVICE");
            builder.setVisibility(VISIBILITY_PUBLIC);
            builder.setCustomContentView(smallRemoteViews);
            builder.setStyle(new Notification.DecoratedCustomViewStyle());
            builder.setColorized(true);
            builder.setOnlyAlertOnce(true); //ALIBI: prevent multiple badge notification

            //TODO: figure out how to update notification actions on exec, then we can show relevant
            if (MainActivity.isScanning(getApplicationContext())) {
                Notification.Action pauseAction = new Notification.Action.Builder(
                        Icon.createWithResource(this, android.R.drawable.ic_media_pause),
                        "Pause", pauseIntent)
                        .build();
                builder.addAction(pauseAction);
            } else {
                Notification.Action scanAction = new Notification.Action.Builder(
                        Icon.createWithResource(this, android.R.drawable.ic_media_play),
                        "Scan", scanIntent)
                        .build();
                builder.addAction(scanAction);
            }
            Notification.Action ulAction = new Notification.Action.Builder(
                    Icon.createWithResource(this, android.R.drawable.ic_menu_upload),
                    "Upload", uploadIntent)
                    .build();
            builder.addAction(ulAction);

            return builder.build();
        }
        return null;
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private Notification getNotification31(final String title, final Context context, final String text,
                                           final long newWiFi, final long runTotalWiFi,
                                           final long newCell, final long runTotalCell,
                                           final long newBt, final long runTotalBt,
                                           final String distString, final String distStringShort,
                                           final long dbNets, final String status,
                                           final long when, final PendingIntent contentIntent,
                                           final PendingIntent pauseIntent, final PendingIntent scanIntent,
                                           final PendingIntent uploadIntent) {
        final NotificationManager notificationManager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (notificationManager == null) return null;

        final NotificationChannel channel = new NotificationChannel(NOTIFICATION_CHANNEL_ID,
                title, NotificationManager.IMPORTANCE_DEFAULT);
        channel.setSound(null, null); // turns off notification sound
        channel.setLockscreenVisibility(VISIBILITY_PUBLIC);
        notificationManager.createNotificationChannel(channel);

        RemoteViews bigRemoteViews = new RemoteViews(this.getApplicationContext().getPackageName(), R.layout.big_notification_content);
        bigRemoteViews.setTextViewText(R.id.wifi_new_notif, UINumberFormat.counterFormat(newWiFi));
        bigRemoteViews.setTextViewText(R.id.wifi_total_notif, UINumberFormat.counterFormat(runTotalWiFi));
        bigRemoteViews.setTextViewText(R.id.cell_new_notif, UINumberFormat.counterFormat(newCell));
        bigRemoteViews.setTextViewText(R.id.cell_total_notif, UINumberFormat.counterFormat(runTotalCell));
        bigRemoteViews.setTextViewText(R.id.bt_new_notif, UINumberFormat.counterFormat(newBt));
        bigRemoteViews.setTextViewText(R.id.bt_total_notif, UINumberFormat.counterFormat(runTotalBt));
        bigRemoteViews.setTextViewText(R.id.dist_notif,distString);
        bigRemoteViews.setTextViewText(R.id.db_total_notif, countFormat.format(dbNets));

        this.smallRemoteViews = new RemoteViews(this.getApplicationContext().getPackageName(),R.layout.small_notification_content);
        smallRemoteViews.setTextViewText(R.id.wifi_new_notif_sm, UINumberFormat.counterFormat(newWiFi));
        smallRemoteViews.setTextViewText(R.id.cell_new_notif_sm, UINumberFormat.counterFormat(newCell));
        smallRemoteViews.setTextViewText(R.id.bt_new_notif_sm, UINumberFormat.counterFormat(newBt));
        smallRemoteViews.setTextViewText(R.id.dist_notif_sm, distStringShort);

        final Notification.Builder builder = new Notification.Builder(context, NOTIFICATION_CHANNEL_ID);
        builder.setContentIntent(contentIntent)
                .setTicker(title)
                .setContentTitle(title)
                .setContentText(text)
                .setWhen(when)
                .setSmallIcon(R.drawable.ic_w_logo_simple)
                .setOngoing(true)
                .setCategory("SERVICE")
                .setSubText(status) //new
                .setCustomBigContentView(bigRemoteViews) //new
                .setCustomContentView(smallRemoteViews) //new
                .setVisibility(VISIBILITY_PUBLIC)
                .setOnlyAlertOnce(true) //ALIBI: prevent multiple badge notification
                .setStyle(new Notification.DecoratedCustomViewStyle())
                .setColorized(true);

        if (MainActivity.isScanning(getApplicationContext())) {
            Notification.Action pauseAction = new Notification.Action.Builder(
                    Icon.createWithResource(this, android.R.drawable.ic_media_pause),
                    "Pause", pauseIntent)
                    .build();
            builder.addAction(pauseAction);
        } else {
            Notification.Action scanAction = new Notification.Action.Builder(
                    Icon.createWithResource(this, android.R.drawable.ic_media_play),
                    "Scan", scanIntent)
                    .build();
            builder.addAction(scanAction);
        }
        Notification.Action ulAction = new Notification.Action.Builder(
                Icon.createWithResource(this, android.R.drawable.ic_menu_upload),
                "Upload", uploadIntent)
                .build();
        builder.addAction(ulAction);

        return builder.build();
    }

}
