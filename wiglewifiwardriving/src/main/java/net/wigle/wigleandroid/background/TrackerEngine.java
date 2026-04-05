package net.wigle.wigleandroid.background;

import android.content.Context;
import android.content.SharedPreferences;
import android.location.Location;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.os.Build;
import androidx.core.app.NotificationCompat;

import net.wigle.wigleandroid.MainActivity;
import net.wigle.wigleandroid.R;
import net.wigle.wigleandroid.model.Network;
import net.wigle.wigleandroid.util.Logging;
import net.wigle.wigleandroid.util.PreferenceKeys;

import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Evaluates whether an unknown radio beacon (WiFi, BT, BLE) is following the user over time and distance.
 * Specifically distinguishes between persistent high-strength signals (likely innocent) 
 * and varying, sporadic signals (suspect followers).
 */
public class TrackerEngine {

    private static final long DEFAULT_TIME_THRESHOLD_MS = 15 * 60 * 1000L; // 15 mins
    private static final float DEFAULT_DIST_THRESHOLD_M = 500.0f; // 500 meters
    private static final int LEVEL_VARIANCE_THRESHOLD = 15; // dBm variance to be considered "varying"
    private static final long SPORADIC_GAP_MS = 2 * 60 * 1000L; // 2 minute gap considered "sporadic"

    private static class TrackerObservation {
        final long firstSeenTime;
        final Location firstLocation;
        final String ssid;
        final String type;
        
        long lastSeenTime;
        long lastObservationTime;
        int observationCount = 0;
        int maxLevel = Integer.MIN_VALUE;
        int minLevel = Integer.MAX_VALUE;
        int sporadicCount = 0;

        TrackerObservation(long time, Location loc, String ssid, String type, int level) {
            this.firstSeenTime = time;
            this.firstLocation = loc;
            this.ssid = ssid;
            this.type = type;
            this.lastObservationTime = time;
            update(time, level);
        }

        void update(long time, int level) {
            if (time - this.lastObservationTime > SPORADIC_GAP_MS) {
                this.sporadicCount++;
            }
            this.lastObservationTime = time;
            this.lastSeenTime = time;
            this.observationCount++;
            if (level > maxLevel) maxLevel = level;
            if (level < minLevel) minLevel = level;
        }

        int getLevelRange() {
            return (maxLevel == Integer.MIN_VALUE || minLevel == Integer.MAX_VALUE) ? 0 : maxLevel - minLevel;
        }
    }

    private final Map<String, TrackerObservation> trackingCache = new ConcurrentHashMap<>();
    private final Set<String> alreadyAlerted = Collections.synchronizedSet(new HashSet<>());
    private final Context context;
    private final SharedPreferences prefs;

    public TrackerEngine(Context context, SharedPreferences prefs) {
        this.context = context.getApplicationContext();
        this.prefs = prefs;
    }

    public void evaluateNetwork(final Network network, final Location currentLocation) {
        if (!prefs.getBoolean(PreferenceKeys.PREF_ENABLE_TRACKER_ALERTS, false)) {
            return;
        }
        if (currentLocation == null || network == null) {
            return;
        }

        final String bssid = network.getBssid();
        if (bssid == null || alreadyAlerted.contains(bssid)) {
            return;
        }

        // Check if MAC is in Alert or Block list (Known/Ignored devices)
        MainActivity.State state = MainActivity.getStaticState();
        if (state != null) {
            if (state.bssidAlertList != null && state.bssidAlertList.reset(bssid).matches()) {
                return; // Handled by known device alerts
            }
            if (state.bssidLogExclusions != null && state.bssidLogExclusions.reset(bssid).matches()) {
                return; // Ignored device
            }
        }

        final int currentLevel = network.getLevel();
        TrackerObservation obs = trackingCache.get(bssid);
        if (obs == null) {
            // First time seeing this device
            trackingCache.put(bssid, new TrackerObservation(currentLocation.getTime(), currentLocation, 
                network.getSsid(), network.getType().getCode(), currentLevel));
        } else {
            obs.update(currentLocation.getTime(), currentLevel);

            // We have seen it before, evaluate time and distance
            long timeThreshold = prefs.getLong(PreferenceKeys.PREF_TRACKER_TIME_THRESHOLD, DEFAULT_TIME_THRESHOLD_MS);
            float distThreshold = prefs.getFloat(PreferenceKeys.PREF_TRACKER_DIST_THRESHOLD, DEFAULT_DIST_THRESHOLD_M);

            long timeDelta = currentLocation.getTime() - obs.firstSeenTime;
            float distDelta = currentLocation.distanceTo(obs.firstLocation);

            if (timeDelta > timeThreshold && distDelta > distThreshold) {
                // Heuristic: If it's persistent and high signal, it's likely "innocent" (traveling with you).
                // If it varies significantly or is sporadic, it's "suspect".
                boolean varying = obs.getLevelRange() > LEVEL_VARIANCE_THRESHOLD;
                boolean sporadic = obs.sporadicCount > 0;
                
                // Suspect if it varies significantly OR has been seen sporadically over a large distance
                if (varying || sporadic) {
                    Logging.info("Suspect Tracker Alert! BSSID: " + bssid + " TimeDelta: " + timeDelta 
                        + " DistDelta: " + distDelta + " Range: " + obs.getLevelRange() + " Sporadic: " + obs.sporadicCount);
                    triggerAlert(network, obs, varying, sporadic);
                    alreadyAlerted.add(bssid);
                } else {
                    // It's traveling with us but very stable/persistent. 
                    // We might still want to know, but we'll log it as "Innocent/Constant" for now.
                    Logging.info("Persistent Constant Signal (Innocent?): " + bssid + " Range: " + obs.getLevelRange());
                }
            }
        }
    }

    private void triggerAlert(Network network, TrackerObservation obs, boolean varying, boolean sporadic) {
        String title = "Suspect Beacon Following You";
        StringBuilder text = new StringBuilder("Device ");
        text.append(network.getSsid() != null && !network.getSsid().isEmpty() ? network.getSsid() : network.getBssid());
        text.append(" is following your location.");
        
        if (varying && sporadic) text.append(" (Sporadic & Varying signal)");
        else if (varying) text.append(" (Varying signal strength)");
        else if (sporadic) text.append(" (Sporadic presence)");

        NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (notificationManager == null) return;

        String channelId = "tracker_alerts";
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(channelId, "Tracker Alerts", NotificationManager.IMPORTANCE_HIGH);
            channel.setDescription("Alerts for suspect radio beacons following you");
            notificationManager.createNotificationChannel(channel);
        }

        Intent intent = new Intent(context, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, channelId)
                .setSmallIcon(R.drawable.wiglewifi_small_white)
                .setContentTitle(title)
                .setContentText(text.toString())
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(text.toString()));

        notificationManager.notify(network.getBssid().hashCode(), builder.build());
    }

    public void cullCache() {
        long now = System.currentTimeMillis();
        long maxAge = 24 * 60 * 60 * 1000L; // 24 hours
        Iterator<Map.Entry<String, TrackerObservation>> it = trackingCache.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, TrackerObservation> entry = it.next();
            if (now - entry.getValue().firstSeenTime > maxAge) {
                it.remove();
            }
        }
    }
    
    public void reset() {
        trackingCache.clear();
        alreadyAlerted.clear();
    }
}
