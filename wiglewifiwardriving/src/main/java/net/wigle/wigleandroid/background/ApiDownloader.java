package net.wigle.wigleandroid.background;

import androidx.fragment.app.FragmentActivity;

import net.wigle.wigleandroid.db.DatabaseHelper;
import net.wigle.wigleandroid.WiGLEAuthException;
import net.wigle.wigleandroid.util.Logging;

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
                requiresLogin, cacheFilename != null, connectionMethod, listener, false);
    }

    @Override
    protected void subRun() throws IOException, InterruptedException, WiGLEAuthException {
        String result = null;
        try {
            result = doDownload(this.connectionMethod);
            if (outputFileName != null) {
                cacheResult(result);
            }
            final JSONObject json = new JSONObject(result);
            listener.requestComplete(json, false);
        } catch (final WiGLEAuthException waex) {
            // ALIBI: allow auth exception through
            throw waex;
        } catch (final JSONException ex) {
            Logging.error("ex: " + ex + " result: " + result, ex);
        }
    }
}
