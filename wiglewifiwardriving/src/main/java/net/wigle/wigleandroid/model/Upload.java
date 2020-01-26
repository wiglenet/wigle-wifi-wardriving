package net.wigle.wigleandroid.model;

/**
 * upload. not thread-safe.
 */
public final class Upload {
    private final String transid;
    private final long totalWifiGps;
    private final long totalBtGps;
    private final long totalCellGps;
    private final int percentDone;
    private final String status;
    private final long fileSize;
    private final String fileName;
    private final Boolean uploadedFromLocal;
    private final Boolean downloadedToLocal;

    public Upload(final String transid, final long totalWifiGps, final long totalBtGps, final long totalCellGps,
                  final int percentDone, final String status, final long fileSize, final String fileName, final Boolean uploadedFromLocal, final Boolean downloadedToLocal) {

        this.transid = transid;
        this.totalWifiGps = totalWifiGps;
        this.totalBtGps = totalBtGps;
        this.totalCellGps = totalCellGps;
        this.percentDone = percentDone;
        this.status = status;
        this.fileSize = fileSize;
        this.fileName = fileName;
        this.uploadedFromLocal = uploadedFromLocal;
        this.downloadedToLocal = downloadedToLocal;
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

    public int getPercentDone() {
        return percentDone;
    }

    public String getStatus() {
        return status;
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
}
