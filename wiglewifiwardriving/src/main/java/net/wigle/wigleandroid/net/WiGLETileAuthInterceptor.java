package net.wigle.wigleandroid.net;

import android.content.SharedPreferences;

import androidx.annotation.NonNull;

import net.wigle.wigleandroid.TokenAccess;
import net.wigle.wigleandroid.util.Logging;
import net.wigle.wigleandroid.util.PreferenceKeys;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;

import okhttp3.Credentials;
import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;

/**
 * HAAAAACK:
 * OkHttp interceptor that adds Basic Authentication headers to WiGLE tile requests.
 */
public class WiGLETileAuthInterceptor implements Interceptor {
    private static final String WIGLE_TILE_DOMAIN = "wigle.net";
    private static final String WIGLE_TILE_PATH = "/clientTile";
    
    private final String credentials;

    /**
     * Creates an interceptor.
     *
     * @param prefs SharedPreferences containing authentication credentials
     */
    public WiGLETileAuthInterceptor(final SharedPreferences prefs) {
        final String token = TokenAccess.getApiToken(prefs);
        final String authname = prefs.getString(PreferenceKeys.PREF_AUTHNAME, null);
        if (authname != null && token != null) {
            this.credentials = Credentials.basic(authname, token);
        } else {
            Logging.warn("null credentials for FOSS TileAuth.");
            this.credentials = null;
        }
    }

    /**
     * Feather-burning user-agent access (some kind of class-loading issue?)
     *
     * @return The user agent string matching WiGLETileProvider
     */
    private static String getUserAgent() {
        return WiGLEApiManager.USER_AGENT;
    }

    @NonNull
    @Override
    public Response intercept(@NotNull Chain chain) throws IOException {
        Request request = chain.request();
        String url = request.url().toString();
        
        // Only apply authentication to WiGLE tile requests (also HAAAACK)
        if (isWiGLETileRequest(url)) {
            // DEBUG:
            Logging.info("WiGLE tile request: " + url);
            Request.Builder requestBuilder = request.newBuilder();
            
            if (credentials != null) {
                requestBuilder.header("Authorization", credentials);
            }
            
            requestBuilder.header("User-Agent", getUserAgent());
            request = requestBuilder.build();
        }
        return chain.proceed(request);
    }

    /**
     * Checks if the given URL is a WiGLE tile request.
     *
     * @param url The request URL
     * @return true if this is a WiGLE tile request, false otherwise
     */
    private boolean isWiGLETileRequest(String url) {
        return url != null && 
               url.contains(WIGLE_TILE_DOMAIN) && 
               url.contains(WIGLE_TILE_PATH);
    }
}
