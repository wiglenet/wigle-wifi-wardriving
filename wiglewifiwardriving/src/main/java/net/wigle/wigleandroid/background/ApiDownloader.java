package net.wigle.wigleandroid.background;

import android.content.SharedPreferences;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.util.Base64;

import net.wigle.wigleandroid.DatabaseHelper;
import net.wigle.wigleandroid.ListFragment;
import net.wigle.wigleandroid.MainActivity;
import net.wigle.wigleandroid.WiGLEAuthException;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URLEncoder;

public class ApiDownloader extends AbstractApiRequest {
    private final ApiListener listener;

    public ApiDownloader(final FragmentActivity context, final DatabaseHelper dbHelper,
                         final String cacheFilename, final String url, final boolean doFormLogin,
                         final boolean doBasicLogin, final boolean requiresLogin,
                         final String connectionMethod, final ApiListener listener) {
        super(context, dbHelper, "ApiDL", cacheFilename, url, doFormLogin, doBasicLogin,
                requiresLogin, connectionMethod, false);
        this.listener = listener;
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
        }
        catch (final WiGLEAuthException waex) {
            // ALIBI: allow auth exception through
            throw waex;
        }
        catch (final Exception ex) {
            MainActivity.error("ex: " + ex + " result: " + result, ex);
        }
    }

    public void startDownload(final Fragment fragment) throws WiGLEAuthException {
        // if we have cached data, call the handler with that
        final JSONObject cache = getCached();
        if (cache != null) listener.requestComplete(cache, true);
        if (cacheOnly) return;

        // download token if needed
        final SharedPreferences prefs = fragment.getActivity().getSharedPreferences(ListFragment.SHARED_PREFS, 0);
        final boolean beAnonymous = prefs.getBoolean(ListFragment.PREF_BE_ANONYMOUS, false);
        final String authname = prefs.getString(ListFragment.PREF_AUTHNAME, null);
        MainActivity.info("authname: " + authname);
        if (beAnonymous && requiresLogin) {
            MainActivity.info("anonymous, not running ApiDownloader: " + this);
            return;
        }
        if (authname == null && doBasicLogin) {
            MainActivity.info("No authname, going to request token");
            downloadTokenAndStart(fragment);
        } else {
            start();
        }
    }

    protected void downloadTokenAndStart(final Fragment fragment) {
        final ApiDownloader task = new ApiDownloader(fragment.getActivity(), ListFragment.lameStatic.dbHelper,
                null, MainActivity.TOKEN_URL, true, false, true, AbstractApiRequest.REQUEST_POST,
                new ApiListener() {
                    @Override
                    public void requestComplete(final JSONObject json, final boolean isCache)
                            throws WiGLEAuthException {
                        try {
                            // {"success": true, "authname": "AID...", "token": "..."}
                            if (json.getBoolean("success")) {
                                final String authname = json.getString("authname");
                                final String token = json.getString("token");
                                final SharedPreferences prefs = fragment.getContext()
                                        .getSharedPreferences(ListFragment.SHARED_PREFS, 0);
                                final SharedPreferences.Editor edit = prefs.edit();
                                edit.putString(ListFragment.PREF_AUTHNAME, authname);
                                edit.putString(ListFragment.PREF_TOKEN, token);
                                edit.apply();
                                // execute ourselves, the pending task
                                start();
                            } else if (json.has("credential_0")) {
                                String message = "login failed for " +
                                        json.getString("credential_0");
                                MainActivity.warn(message);
                                throw new WiGLEAuthException(message);
                            } else {
                                throw new WiGLEAuthException("Unable to log in.");
                            }
                        }
                        catch (final JSONException ex) {
                            MainActivity.warn("json exception: " + ex + " json: " + json, ex);
                            throw new WiGLEAuthException("Unable to log in.");
                        }
                    }
                });
        task.start();
    }

}
