package net.wigle.wigleandroid.background;

public interface ProgressListener {
    void onRequestProgress(long bytesWritten, long contentLength);
}