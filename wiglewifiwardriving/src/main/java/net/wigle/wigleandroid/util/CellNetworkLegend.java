package net.wigle.wigleandroid.util;

import android.content.Context;
import android.telephony.TelephonyManager;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class CellNetworkLegend {
    private static final Map<Integer, String> NETWORK_TYPE_LEGEND;
    static {
        Map<Integer, String> initMap = new HashMap<>();
        initMap.put(TelephonyManager.NETWORK_TYPE_1xRTT, "CDMA - 1xRTT");
        initMap.put(TelephonyManager.NETWORK_TYPE_CDMA, "CDMA"); //CDMA: Either IS95A or IS95B
        initMap.put(TelephonyManager.NETWORK_TYPE_EDGE, "EDGE");
        initMap.put(TelephonyManager.NETWORK_TYPE_EHRPD, "eHRPD");
        initMap.put(TelephonyManager.NETWORK_TYPE_EVDO_0, "CDMA - EvDo rev. 0");
        initMap.put(TelephonyManager.NETWORK_TYPE_EVDO_A, "CDMA - EvDo rev. A");
        initMap.put(TelephonyManager.NETWORK_TYPE_EVDO_B, "CDMA - EvDo rev. B");
        initMap.put(TelephonyManager.NETWORK_TYPE_GPRS, "GPRS");
        initMap.put(TelephonyManager.NETWORK_TYPE_GSM, "GSM");
        initMap.put(TelephonyManager.NETWORK_TYPE_HSDPA, "HSDPA");
        initMap.put(TelephonyManager.NETWORK_TYPE_HSPA, "HSPA");
        initMap.put(TelephonyManager.NETWORK_TYPE_HSPAP, "HSPA+");
        initMap.put(TelephonyManager.NETWORK_TYPE_HSUPA, "HSUPA");
        initMap.put(TelephonyManager.NETWORK_TYPE_IDEN, "iDEN");
        initMap.put(TelephonyManager.NETWORK_TYPE_IWLAN, "IWLAN");
        initMap.put(TelephonyManager.NETWORK_TYPE_LTE, "LTE");
        initMap.put(TelephonyManager.NETWORK_TYPE_TD_SCDMA, "TD_SCDMA");
        initMap.put(TelephonyManager.NETWORK_TYPE_UMTS, "UMTS");
        initMap.put(TelephonyManager.NETWORK_TYPE_UNKNOWN, "UNKNOWN");

        NETWORK_TYPE_LEGEND = Collections.unmodifiableMap(initMap);
    }

    public static String getNetworkTypeName(TelephonyManager tele) {
        return NETWORK_TYPE_LEGEND.get(tele.getNetworkType());
    }

}
