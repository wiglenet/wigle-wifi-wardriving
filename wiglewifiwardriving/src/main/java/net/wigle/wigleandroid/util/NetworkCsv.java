package net.wigle.wigleandroid.util;

import net.wigle.wigleandroid.model.Network;
import net.wigle.wigleandroid.model.NetworkType;

/**
 * Map a CSV string array to a Network object
 */
public class NetworkCsv {
    public static Network fromWiGLEWirelessCsvLine(final String csvLine) {

        //"MAC,SSID,AuthMode,FirstSeen,Channel,RSSI,CurrentLatitude,CurrentLongitude,AltitudeMeters,AccuracyMeters,Type

        String[] parsed = CsvUtil.lineToArray(csvLine);
        final NetworkType type = NetworkType.valueOf(parsed[10]);
        Integer frequency = Integer.parseInt(parsed[4]);
        if (NetworkType.WIFI.equals(type)) {
            frequency = Network.frequencyMHzForWiFiChannel(frequency, Network.WiFiBand.UNDEFINED);
            if (null == frequency) {
                frequency = 0;
            }
        }
        //TODO: verify this conversion
        Double level = Double.parseDouble(parsed[5]);
        return new Network(parsed[0], parsed[1], frequency,
                parsed[2], level.intValue(), type);
    }
}
