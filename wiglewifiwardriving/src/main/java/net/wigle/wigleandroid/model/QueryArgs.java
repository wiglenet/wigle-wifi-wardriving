package net.wigle.wigleandroid.model;

import android.location.Address;

/**
 *  Search query arguments
 *  @author bobzilla, arkasha
 */
public class QueryArgs {
    private Address address;
    private String ssid;
    private String bssid;

    private NetworkFilterType type;

    private WiFiSecurityType crypto;

    public void setSearchWiGLE(boolean searchWiGLE) {
        this.searchWiGLE = searchWiGLE;
    }

    public boolean searchWiGLE() {
        return searchWiGLE;
    }

    private boolean searchWiGLE = false;

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

    public NetworkFilterType getType() {
        return type;
    }

    public void setType(NetworkFilterType type) {
        this.type = type;
    }

    public WiFiSecurityType getCrypto() {
        return crypto;
    }

    public void setCrypto(WiFiSecurityType crypto) {
        this.crypto = crypto;
    }

    public String toString() {
        StringBuilder b = new StringBuilder("QueryArgs:");
        if (null != address) {
            b.append(" address: ").append(address);
        }
        if (null != ssid) {
            b.append(" ssid: ").append(ssid);
        }
        if (null != bssid) {
            b.append(" bssid: ").append(bssid);
        }
        if (null != type) {
            b.append(" type: ").append(type.toString());
        }
        if (null != crypto) {
            b.append(" encryption: ").append(crypto.toString());
        }

        return b.toString();
    }
}
