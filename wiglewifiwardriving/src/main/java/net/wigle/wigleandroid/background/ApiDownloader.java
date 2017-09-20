package net.wigle.wigleandroid.background;

import android.support.v4.app.FragmentActivity;

import net.wigle.wigleandroid.DatabaseHelper;
import net.wigle.wigleandroid.MainActivity;
import net.wigle.wigleandroid.WiGLEAuthException;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;

/**
 * general-purpose downloader for WiGLE API connections
 */
public class ApiDownloader extends AbstractApiRequest {

    public ApiDownloader(final FragmentActivity context, final DatabaseHelper dbHelper,
                         final String cacheFilename, final String url, final boolean doFormLogin,
                         final boolean doBasicLogin, final boolean requiresLogin,
                         final String connectionMethod, final ApiListener listener) {
        super(context, dbHelper, "ApiDL", cacheFilename, url, doFormLogin, doBasicLogin,
                requiresLogin, true, connectionMethod, listener, false);
    }

    @Override
    protected void subRun() throws IOException, InterruptedException, WiGLEAuthException {
        String result = null;
        try {
            result = doDownload(this.connectionMethod);
            if (cacheFilename != null) {
                cacheResult(result);
            }
            final JSONObject json = new JSONObject(result);
            listener.requestComplete(json, false);
        } catch (final WiGLEAuthException waex) {
            // ALIBI: allow auth exception through
            throw waex;
        } catch (final JSONException ex) {
            MainActivity.error("ex: " + ex + " result: " + result, ex);
        }
    }
}
