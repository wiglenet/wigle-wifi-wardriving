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
    private DateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ");
    private int rssi;
    private String formattedTime;

    private double latitude;

    private double longitude;

    public Observation(final int rissi, final double latitude, final double longitude) {
        this.rssi = rssi;
        this.latitude = latitude;
        this.longitude = longitude;
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
}
