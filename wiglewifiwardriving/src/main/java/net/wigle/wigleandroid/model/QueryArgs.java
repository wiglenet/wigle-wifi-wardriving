package net.wigle.wigleandroid.model;

import android.location.Address;

public class QueryArgs {
    private Address address;
    private String ssid;
    private String bssid;

    public Address getAddress() {
        return address;
    }
    public void setAddress(Address address) {
        this.address = address;
    }

    public String getSSID() {
        return ssid;
    }
    public void setSSID(String ssid) {
        this.ssid = ssid;
    }

    public String getBSSID() {
        return bssid;
    }
    public void setBSSID(String bssid) {
        this.bssid = bssid;
    }

    public String toString() {
        return "QueryArgs: address: " + address + ", ssid: " + ssid + ", bssid: " + bssid;
    }
}
