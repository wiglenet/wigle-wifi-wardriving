package net.wigle.wigleandroid.model;

/**
 * upload. not thread-safe.
 */
public final class Upload {
    private final String transid;
    private final long totalWifiGps;
    private final long totalCellGps;

    public Upload(final String transid, final long totalWifiGps, final long totalCellGps) {

        this.transid = transid;
        this.totalWifiGps = totalWifiGps;
        this.totalCellGps = totalCellGps;
    }

    public String getTransid() {
        return transid;
    }

    public long getTotalWifiGps() {
        return totalWifiGps;
    }

    public long getTotalCellGps() {
        return totalCellGps;
    }
}
