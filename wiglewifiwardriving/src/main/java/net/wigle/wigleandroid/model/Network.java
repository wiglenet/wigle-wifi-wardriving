package net.wigle.wigleandroid.model;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import android.annotation.SuppressLint;
import android.net.wifi.ScanResult;

import androidx.annotation.NonNull;

import com.google.android.gms.maps.model.LatLng;
import com.google.maps.android.clustering.ClusterItem;

/**
 * network data. not thread-safe.
 */
@SuppressLint("UseSparseArrays")
public final class Network implements ClusterItem {

    public static final List<Integer> wiFi24GHzChannelNumbers = Arrays.asList(1, 2, 3, 4, 5,
            6, 7, 8, 9, 10, 11, 12, 13, 14);
    public static final List<Integer> wiFi5GHzChannelNumbers = Arrays.asList(7, 8, 9, 11, 12,
            16, 32, 34, 36, 38, 40, 42, 44, 46, 48,
            50, 52, 54, 56, 58, 60, 62, 64, 68, 96,
            100, 102, 104, 106, 108, 110, 112, 114, 116, 118,
            120, 122, 124, 126, 128, 132, 134, 136, 138, 140,
            142, 144, 149, 151, 153, 155, 157, 159, 161, 163,
            165, 167, 169, 171, 173, 175, 177/*UNUSED: , 180, 182, 183,
            184, 187, 188, 189, 192, 196*/);
    public static final List<Integer> wiFi6GHzChannelNumbers = Arrays.asList(1, 5, 9, 13, 17,
            21, 25, 29, 33, 37, 41, 45, 49, 53, 57, 61,
            65, 69, 73, 77, 81, 85, 89, 93);
    public static final List<Integer> wiFi60GHzChannelNumbers = Arrays.asList(1, 2, 3, 4, 5,
            6);

    private final String bssid;
    private String ssid;
    private final String capabilities;
    private final String showCapabilities;
    private final int crypto;
    private NetworkType type;

    private int frequency;
    private int level;
    private Integer channel;
    private LatLng geoPoint;
    private boolean isNew;

    private String detail;
    private final long constructionTime = System.currentTimeMillis(); // again

    private static final String BAR_STRING = " | ";
    private static final String DASH_STRING = " - ";
    private static final String WPA3_CAP = "[WPA3";
    private static final String SAE_CAP = "SAE";
    private static final String SUITE_B_192_CAP = "EAP_SUITE_B_192";
    //private static final String OWE_CAP = "OWE";    //handles both OWE and OWE_TRANSITION
    //TODO: how we do distinguish between RSN-EAP-CCMP WPA2 and WPA3 implementations?
    private static final String WPA2_CAP = "[WPA2";
    private static final String RSN_CAP = "[RSN";
    private static final String WPA_CAP = "[WPA";
    private static final String WEP_CAP = "[WEP";

    // faster than enums
    public static final int CRYPTO_NONE = 0;
    public static final int CRYPTO_WEP = 1;
    public static final int CRYPTO_WPA = 2;
    public static final int CRYPTO_WPA2 = 3;
    public static final int CRYPTO_WPA3 = 4;

    public enum NetworkBand {
        WIFI_2_4_GHZ, WIFI_5_GHZ, WIFI_6_GHZ, WIFI_60_GHZ, CELL_2_3_GHZ, UNDEFINED
    }
    /**
     * convenience constructor
     * @param scanResult a result from a wifi scan
     */
    public Network( final ScanResult scanResult ) {
        this( scanResult.BSSID, scanResult.SSID, scanResult.frequency, scanResult.capabilities,
                scanResult.level,  NetworkType.WIFI, null);
    }

    public Network( final String bssid, final String ssid, final int frequency, final String capabilities,
                    final int level, final NetworkType type ) {
        this(bssid, ssid, frequency, capabilities, level, type, null);
    }

    public Network( final String bssid, final String ssid, final int frequency, final String capabilities,
        final int level, final NetworkType type, final LatLng latLng ) {

        this.bssid = ( bssid == null ) ? "" : bssid.toLowerCase(Locale.US);
        this.ssid = ( ssid == null ) ? "" : ssid;
        this.frequency = frequency;
        this.capabilities = ( capabilities == null ) ? "" : capabilities;
        this.level = level;
        this.type = type;
        if (this.type.equals(NetworkType.typeForCode("W"))) {
            this.channel = channelForWiFiFrequencyMhz(frequency);
        } else if (frequency != 0 && frequency != Integer.MAX_VALUE) {
            //TODO: this maps *FCN directly to channel; could xlate to band by network type here (2/2)
            /*if (NetworkType.GSM.equals(type)) {

            } else if (NetworkType.LTE.equals(type)) {

            } else if (NetworkType.WCDMA.equals(type)) {

            } else if (NetworkType.NR.equals(type)) {

            } else {
                channel = 0;
            }*/
            this.channel = frequency;
        } else {
            channel = null;
        }

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

        if (this.capabilities.contains(WPA3_CAP) || this.capabilities.contains(SUITE_B_192_CAP) || this.capabilities.contains(SAE_CAP)) {
            crypto = CRYPTO_WPA3;
        } else if (this.capabilities.contains(WPA2_CAP) || this.capabilities.contains(RSN_CAP)) {
            crypto = CRYPTO_WPA2;
        } else if (this.capabilities.contains(WPA_CAP)) {
            crypto = CRYPTO_WPA;
        } else if (this.capabilities.contains(WEP_CAP)) {
            crypto = CRYPTO_WEP;
        } else {
            crypto = CRYPTO_NONE;
        }
        this.geoPoint = latLng;
    }

    public String getTitle() {
        return ssid;
    }

    public String getSnippet() {
        return bssid;
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

    // Overloading for *FCN in GSM-derived networks for now. a subclass is probably more correct.
    public void setFrequency( final int frequency) {
        this.frequency = frequency;
        if (NetworkType.WIFI.equals(this.type)) {
            this.channel = channelForWiFiFrequencyMhz(frequency);
        } else if (frequency != 0 && frequency != Integer.MAX_VALUE) {
            //TODO: this maps *FCN directly to channel; could xlate to band by network type here (2/2)
            this.channel = frequency;
        }
    }

    public void setType(final NetworkType type) { this.type = type; }

    public void setSsid(final String ssid) {
        this.ssid = ssid;
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
            final StringBuilder detailBuild = new StringBuilder( 40 );
            if (!NetworkType.WIFI.equals(type)) {
                detailBuild.append(type).append(BAR_STRING);
            }
            detailBuild.append( getShowCapabilities() );
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

    @NonNull
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

    /*public static final int lteChannelforEarfcn() {
        BigDecimal[2] dlUlFrequs =
        return 0;
    }*/

    /**
     * credit and apologies to credit to - https://github.com/torvalds/linux/blob/ba31f97d43be41ca99ab72a6131d7c226306865f/net/wireless/util.c#L75
     * @param channel the channel number
     * @param band the band
     * @return the integer frequency (center)
     */
    public static Integer frequencyMHzForWiFiChannel(final int channel, final NetworkBand band) {
        NetworkBand bandGuess = band;
        //This isn't sustainable - in SDK 31 and up, android handles this for us, but we need to figure out how to get back to bands from previously incomplete records.
        if (band == NetworkBand.UNDEFINED.UNDEFINED) {
            if (channel <= 14) {
                bandGuess = NetworkBand.WIFI_2_4_GHZ;
            } else if (channel >= 237 && channel <= 255 ) {
                bandGuess = NetworkBand.CELL_2_3_GHZ;
            } else if (wiFi6GHzChannelNumbers.contains(channel)){
                bandGuess = NetworkBand.WIFI_6_GHZ;
            } else {
                bandGuess = NetworkBand.WIFI_5_GHZ;
            }
            //ALIBI: 60GHz contains no channels not aliased by WiFi 2.4GHz.
            //ALIBI: Since WiFi 5GHz has weird ambiguity/repurposed frequencies, it becomes a catch-all.
        }
        switch (bandGuess) {
            case WIFI_2_4_GHZ:
                if (channel == 14) {
                    return 2484;
                } else if (channel < 14) {
                    return (2407 + channel * 5);
                }
                return null;
            case WIFI_5_GHZ:
                if (channel >= 182 && channel <= 196) {
                    return 4000 + channel * 5;
                } else {
                    return 5000 + channel * 5;
                }
            case WIFI_6_GHZ:
                /* see 802.11ax D6.1 27.3.23.2 */
                if (channel == 2) {
                    return 5935;
                }
                if (channel <= 233) {
                    return 5950 + channel * 5;
                }
                return null;
            case WIFI_60_GHZ:
                if (channel < 7) {
                    return 56160 + channel * 2160;
                }
                return null;
            case CELL_2_3_GHZ:
                //ALIBI: cell network for backwards compat.
                if (channel > 236 && channel <= 255) {
                    return 2312 + 5 * (channel - 237);
                }
                return null;
            default:
                return null;
        }
    }

    /**
     * credit to int ieee80211_freq_khz_to_channel(u32 freq) - https://github.com/torvalds/linux/blob/ba31f97d43be41ca99ab72a6131d7c226306865f/net/wireless/util.c#L141
     * @param frequencyMHz the frequency in MHz for which to determine the channel
     * @return the channel value for the frequency
     */
    public static Integer channelForWiFiFrequencyMhz(final int frequencyMHz) {
        //NOTE: we're not storing band information here, we probably should be

        if (frequencyMHz == 2484) {
            return 14;
        } else if (frequencyMHz <= 2402) {
            // ALIBI: for backwards compat with cells
            return ((frequencyMHz - 2312) / 5) + 237;
        } else if (frequencyMHz < 2484) {
            return (frequencyMHz - 2407) / 5;
        } else if (frequencyMHz >= 4910 && frequencyMHz <= 4980) {
            return (frequencyMHz - 4000) / 5;
        } else if (frequencyMHz < 5925) {
            return (frequencyMHz - 5000) / 5;
        } else if (frequencyMHz == 5935) {
            return 2;
        } else if (frequencyMHz <= 45000) { /* DMG band lower limit */
            /* see 802.11ax D6.1 27.3.22.2 */
            return (frequencyMHz - 5950) / 5;
        } else if (frequencyMHz >= 58320 && frequencyMHz <= 70200) {
            return (frequencyMHz - 56160) / 2160;
        } else {
            return null;
        }
    }
}
