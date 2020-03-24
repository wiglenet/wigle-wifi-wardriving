package net.wigle.wigleandroid.util;

import android.location.Location;

/**
 * map a CSV String array to a Location object
 */
public class LocationCsv {
    public static Location fromWiGLEWirelessCsvLine(final String csvLine) {
        String[] parsed = CsvUtil.lineToArray(csvLine);
        Location loc = new Location("wigle");
        //"MAC,SSID,AuthMode,FirstSeen,Channel,RSSI,CurrentLatitude,CurrentLongitude,AltitudeMeters,AccuracyMeters,Type
        double latitude = Double.parseDouble(parsed[6]);
        double longitude = Double.parseDouble(parsed[7]);
        double altitudeMeters = Double.parseDouble(parsed[8]);
        float accuracyMeters = Float.parseFloat(parsed[9]);

        loc.setTime(CsvUtil.getUnixtimeForString(parsed[3]));
        loc.setLatitude(latitude);
        loc.setLongitude(longitude);
        loc.setAltitude(altitudeMeters);
        loc.setAccuracy(accuracyMeters);
        return loc;
    }
}
