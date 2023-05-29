package net.wigle.wigleandroid.net;

public interface AuthenticatedRequestCompletedListener<T, JSONObject> extends RequestCompletedListener<T, JSONObject>{
    void onAuthenticationRequired();
}
