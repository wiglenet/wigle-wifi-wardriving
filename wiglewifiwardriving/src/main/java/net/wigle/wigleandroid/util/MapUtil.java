package net.wigle.wigleandroid.util;

import android.content.SharedPreferences;
import android.util.Base64;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import net.wigle.wigleandroid.TokenAccess;

import java.nio.charset.StandardCharsets;

import org.maplibre.android.util.TileServerOptions;

public class MapUtil {
    public static TileServerOptions getTileServerOptions(@NonNull SharedPreferences prefs) {
        return null;
    }

    /**
     * Creates a Basic authentication token string from SharedPreferences.
     * Retrieves the API token and authname, encodes them, and returns the "Basic " prefixed string.
     *
     * @param prefs SharedPreferences containing the API token and authname
     * @return "Basic <encoded_credentials>" string, or null if credentials are unavailable
     */
    @Nullable
    public static String createAuthToken(@NonNull SharedPreferences prefs) {
        try {
            final String token = TokenAccess.getApiToken(prefs);
            // get authname second, as the token may clear it
            final String authname = prefs.getString(PreferenceKeys.PREF_AUTHNAME, null);
            if (authname != null && token != null) {
                final String encoded = Base64.encodeToString(
                    (authname + ":" + token).getBytes(StandardCharsets.UTF_8),
                    Base64.NO_WRAP);
                return "Basic " + encoded;
            }
        } catch (Exception ex) {
            Logging.error("map tiles: unable to access credentials for mine/others", ex);
        }
        return null;
    }
}
