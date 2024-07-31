package net.wigle.wigleandroid.model;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * convenience internal class for logging time and rssi for local site mapping
 * intended for use in KML export of surveys
 * @author rksh
 */
public class Observation {
    private static DateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ");
    private int rssi;
    private String formattedTime;

    private double latitude;

    private double longitude;

    private double elevationMeters;

    public Observation(final int rssi, final double latitude, final double longitude, final double elevationMeters) {
        this.rssi = rssi;
        this.latitude = latitude;
        this.longitude = longitude;
        this.elevationMeters = elevationMeters;
        this.formattedTime = sdf.format(new Date());
    }

    public int getRssi() {
        return rssi;
    }

    public String getFormattedTime() {
        return formattedTime;
    }

    public double getLatitude() {
        return latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public double getElevationMeters() {
        return elevationMeters;
    }
}
