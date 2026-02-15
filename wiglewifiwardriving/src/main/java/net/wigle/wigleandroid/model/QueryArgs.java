package net.wigle.wigleandroid.model;

import android.location.Address;

/**
 *  Search query arguments
 *  @author bobzilla, arkasha
 */
public class QueryArgs {
    private static final Double LOCAL_RANGE = 0.1d;
    private static final Double ONLINE_RANGE = 0.001d; //ALIBI: online DB coverage mandates tighter bounds.

    private Address address;
    private MapBounds locationBounds;
    private String ssid;
    private String bssid;
    private String cellOp;
    private String cellNet;
    private String cellId;
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
        if (null != address) {
            //TODO: eventually get rid of this entirely in favor of bounds
            final double centerLat = address.getLatitude();
            final double centerLon = address.getLongitude();
            final Double range = searchWiGLE?ONLINE_RANGE:LOCAL_RANGE;
            locationBounds = new MapBounds(new LatLng(centerLat-range, centerLon-range), new LatLng(centerLat+range, centerLon+range));
        }
        this.address = address;
    }

    public MapBounds getLocationBounds() {
        return locationBounds;
    }

    public void setLocationBounds(MapBounds locationBounds) {
        this.locationBounds = locationBounds;
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

    public String getCellOp() {
        return cellOp;
    }

    public void setCellOp(String cellOp) {
        this.cellOp = cellOp;
    }

    public String getCellNet() {
        return cellNet;
    }

    public void setCellNet(String cellNet) {
        this.cellNet = cellNet;
    }

    public String getCellId() {
        return cellId;
    }

    public void setCellId(String cellId) {
        this.cellId = cellId;
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
        if (null != cellOp) {
            b.append(" cell op: ").append(cellOp);
        }
        if (null != cellNet) {
            b.append(" bssid: ").append(cellNet);
        }
        if (null != cellId) {
            b.append(" cell ID: ").append(cellId);
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
