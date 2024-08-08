package net.wigle.wigleandroid.listener;

import android.location.Location;

public interface WiFiScanUpdater {
    void handleWiFiSeen(final String bssid, final Integer rssi, Location location);
}
