package net.wigle.wigleandroid.net;

public interface RequestCompletedListener<T, JSONObject> {
    void onTaskCompleted();
    void onTaskSucceeded(T response);
    void onTaskFailed(int status, JSONObject error);
}
