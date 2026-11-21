package net.wigle.wigleandroid.model.api;

import androidx.annotation.Keep;

/**
 * API Token response object
 */
@Keep
public class ApiTokenResponse {
    private boolean success;
    private String authname;
    private String token;

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getAuthname() {
        return authname;
    }

    public void setAuthname(String authname) {
        this.authname = authname;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    @Keep
    public enum ApiTokenType {
        API, COMMAPI, ANDROID
    }
}
