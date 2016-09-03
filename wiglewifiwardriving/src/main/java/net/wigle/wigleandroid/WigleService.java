package net.wigle.wigleandroid;

import android.app.Notification;
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

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;

public final class WigleService extends Service {
    private static final int NOTIFICATION_ID = 1;

    private GuardThread guardThread;
    private final AtomicBoolean done = new AtomicBoolean( false );
    private Bitmap largeIcon = null;

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
        return 1;
    }

    private void handleCommand( Intent intent ) {
        MainActivity.info( "service: handleCommand: intent: " + intent );
    }

    private void shutdownNotification() {
        stopForegroundCompat( NOTIFICATION_ID );
    }

    private void setupNotification() {
        if ( ! done.get() ) {
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

            final NotificationCompat.Builder builder = new NotificationCompat.Builder(context);
            builder.setContentIntent(contentIntent);
            builder.setNumber((int)ListFragment.lameStatic.newNets);
            builder.setTicker(title);
            builder.setContentTitle(title);
            builder.setContentText(text);
            builder.setWhen(when);
            builder.setLargeIcon(largeIcon);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                builder.setSmallIcon(R.drawable.wiglewifi_small_white);
            }
            else {
                builder.setSmallIcon(R.drawable.wiglewifi_small);
            }
            builder.setOngoing(true);
            builder.setCategory("SERVICE");
            builder.setPriority(NotificationCompat.PRIORITY_LOW);
            builder.setVisibility(NotificationCompat.VISIBILITY_PUBLIC);

            final Uri uri = Uri.EMPTY;
            final Intent pauseSharedIntent = new Intent(Intent.ACTION_DELETE, uri, this, ShareActivity.class );
            final PendingIntent pauseIntent = PendingIntent.getActivity( this, 0, pauseSharedIntent, 0 );
            builder.addAction(R.drawable.wiglewifi_small_black_white, "Pause", pauseIntent);

            final Intent scanSharedIntent = new Intent(Intent.ACTION_INSERT, uri, this, ShareActivity.class );
            final PendingIntent scanIntent = PendingIntent.getActivity( this, 0, scanSharedIntent, 0 );
            builder.addAction(R.drawable.wiglewifi_small_black_white, "Scan", scanIntent);

            // final Intent uploadSharedIntent = new Intent(Intent.ACTION_SYNC, uri, this, ShareActivity.class );
            // final PendingIntent uploadIntent = PendingIntent.getActivity( this, 0, uploadSharedIntent, 0 );
            // builder.addAction(R.drawable.wiglewifi_small_black_white, "Upload", uploadIntent);

            final Notification notification = builder.build();
            startForegroundCompat( NOTIFICATION_ID, notification );
        }
    }

    void invokeMethod(Method method, Object[] args) {
        //noinspection TryWithIdenticalCatches
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
            mStartForegroundArgs[0] = id;
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
