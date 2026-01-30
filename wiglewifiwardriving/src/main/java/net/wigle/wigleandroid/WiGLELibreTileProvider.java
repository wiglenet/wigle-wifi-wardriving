package net.wigle.wigleandroid;

import android.annotation.SuppressLint;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;

import net.wigle.wigleandroid.net.WiGLETileAuthInterceptor;
import net.wigle.wigleandroid.util.Logging;
import net.wigle.wigleandroid.util.PreferenceKeys;

import org.maplibre.android.module.http.HttpRequestUtil;
import org.maplibre.android.style.sources.RasterSource;
import org.maplibre.android.style.sources.TileSet;

import java.util.Calendar;

import okhttp3.OkHttpClient;

/**
 * Load WiGLE raster overlay tiles in MapLibre
 */
public class WiGLELibreTileProvider {

    /**
     * override OkHttpClient to add user agent and auth header
     * must be called before any RasterSource using WiGLE tiles is added.
     */
    public static void configureAuthentication(@NonNull SharedPreferences prefs) {
        try {
            OkHttpClient client = new OkHttpClient.Builder()
                    .addInterceptor(new WiGLETileAuthInterceptor(prefs))
                    // Increase timeouts for slow tile loads
                    .connectTimeout(180, java.util.concurrent.TimeUnit.SECONDS)
                    .readTimeout(180, java.util.concurrent.TimeUnit.SECONDS)
                    .build();

            HttpRequestUtil.setOkHttpClient(client);
            Logging.info("Configured MapLibre OkHttpClient: WiGLETileAuthInterceptor");
        } catch (Exception e) {
            Logging.error("Failed to config MapLibre tile OkHttpClient: " + e.getMessage(), e);
        }
    }

    /**
     * create WiGLE MapLibre RasterSource
     * @param sourceId The unique ID for the raster source
     * @param prefs SharedPreferences user settings
     * @return a configured RasterSource instance
     * @throws IllegalArgumentException if tiles are disabled in preferences
     */
    @NonNull
    public static RasterSource createRasterSource(@NonNull String sourceId,
                                                 @NonNull SharedPreferences prefs) {
        if (PreferenceKeys.PREF_MAP_NO_TILE.equals(
                prefs.getString(PreferenceKeys.PREF_SHOW_DISCOVERED, PreferenceKeys.PREF_MAP_NO_TILE))) {
            throw new IllegalArgumentException("Tiles are disabled in preferences");
        }

        String tileUrlTemplate = buildTileUrlTemplate(prefs);
        // DEBUG Logging.info("WiGLE tile URL template: " + tileUrlTemplate);

        TileSet tileSet = new TileSet("2.1.0", tileUrlTemplate);

        // set scheme to "xyz" to match WiGLE/Gmaps coordinate system
        tileSet.setScheme("xyz");
        return new RasterSource(sourceId, tileSet);
    }

    /**
     * hacked URL format retrofit for MapLibre <> WiGLE
     *
     * @param prefs SharedPreferences containing user settings
     * @return Tile URL template string with {z}, {x}, {y} placeholders for RasterSource
     */
    @SuppressLint("DefaultLocale")
    private static String buildTileUrlTemplate(@NonNull SharedPreferences prefs) {
        final Long since = prefs.getLong(PreferenceKeys.PREF_SHOW_DISCOVERED_SINCE, 2001);
        int thisYear = Calendar.getInstance().get(Calendar.YEAR);
        String tileContents = prefs.getString(PreferenceKeys.PREF_SHOW_DISCOVERED,
                PreferenceKeys.PREF_MAP_NO_TILE);

        String sinceString = String.format("%d0000-00000", since);
        String toString = String.format("%d0000-00000", thisYear + 1);

        //hack our map tile URL template to honor the template format MapLibre uses, using number-order (fragile)
        String baseUrl = AbstractMappingFragment.MAP_TILE_URL_FORMAT
                .replaceFirst("%d", "{z}")  // First %d is zoom -> {z}
                .replaceFirst("%d", "{x}")  // Second %d is x -> {x}
                .replaceFirst("%d", "{y}")  // Third %d is y -> {y}
                .replaceFirst("%s", sinceString)
                .replaceFirst("%s", toString);
        // high-res parameter if enabled
        if (MainActivity.isHighDefinition()) {
            baseUrl += AbstractMappingFragment.HIGH_RES_TILE_TRAILER;
        }

        // tile first-observer filter
        if (PreferenceKeys.PREF_MAP_ONLYMINE_TILE.equals(tileContents)) {
            baseUrl += AbstractMappingFragment.ONLY_MINE_TILE_TRAILER;
        } else if (PreferenceKeys.PREF_MAP_NOTMINE_TILE.equals(tileContents)) {
            baseUrl += AbstractMappingFragment.NOT_MINE_TILE_TRAILER;
        }
        return baseUrl;
    }
}