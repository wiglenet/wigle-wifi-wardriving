package net.wigle.wigleandroid;

import android.annotation.SuppressLint;
import android.content.SharedPreferences;

import com.google.android.gms.maps.model.Tile;
import com.google.android.gms.maps.model.TileProvider;

import net.wigle.wigleandroid.util.Logging;
import net.wigle.wigleandroid.util.PreferenceKeys;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Calendar;

/**
 * TileProvider implementation for WiGLE network discovery tiles.
 * Fetches tiles from the WiGLE API based on user preferences.
 */
public class WiGLETileProvider implements TileProvider {
    private static final int TILE_MIN_ZOOM = 0;
    private static final int TILE_MAX_ZOOM = 24;
    private static final int BYTE_BUFFER_SIZE = 4096;
    
    private final SharedPreferences prefs;
    private final String userAgent;
    private final String authToken;
    private final int providerTileRes;

    /**
     * Creates a new WiGLETileProvider.
     *
     * @param prefs SharedPreferences for accessing user settings
     * @param userAgent User agent string for HTTP requests
     * @param authToken Authentication token (can be null)
     * @param providerTileRes Tile resolution (256 or 512 pixels)
     */
    public WiGLETileProvider(SharedPreferences prefs, String userAgent, 
                            String authToken, int providerTileRes) {
        this.prefs = prefs;
        this.userAgent = userAgent;
        this.authToken = authToken;
        this.providerTileRes = providerTileRes;
    }

    @SuppressLint("DefaultLocale")
    @Override
    public Tile getTile(int x, int y, int zoom) {
        if (!checkTileExists(x, y, zoom)) {
            return NO_TILE;
        }

        final Long since = prefs.getLong(PreferenceKeys.PREF_SHOW_DISCOVERED_SINCE, 2001);
        int thisYear = Calendar.getInstance().get(Calendar.YEAR);
        String tileContents = prefs.getString(PreferenceKeys.PREF_SHOW_DISCOVERED,
                PreferenceKeys.PREF_MAP_NO_TILE);

        String sinceString = String.format("%d0000-00000", since);
        String toString = String.format("%d0000-00000", thisYear + 1);
        String url = String.format(AbstractMappingFragment.MAP_TILE_URL_FORMAT,
                zoom, x, y, sinceString, toString);

        if (MainActivity.isHighDefinition()) {
            url += AbstractMappingFragment.HIGH_RES_TILE_TRAILER;
        }

        // ALIBI: defaults to "ALL"
        if (PreferenceKeys.PREF_MAP_ONLYMINE_TILE.equals(tileContents)) {
            url += AbstractMappingFragment.ONLY_MINE_TILE_TRAILER;
        } else if (PreferenceKeys.PREF_MAP_NOTMINE_TILE.equals(tileContents)) {
            url += AbstractMappingFragment.NOT_MINE_TILE_TRAILER;
        }

        try {
            final byte[] data = downloadData(new URL(url), userAgent, authToken);
            if (data.length > 0) {
                return new Tile(providerTileRes, providerTileRes, data);
            } else {
                return null;
            }
        } catch (MalformedURLException e) {
            throw new AssertionError(e);
        }
    }

    /**
     * Checks if a tile exists for the given coordinates and zoom level.
     * Depends on supported levels on the server.
     *
     * @param x Tile X coordinate
     * @param y Tile Y coordinate
     * @param zoom Zoom level
     * @return true if tile exists, false otherwise
     */
    private boolean checkTileExists(int x, int y, int zoom) {
        return zoom >= TILE_MIN_ZOOM && zoom <= TILE_MAX_ZOOM;
    }

    /**
     * Downloads tile data from the specified URL.
     *
     * @param url URL to download from
     * @param userAgent User agent string
     * @param authToken Authentication token (can be null)
     * @return Byte array containing tile data
     */
    private byte[] downloadData(final URL url, final String userAgent, final String authToken) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        InputStream is = null;
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) url.openConnection();
            if (authToken != null) {
                conn.setRequestProperty("Authorization", authToken);
            }
            conn.setRequestProperty("User-Agent", userAgent);
            int responseCode = conn.getResponseCode();
            if (responseCode >= 200 && responseCode < 300) {
                is = conn.getInputStream();
                byte[] byteChunk = new byte[BYTE_BUFFER_SIZE];
                int n;

                while ((n = is.read(byteChunk)) > 0) {
                    baos.write(byteChunk, 0, n);
                }
            } else {
                String errorMessage = conn.getResponseMessage();
                Logging.error("HTTP error " + responseCode + " while fetching tile from " +
                        url.toExternalForm() + ": " + errorMessage);
                InputStream errorStream = conn.getErrorStream();
                if (errorStream != null) {
                    try {
                        byte[] errorChunk = new byte[256];
                        int errorBytes = errorStream.read(errorChunk);
                        if (errorBytes > 0) {
                            String errorBody = new String(errorChunk, 0, errorBytes);
                            Logging.error("Error response body: " + errorBody);
                        }
                    } catch (IOException e) {
                        // ignore errors reading error stream
                    } finally {
                        try {
                            errorStream.close();
                        } catch (IOException e) {
                            // what would we even do here?
                        }
                    }
                }
                return baos.toByteArray();
            }
        } catch (IOException e) {
            Logging.error("Failed while reading bytes from " +
                    url.toExternalForm() + ": " + e.getMessage());
        } finally {
            if (is != null) {
                try {
                    is.close();
                } catch (IOException ioex) {
                    Logging.error("Failed while closing InputStream " +
                            url.toExternalForm() + ": " + ioex.getMessage());
                }
            }
            if (conn != null) {
                conn.disconnect();
            }
        }
        return baos.toByteArray();
    }
}
