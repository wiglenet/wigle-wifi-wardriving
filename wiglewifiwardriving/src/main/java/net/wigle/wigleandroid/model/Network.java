package net.wigle.wigleandroid.model;

import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import android.annotation.SuppressLint;
import android.net.wifi.ScanResult;
import com.google.android.gms.maps.model.LatLng;
import com.google.maps.android.clustering.ClusterItem;

/**
 * network data. not thread-safe.
 */
@SuppressLint("UseSparseArrays")
public final class Network implements ClusterItem {
    private final String bssid;
    private final String ssid;
    private final int frequency;
    private final String capabilities;
    private int level;
    private final Integer channel;
    private final String showCapabilities;
    private final int crypto;
    private final NetworkType type;
    private LatLng geoPoint;
    private boolean isNew;

    private String detail;
    private final long constructionTime = System.currentTimeMillis();

    private static final String BAR_STRING = " | ";
    private static final String DASH_STRING = " - ";
    private static final String WPA2_CAP = "[WPA2";
    private static final String WPA_CAP = "[WPA";
    private static final String WEP_CAP = "[WEP";

    // faster than enums
    public static final int CRYPTO_NONE = 0;
    public static final int CRYPTO_WEP = 1;
    public static final int CRYPTO_WPA = 2;
    public static final int CRYPTO_WPA2 = 3;

    private static final Map<Integer,Integer> freqToChan;
    static {
        Map<Integer,Integer> freqToChanTemp = new HashMap<>();
        for ( int i = 237; i <= 255; i++ ) {
            freqToChanTemp.put( 2312 + 5 * (i - 237), i );
        }

        for ( int i = 0; i <= 13; i++ ) {
            freqToChanTemp.put(2407 + (5 * i), i);
        }
        freqToChanTemp.put(2484, 14);

        freqToChanTemp.put(5170, 34);
        freqToChanTemp.put(5180, 36);
        freqToChanTemp.put(5190, 38);
        freqToChanTemp.put(5200, 40);
        freqToChanTemp.put(5210, 42);
        freqToChanTemp.put(5220, 44);
        freqToChanTemp.put(5230, 46);
        freqToChanTemp.put(5240, 48);
        freqToChanTemp.put(5260, 52);
        freqToChanTemp.put(5280, 56);
        freqToChanTemp.put(5300, 58);
        freqToChanTemp.put(5320, 60);

        freqToChanTemp.put(5500, 100);
        freqToChanTemp.put(5520, 104);
        freqToChanTemp.put(5540, 108);
        freqToChanTemp.put(5560, 112);
        freqToChanTemp.put(5570, 116);
        freqToChanTemp.put(5600, 120);
        freqToChanTemp.put(5620, 124);
        freqToChanTemp.put(5640, 128);
        freqToChanTemp.put(5660, 132);
        freqToChanTemp.put(5680, 136);
        freqToChanTemp.put(5700, 140);

        freqToChanTemp.put(5745, 149);
        freqToChanTemp.put(5765, 153);
        freqToChanTemp.put(5785, 157);
        freqToChanTemp.put(5805, 161);
        freqToChanTemp.put(5825, 165);

        freqToChan = Collections.unmodifiableMap( freqToChanTemp );
    }

    /**
     * convenience constructor
     * @param scanResult a result from a wifi scan
     */
    public Network( final ScanResult scanResult ) {
        this( scanResult.BSSID, scanResult.SSID, scanResult.frequency, scanResult.capabilities,
                scanResult.level, NetworkType.WIFI );
    }

    public Network( final String bssid, final String ssid, final int frequency, final String capabilities,
                    final int level, final NetworkType type ) {

        this.bssid = ( bssid == null ) ? "" : bssid.toLowerCase(Locale.US);
        this.ssid = ( ssid == null ) ? "" : ssid;
        this.frequency = frequency;
        this.capabilities = ( capabilities == null ) ? "" : capabilities;
        this.level = level;
        this.type = type;
        this.channel = freqToChan.get( frequency );

        if ( ! NetworkType.WIFI.equals( type ) ) {
            int semicolon = this.capabilities.lastIndexOf(";");
            if ( semicolon > 0 ) {
                this.showCapabilities = this.capabilities.substring(0, semicolon);
            }
            else {
                this.showCapabilities = this.capabilities;
            }
        }
        else if ( this.capabilities.length() > 16 ) {
            this.showCapabilities = this.capabilities.replaceAll("(\\[\\w+)\\-.*?\\]", "$1]");
        }
        else {
            this.showCapabilities = null;
        }

        if (this.capabilities.contains(WPA2_CAP)) {
            crypto = CRYPTO_WPA2;
        } else if (this.capabilities.contains(WPA_CAP)) {
            crypto = CRYPTO_WPA;
        }
        else if (this.capabilities.contains(WEP_CAP)) {
            crypto = CRYPTO_WEP;
        }
        else {
            crypto = CRYPTO_NONE;
        }
    }

    public String getBssid() {
        return bssid;
    }

    public String getSsid() {
        return ssid;
    }

    public int getFrequency() {
        return frequency;
    }

    public String getCapabilities() {
        return capabilities;
    }

    public String getShowCapabilities() {
        if ( showCapabilities == null ) {
            return capabilities;
        }
        return showCapabilities;
    }

    public int getLevel() {
        return level;
    }

    public NetworkType getType() {
        return type;
    }

    public Integer getChannel() {
        return channel;
    }

    public void setLevel( final int level ) {
        this.level = level;
    }

    public void setIsNew() {
        this.isNew = true;
    }

    public boolean isNew() {
        return isNew;
    }

    /**
     * get crypto category, one of CRYPTO_* defined in this class.
     * @return integer corresponding to an encryption category
     */
    public int getCrypto() {
        return crypto;
    }

    public long getConstructionTime() {
        return constructionTime;
    }

    public String getDetail() {
        if ( detail == null ) {
            final Integer chan = channel != null ? channel : frequency;
            final StringBuilder detailBuild = new StringBuilder( 40 );
            detailBuild.append( BAR_STRING ).append( bssid );
            detailBuild.append( DASH_STRING );
            if ( NetworkType.WIFI.equals(type) ) {
                detailBuild.append( chan );
            }
            else {
                detailBuild.append( type );
            }

            detailBuild.append( DASH_STRING ).append( getShowCapabilities() );
            detail = detailBuild.toString();
        }

        return detail;
    }

    public void setLatLng(LatLng geoPoint) {
        this.geoPoint = geoPoint;
    }

    public LatLng getLatLng() {
        return geoPoint;
    }

    public String getOui(final OUI oui) {
        String retval = "";
        final String lookup = getBssid().replace(":", "").toUpperCase();
        if (oui != null && lookup.length() >= 9) {
            retval = oui.getOui(lookup.substring(0, 9));
            if (retval == null) retval = oui.getOui(lookup.substring(0, 7));
            if (retval == null) retval = oui.getOui(lookup.substring(0, 6));
        }
        return retval == null ? "" : retval;
    }

    @Override
    public LatLng getPosition() {
        return geoPoint;
    }

    @Override
    public int hashCode() {
        return bssid.hashCode();
    }

    @Override
    public boolean equals(final Object other) {
        if (other instanceof Network) {
            final Network o = (Network) other;
            return bssid.equals(o.bssid);
        }
        return false;
    }

}
