package net.wigle.wigleandroid.model;

import java.util.HashMap;
import java.util.Map;

public enum NetworkType {
    WIFI("W"),
    GSM("G"),
    CDMA("C"),
    LTE("L"),
    WCDMA("D"),
    BT("B"),
    BLE("E"),
    NFC("N");

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
}
