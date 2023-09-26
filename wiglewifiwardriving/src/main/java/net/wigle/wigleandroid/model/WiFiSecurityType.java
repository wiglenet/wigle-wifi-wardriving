package net.wigle.wigleandroid.model;

import net.wigle.wigleandroid.R;

public enum WiFiSecurityType {
  ALL, NONE, WEP, WPA, WPA2, WPA3;

    public Integer getImageResourceId() {
        switch (this) {
            case NONE:
                return R.drawable.no_ico;
            case WEP:
                return R.drawable.wep_ico;
            case WPA:
                return R.drawable.wpa_ico;
            case WPA2:
                return R.drawable.wpa2_ico;
            case WPA3:
                return R.drawable.wpa3_ico;
            default:
                return R.drawable.cancel_button;
        }
    }

    public static String webParameterValue(WiFiSecurityType type) {
        switch (type) {
            case NONE:
                return "None";
            case WEP:
                return "WEP";
            case WPA:
                return "WPA";
            case WPA2:
                return "WPA2";
            case WPA3:
                return "WPA3";
            default:
                return null;
        }
    }
}
