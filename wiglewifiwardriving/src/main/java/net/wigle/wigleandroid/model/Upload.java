package net.wigle.wigleandroid.model;

/**
 * upload. not thread-safe.
 */
public final class Upload {
    private final String transid;
    private final long totalWifiGps;
    private final long totalCellGps;
    private final int percentDone;
    private final String status;
    private final long fileSize;

    public Upload(final String transid, final long totalWifiGps, final long totalCellGps,
                  final int percentDone, final String status, final long fileSize) {

        this.transid = transid;
        this.totalWifiGps = totalWifiGps;
        this.totalCellGps = totalCellGps;
        this.percentDone = percentDone;
        this.status = status;
        this.fileSize = fileSize;
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

    public int getPercentDone() {
        return percentDone;
    }

    public String getStatus() {
        return status;
    }

    public long getFileSize() {
        return fileSize;
    }
}
