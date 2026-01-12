package net.wigle.wigleandroid.model.api;

import net.wigle.wigleandroid.model.LatLng;
import net.wigle.wigleandroid.model.Network;
import net.wigle.wigleandroid.model.NetworkType;

import java.util.List;
import java.util.Locale;

public class CellSearchResponse {
    private boolean success;
    private List<CellNetwork> results;

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public List<CellNetwork> getResults() {
        return results;
    }

    public void setResults(List<CellNetwork> results) {
        this.results = results;
    }

    public class CellNetwork {
        private String id;
        private Double trilat;
        private Double trilong;
        private String ssid;
        private String gentype;

        private String attributes;
        private Integer channel;

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public Double getTrilat() {
            return trilat;
        }

        public void setTrilat(Double trilat) {
            this.trilat = trilat;
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

        public String getGentype() {
            return gentype;
        }

        public void setGentype(String gentype) {
            this.gentype = gentype;
        }

        public String getAttributes() {
            return attributes;
        }

        public void setAttributes(String attributes) {
            this.attributes = attributes;
        }

        public Integer getChannel() {
            return channel;
        }

        public void setChannel(Integer channel) {
            this.channel = channel;
        }
    }
    public static Network asNetwork(CellNetwork wNet) {
        final LatLng l = new LatLng(wNet.getTrilat(),wNet.getTrilong());
        final String attr = wNet.getAttributes() == null ? "" : wNet.getAttributes().toUpperCase(Locale.ROOT);
        return new Network(wNet.getId(), wNet.getSsid(),
                0, attr + " [SEARCH]",
                0, NetworkType.valueOf(wNet.getGentype()) /*TODO: check*/, l);
    }
}
