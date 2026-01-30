package net.wigle.wigleandroid.model.api;

import net.wigle.wigleandroid.model.LatLng;
import net.wigle.wigleandroid.model.Network;
import net.wigle.wigleandroid.model.NetworkType;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public class BtSearchResponse {
    private boolean success;
    private List<BtNetwork> results;

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public List<BtNetwork> getResults() {
        return results;
    }

    public void setResults(List<BtNetwork> results) {
        this.results = results;
    }

    public class BtNetwork {
        private String netid;
        private Double trilat;
        private Double trilong;
        private String name;
        private String type;
        private String[] capabilities;
        private Integer device;

        public String getNetid() {
            return netid;
        }

        public void setNetid(String netid) {
            this.netid = netid;
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

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public String[] getCapabilities() {
            return capabilities;
        }

        public void setCapabilities(String[] capabilities) {
            this.capabilities = capabilities;
        }

        public Integer getDevice() {
            return device;
        }

        public void setDevice(Integer device) {
            this.device = device;
        }
    }
    public static Network asNetwork(BtNetwork wNet) {
        final LatLng l = new LatLng(wNet.getTrilat(),wNet.getTrilong());
        return new Network(wNet.getNetid(), wNet.getName(),
                wNet.getDevice(), Arrays.toString(wNet.getCapabilities()).toUpperCase(Locale.ROOT)+" [SEARCH]",
                0, NetworkType.valueOf(wNet.getType()), l);
    }
}
