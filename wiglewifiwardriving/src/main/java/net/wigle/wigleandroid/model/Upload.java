package net.wigle.wigleandroid.model;

import com.google.gson.annotations.SerializedName;

/**
 * upload. not thread-safe.
 */
public final class Upload {
    private final String transid;
    @SerializedName("totalGps")
    private final long totalWifiGps;
    @SerializedName("total")
    private final long totalWifi;
    @SerializedName("btTotalGps")
    private final long totalBtGps;
    @SerializedName("btTotal")
    private final long totalBt;
    @SerializedName("genTotalGps")
    private final long totalCellGps;
    @SerializedName("genTotal")
    private final long totalCell;
    private final int percentDone;
    private String humanReadableStatus;
    private final long fileSize;
    private final String fileName;
    private Boolean uploadedFromLocal;
    private Boolean downloadedToLocal;
    private final Status status;
    private final long wait;

    public Upload(final String transid, final long totalWifiGps, final long totalBtGps, final long totalCellGps, final long totalWifi, final long totalBt, final long totalCell,
                  final int percentDone, final Status status, final long fileSize, final String fileName, final Boolean uploadedFromLocal, final Boolean downloadedToLocal, final long wait) {
        this.transid = transid;
        this.totalWifiGps = totalWifiGps;
        this.totalBtGps = totalBtGps;
        this.totalCellGps = totalCellGps;
        this.totalWifi = totalWifi;
        this.totalBt = totalBt;
        this.totalCell = totalCell;
        this.percentDone = percentDone;
        this.status = status;
        this.fileSize = fileSize;
        this.fileName = fileName;
        this.uploadedFromLocal = uploadedFromLocal;
        this.downloadedToLocal = downloadedToLocal;
        this.wait = wait;
    }

    public String getTransid() {
        return transid;
    }

    public long getTotalWifiGps() {
        return totalWifiGps;
    }

    public long getTotalBtGps() {
        return totalBtGps;
    }

    public long getTotalCellGps() {
        return totalCellGps;
    }

    public long getTotalWifi() {
        return totalWifi;
    }

    public long getTotalBt() {
        return totalBt;
    }

    public long getTotalCell() {
        return totalCell;
    }

    public int getPercentDone() {
        return percentDone;
    }

    public String getHumanReadableStatus() {
        return humanReadableStatus;
    }

    public void setHumanReadableStatus(final String humanReadableStatus) {
        this.humanReadableStatus = humanReadableStatus;
    }

    public long getFileSize() {
        return fileSize;
    }

    public String getFileName() {
        return fileName;
    }

    public Boolean getUploadedFromLocal() {
        return uploadedFromLocal;
    }

    public Boolean getDownloadedToLocal() {
        return downloadedToLocal;
    }

    public void setUploadedFromLocal(Boolean uploadedFromLocal) {
        this.uploadedFromLocal = uploadedFromLocal;
    }

    public void setDownloadedToLocal(Boolean downloadedToLocal) {
        this.downloadedToLocal = downloadedToLocal;
    }

    public Status getStatus() {
        return status;
    }

    public long getWait() {
        return wait;
    }

    public enum Status {
        @SerializedName("W")
        QUEUED("W"),
        @SerializedName("I")
        PARSING("I"),
        @SerializedName("T")
        TRILATERATING("T"),
        @SerializedName("S")
        STATS("S"),
        @SerializedName("D")
        SUCCESS("D"),
        @SerializedName("E")
        FAILED("E"),
        @SerializedName("A")
        ARCHIVE("A"),
        @SerializedName("C")
        CATALOG("C"),
        @SerializedName("G")
        GEOINDEX("G"),
        //TODO: handoff type
        @SerializedName("?")
        UNKNOWN("?");

        private final String stringStatus;

        Status(final String status) {
            this.stringStatus = status;
        }
    }
}
