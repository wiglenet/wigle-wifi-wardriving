package net.wigle.wigleandroid.model;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import android.annotation.SuppressLint;
import android.net.wifi.ScanResult;

import androidx.annotation.NonNull;

import com.google.maps.android.clustering.ClusterItem;

import net.wigle.wigleandroid.MainActivity;
import net.wigle.wigleandroid.util.Logging;

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
    private String capabilities;
    private final String showCapabilities;
    private final int crypto;
    private NetworkType type;

    private int frequency;
    private int level;
    private Long lastTime;
    private Integer channel;
    private LatLng geoPoint;
    private boolean isNew;

    private List<String> bleServiceUuids;
    private Integer bleMfgrId;
    private String bleMfgr;

    private Integer bleAddressType = null;

    private boolean passpoint;

    private String detail;
    private final long constructionTime = System.currentTimeMillis(); // again

    private static final String BAR_STRING = " | ";
    public static final String WPA3_CAP = "[WPA3";
    public static final String SAE_CAP = "SAE";
    public static final String SUITE_B_192_CAP = "EAP_SUITE_B_192";
    //private static final String OWE_CAP = "OWE";    //handles both OWE and OWE_TRANSITION
    //TODO: how we do distinguish between RSN-EAP-CCMP WPA2 and WPA3 implementations?
    public static final String WPA2_CAP = "[WPA2";
    public static final String RSN_CAP = "[RSN";
    public static final String WPA_CAP = "[WPA-";
    public static final String WEP_CAP = "[WEP";

    // faster than enums
    public static final int CRYPTO_NONE = 0;
    public static final int CRYPTO_WEP = 1;
    public static final int CRYPTO_WPA = 2;
    public static final int CRYPTO_WPA2 = 3;
    public static final int CRYPTO_WPA3 = 4;

    public enum CryptoType {
        None(CRYPTO_NONE), WEP(CRYPTO_WEP), WPA(CRYPTO_WPA), WPA2(CRYPTO_WPA2), WPA3(CRYPTO_WPA3);
        private final int value;

        CryptoType(final int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }

        @NonNull
        public String toString() {
            switch (value) {
                case CRYPTO_NONE:
                    return "None";
                case CRYPTO_WEP:
                    return "WEP";
                case CRYPTO_WPA:
                    return "WPA";
                case CRYPTO_WPA2:
                    return "WPA2";
                case CRYPTO_WPA3:
                    return "WPA3";
                default:
                    return "Unknown";
            }
        }
    }
    public enum NetworkBand {
        WIFI_2_4_GHZ, WIFI_5_GHZ, WIFI_6_GHZ, WIFI_60_GHZ, CELL_2_3_GHZ, UNDEFINED
    }

    private String concatenatedRcois;

    /**
     * convenience constructor, WiFi-specific
     * @param scanResult a result from a wifi scan
     */
    public Network( final ScanResult scanResult ) {
        this( scanResult.BSSID, scanResult.SSID, scanResult.frequency, scanResult.capabilities,
                scanResult.level,  NetworkType.WIFI, null, null, null, null, null, scanResult.isPasspointNetwork());
    }

    // load from CSV/observation list
    public Network( final String bssid, final String ssid, final int frequency, final String capabilities,
                    final int level, final NetworkType type) {
        this(bssid, ssid, frequency, capabilities, level, type, null, null, null, null, null, null);
    }

    // new Network, no location
    public Network( final String bssid, final String ssid, final int frequency, final String capabilities,
                    final int level, final NetworkType type, final List<String> bleServiceUuid16s, Integer bleMfgrId, final Long lastTime, final Integer bleAddressType) {
        this(bssid, ssid, frequency, capabilities, level, type, bleServiceUuid16s, bleMfgrId, null, lastTime, bleAddressType, null);
    }

    // for WiFiSearchResponse
    public Network( final String bssid, final String ssid, final int frequency, final String capabilities,
                    final int level, final NetworkType type, final LatLng latLng ) {
        this(bssid, ssid, frequency, capabilities, level, type, null, null, latLng, null, null, null);
    }

    private Network(final String bssid, final String ssid, final int frequency, final String capabilities,
                    final int level, final NetworkType type, final List<String> bleServiceUuid16s, Integer bleMfgrId,
                    final LatLng latLng, final Long lastTime, final Integer bleAddressType, final Boolean passpoint ) {
        this.bssid = ( bssid == null ) ? "" : bssid.toLowerCase(Locale.US);
        this.ssid = ( ssid == null ) ? "" : ssid;
        this.frequency = frequency;
        this.capabilities = ( capabilities == null ) ? "" : capabilities;
        this.level = level;
        this.type = type;
        if (null != passpoint && passpoint) {
            this.passpoint = true;
        } else {
            this.passpoint = false;
        }
        if (bleAddressType != null) {
            this.bleAddressType = bleAddressType;
        }
        if (null != lastTime && lastTime > 0L) {
            this.lastTime = lastTime;
        }
        if (bleMfgrId != null) this.bleMfgrId = bleMfgrId;
        if (NetworkType.WIFI.equals(this.type)) {
            this.channel = channelForWiFiFrequencyMhz(frequency);
        } else if (NetworkType.BLE.equals(this.type) || NetworkType.BT.equals(this.type)) {
            if (null != bleServiceUuid16s && !bleServiceUuid16s.isEmpty()) {
                this.bleServiceUuids = bleServiceUuid16s;
                bleMfgr = lookupMfgrByServiceUuid(bleServiceUuids.get(0));
            } else if (bleMfgrId != null) {
                bleMfgr = lookupMfgrByMfgrId(bleMfgrId);
            }
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

    /**
     * ClusterItem title
     * [delete this method for FOSS build]
     * @return the SSID of the network as title
     */
    public String getTitle() {
        return ssid;
    }

    /**
     * ClusterItem snippet
     * [delete this method for FOSS build]
     * @return the BSSID of the network as "snippet"
     */
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
    public void setCapabilities( final String capabilities ) {
        this.capabilities = capabilities;
    }

    public String getRcois() {
        return concatenatedRcois;
    }

    public String getRcoisOrBlank() {
        final String result = concatenatedRcois;
        return result == null ? "" : result;
    }

    public Long getLastTime() {
        return lastTime;
    }

    public void setRcois(final String concatenatedRcois) {
        this.concatenatedRcois = concatenatedRcois;
    }

    public boolean isPasspoint() {
        return this.passpoint;
    }

    // Overloading for *FCN in GSM-derived networks for now. a subclass is probably more correct.
    public void setFrequency( final int frequency) {
        this.frequency = frequency;
        if (NetworkType.WIFI.equals(this.type)) {
            this.channel = channelForWiFiFrequencyMhz(frequency);
        } else if (!NetworkType.BLE.equals(this.type) && !NetworkType.BT.equals(this.type) && frequency != 0 && frequency != Integer.MAX_VALUE) {
            //TODO: this maps *FCN directly to channel; could xlate to band by network type here (2/2)
            this.channel = frequency;
        }
    }

    public void setType(final NetworkType type) { this.type = type; }

    public void setSsid(final String ssid) {
        this.ssid = ssid;
    }

    public void addBleServiceUuid(final String uuid) {
        boolean newSuids = false;
        if (bleServiceUuids == null) {
            bleServiceUuids = new ArrayList<>();
            newSuids = true;
        }
        if (!bleServiceUuids.contains(uuid)) {
            bleServiceUuids.add(uuid);
        }
        if (newSuids && (bleMfgr == null || bleMfgr.isEmpty())) {
            bleMfgr = lookupMfgrByServiceUuid(bleServiceUuids.get(0));
        }
    }

    public void addBleMfgrId(Integer id) {
        bleMfgrId = id;
        if (bleMfgrId != null) {
            //ALIBI: in conjunction with addBleServiceUuid, Mfgr takes precedence over service UUID-derived name in this impl.
            bleMfgr = lookupMfgrByMfgrId(id);
        }
    }

    public Integer getBleAddressType() {
        return bleAddressType;
    }

    public void setBleAddressType(final Integer bleAddressType) {
        if (null != bleAddressType && (null == this.bleAddressType || bleAddressType > this.bleAddressType)) {
            this.bleAddressType = bleAddressType;
        }
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

    /**
     * try using a service UUID to lookup mfgr name
     * @param fullUuid the raw esrvice UUID to use
     * @return the string if found or null
     */
    public String lookupMfgrByServiceUuid(final String fullUuid) {
        //ALIBI: non-index-0 records seem to be secondary
        String uuid16Service = fullUuid.substring(4, 8);
        if (uuid16Service != null) {
            final MainActivity ma = MainActivity.getMainActivity();
            int mfgrIndex = Integer.parseInt(uuid16Service, 16);
            if (null != ma) {
                final String mfgrName = ma.getBleVendor(mfgrIndex);
                if (null != mfgrName) {
                    return mfgrName;
                }
            } else {
                Logging.error("null mfgr name: "+mfgrIndex);
            }
        }
        return null;
    }

    public String lookupMfgrByMfgrId(final Integer mfgrId) {
        //ALIBI: non-index-0 records seem to be secondary
        final MainActivity ma = MainActivity.getMainActivity();
        if (null != ma) {
            final String mfgrName = ma.getBleMfgr(mfgrId);
            if (null != mfgrName) {
                return  mfgrName;
            }
        } else {
            Logging.error("no main activity accessible.");
        }
        return null;
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
        if (NetworkType.BLE.equals(type)) {
            if (bleMfgr != null && !bleMfgr.isEmpty()) {
                return bleMfgr;
            }
        }
        final String lookup = getBssid().replace(":", "").toUpperCase(Locale.ROOT);
        if (oui != null && lookup.length() >= 9) {
            retval = oui.getOui(lookup.substring(0, 9));
            if (retval == null) retval = oui.getOui(lookup.substring(0, 7));
            if (retval == null) retval = oui.getOui(lookup.substring(0, 6));
        }
        return retval == null ? "" : retval;
    }

    public List<String> getBleServiceUuids() {
        return bleServiceUuids;
    }

    public String getBleServiceUuidsAsString() {
        final List<String> current = bleServiceUuids;
        return current == null ? "" : String.join(" ",current);
    }

    public Integer getBleMfgrId() {
        return bleMfgrId;
    }

    public int getBleMfgrIdAsInt() {
        final Integer current = bleMfgrId;
        return current == null ? 0 : current;
    }

    public String getBleMfgr() {
        return bleMfgr;
    }

    /**
     * ClusterItem contract position
     * [delete this method for FOSS build]
     */
    @NonNull
    @Override
    public com.google.android.gms.maps.model.LatLng getPosition() {
        if (null != geoPoint) {
            return new com.google.android.gms.maps.model.LatLng(geoPoint.latitude, geoPoint.longitude);
        } else {
            return new com.google.android.gms.maps.model.LatLng(0d, 0d);
        }
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
        if (band == NetworkBand.UNDEFINED) {
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
