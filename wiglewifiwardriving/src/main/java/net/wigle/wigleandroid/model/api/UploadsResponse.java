package net.wigle.wigleandroid.model.api;

import net.wigle.wigleandroid.model.Upload;

import java.util.List;

/**
 * Model of the API upload list response
 */
public class UploadsResponse {
    private boolean success;
    private List<Upload> results;
    private long processingQueueDepth;
    private long geoQueueDepth;
    private long trilaterationQueueDepth;

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public List<Upload> getResults() {
        return results;
    }

    public void setResults(List<Upload> results) {
        this.results = results;
    }

    public long getProcessingQueueDepth() {
        return processingQueueDepth;
    }

    public void setProcessingQueueDepth(long processingQueueDepth) {
        this.processingQueueDepth = processingQueueDepth;
    }

    public long getGeoQueueDepth() {
        return geoQueueDepth;
    }

    public void setGeoQueueDepth(long geoQueueDepth) {
        this.geoQueueDepth = geoQueueDepth;
    }

    public long getTrilaterationQueueDepth() {
        return trilaterationQueueDepth;
    }

    public void setTrilaterationQueueDepth(long trilaterationQueueDepth) {
        this.trilaterationQueueDepth = trilaterationQueueDepth;
    }
}
