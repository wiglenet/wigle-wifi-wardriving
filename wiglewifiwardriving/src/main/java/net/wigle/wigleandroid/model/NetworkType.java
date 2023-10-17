package net.wigle.wigleandroid.model;

import java.util.HashMap;
import java.util.Map;

public enum NetworkType {
    WIFI("W"),
    GSM("G"),
    CDMA("C"),
    LTE("L"),
    WCDMA("D"),
    NR("N"),
    BT("B"),
    BLE("E"),
    NFC("F");

    private static final Map<String,NetworkType> types = new HashMap<>();

    static {
        for( NetworkType type : NetworkType.values() ) {
            types.put( type.getCode(), type );
        }
    }

    private final String code;
    NetworkType(final String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }

    public static NetworkType typeForCode(final String code) {
        return types.get(code);
    }

    public static boolean isCellType (NetworkType type) {
        if (NetworkType.CDMA.equals(type) || NetworkType.GSM.equals(type) || NetworkType.LTE.equals(type) || NetworkType.WCDMA.equals(type) || NetworkType.NR.equals(type)) {
            return true;
        }
        return false;
    }

    public static boolean isGsmLike (NetworkType type) {
        if (NetworkType.GSM.equals(type) || NetworkType.LTE.equals(type) || NetworkType.WCDMA.equals(type) || NetworkType.NR.equals(type)) {
            return true;
        }
        return false;
    }

    public static boolean isBtType (NetworkType type) {
        if (NetworkType.BT.equals(type) || NetworkType.BLE.equals(type)) {
            return true;
        }
        return false;
    }

    public static final String channelCodeTypeForNetworkType(NetworkType type) {
        switch (type) {
            case GSM:
                return "ARFCN"  ;
            case LTE:
                return "EARFCN";
            case WCDMA:
                return "UARFCN";
            case NR:
                return "NRARFCN";
            default:
                return null;
        }
    }

}
