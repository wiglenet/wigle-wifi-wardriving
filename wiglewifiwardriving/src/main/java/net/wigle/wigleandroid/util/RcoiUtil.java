package net.wigle.wigleandroid.util;

import android.net.wifi.ScanResult;
import android.os.Build;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * RCOIs-handling logic (refactored from WifiReceiver
 */
public class RcoiUtil {
    public static final int NIBBLE_MASK = 0x0f;
    public static final int BYTE_MASK = 0xff;

    public static final int EID_ROAMING_CONSORTIUM = 111;

    /**
     *  get any Roaming Consortium Organizational identifiers from beacon and concatenate
     *  @param ie ScanResult.InformationElement
     *  @return a string of concatenated RCOIS with " " delimiter
    */
    public static String getConcatenatedRcois (ScanResult.InformationElement ie) {
        String concatenatedRcois = null;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (ie.getId() != EID_ROAMING_CONSORTIUM) {
                throw new IllegalArgumentException("Element id is not ROAMING_CONSORTIUM, : "
                        + ie.getId());
            }
            // RCOI length handling from https://android.googlesource.com/platform/frameworks/opt/net/wifi/+/6f5af9b7f69b15369238bd2642c46638ba1f0255/service/java/com/android/server/wifi/util/InformationElementUtil.java#206

            // Roaming Consortium (OI) element format defined in IEEE 802.11 clause 9.4.2.95
            // ElementID (1 Octet), Length (1 Octet), Number of OIs (1 Octet), OI #1 and #2 Lengths (1 Octet), OI#1 (variable), OI#2 (variable), OI#3 (variable)
            // where 1 octet "OI #1 and #2 Length" comprises: OI#1 Length [B0-B3], OI#2 Length [B4-B7]
            ByteBuffer data = ie.getBytes().order(ByteOrder.LITTLE_ENDIAN);
            data.get(); // anqpOICount
            int oi12Length = data.get() & BYTE_MASK;
            int oi1Length = oi12Length & NIBBLE_MASK;
            int oi2Length = (oi12Length >>> 4) & NIBBLE_MASK;
            int oi3Length = ie.getBytes().limit() - 2 - oi1Length - oi2Length;

            if (oi1Length > 0) {
                final long rcoiInteger = getInteger(data, ByteOrder.BIG_ENDIAN, oi1Length, 0);
                concatenatedRcois = formatRcoi(rcoiInteger);
            }
            if (oi2Length > 0) {
                final long rcoiInteger = getInteger(data, ByteOrder.BIG_ENDIAN, oi2Length, oi1Length);
                concatenatedRcois += " " + formatRcoi(rcoiInteger);
            }
            if (oi3Length > 0) {
                final long rcoiInteger = getInteger(data, ByteOrder.BIG_ENDIAN, oi3Length, oi2Length + oi1Length);
                concatenatedRcois += " " + formatRcoi(rcoiInteger);
            }
        }
        // OpenRoaming example "5A03BA0000 BAA2D00000 BAA2D02000"
        return concatenatedRcois;
    }

    private static String formatRcoi(final long rcoi) {
        if (rcoi < 16777216) {
            return String.format("%1$06X", rcoi);
        }
        return String.format("%1$010X", rcoi);
    }

    public static long getInteger(ByteBuffer payload, ByteOrder bo, int size, int position) {
        payload.position(position + 2);
        long value = 0;
        if (bo == ByteOrder.LITTLE_ENDIAN) {
            final byte[] octets = new byte[size];
            payload.get(octets);

            for (int n = octets.length - 1; n >= 0; n--) {
                value = (value << Byte.SIZE) | (octets[n] & BYTE_MASK);
            }
        }
        else {
            for (int i = 0; i < size; i++) {
                final byte octet = payload.get();
                value = (value << Byte.SIZE) | (octet & BYTE_MASK);
            }
        }
        return value;
    }
}
