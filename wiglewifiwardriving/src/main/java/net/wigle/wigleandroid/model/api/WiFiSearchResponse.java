package net.wigle.wigleandroid.model.api;

import net.wigle.wigleandroid.model.LatLng;
import net.wigle.wigleandroid.model.Network;
import net.wigle.wigleandroid.model.NetworkType;

import java.util.List;
import java.util.Locale;

/**
 * WiGLE v2 API WiFi Search Response model object
 */
public class WiFiSearchResponse {
    private boolean success;
    private List<WiFiNetwork> results;

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public List<WiFiNetwork> getResults() {
        return results;
    }

    public void setResults(List<WiFiNetwork> results) {
        this.results = results;
    }

    //ALIBI: while these have some things in common w/ Network, we don't need all of that.
    public class WiFiNetwork {
        private String netid;
        private Double trilat;
        private Double trilong;
        private String ssid;
        private String encryption;
        private final String type = "WiFi";
        private int frequency;
        private Integer channel;

        public String getNetid() {
            return netid;
        }

        public void setNetid(String netid) {
            this.netid = netid;
        }

        public Double getTrilong() {
            return trilong;
        }

        public void setTrilong(Double trilong) {
            this.trilong = trilong;
        }

        public String getSsid() {
            return ssid;
        }

        public void setSsid(String ssid) {
            this.ssid = ssid;
        }

        public String getEncryption() {
            return encryption;
        }

        public void setEncryption(String encryption) {
            this.encryption = encryption;
        }

        public String getType() {
            return type;
        }

        public int getFrequency() {
            return frequency;
        }

        public void setFrequency(int frequency) {
            this.frequency = frequency;
        }

        public Integer getChannel() {
            return channel;
        }

        public void setChannel(Integer channel) {
            this.channel = channel;
        }

        public Double getTrilat() {
            return trilat;
        }

        public void setTrilat(Double trilat) {
            this.trilat = trilat;
        }
    }

    /**
     * Sometimes you need a {@link Network} that contains the information in a {@link WiFiNetwork}. ALIBI: avoids a rendering rewrite for network refactor.
     * @param wNet the WiFiNetwork instance
     * @return a Network instance with some assumptions. Capabilities will be [<encryption value> SEARCH]
     */
    public static Network asNetwork(WiFiNetwork wNet) {
        final LatLng l = new LatLng(wNet.getTrilat(),wNet.getTrilong());
        return new Network(wNet.getNetid(), wNet.getSsid(),
                wNet.getChannel(), "["+wNet.getEncryption().toUpperCase(Locale.ROOT)+" SEARCH]",
                0, NetworkType.WIFI, l);
    }

}
