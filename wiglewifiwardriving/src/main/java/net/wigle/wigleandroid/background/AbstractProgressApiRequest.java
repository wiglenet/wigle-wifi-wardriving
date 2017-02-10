package net.wigle.wigleandroid.background;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;

import net.wigle.wigleandroid.DatabaseHelper;
import net.wigle.wigleandroid.ListFragment;
import net.wigle.wigleandroid.MainActivity;
import net.wigle.wigleandroid.WiGLEAuthException;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * downloadTokenAndStart abstract parent for API connections that have a progress dialog
 * Created by arkasha on 2/10/17.
 */

public abstract class AbstractProgressApiRequest extends AbstractApiRequest {
    public AbstractProgressApiRequest(FragmentActivity context, DatabaseHelper dbHelper, String name,
                                      String cacheFilename, String url, boolean doFormLogin,
                                      boolean doBasicLogin, boolean requiresLogin,
                                      boolean useCacheIfPresent, String connectionMethod,
                                      ApiListener listener, boolean createDialog) {
        super(context, dbHelper, name, cacheFilename, url, doFormLogin, doBasicLogin, requiresLogin,
                useCacheIfPresent, connectionMethod, listener, createDialog);
    }

    @Override
    /**
     * need to DRY this up vs. the exception-based version in ApiDownloader
     */
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
                                final Bundle bundle = new Bundle();
                                sendBundledMessage(Status.BAD_LOGIN.ordinal(), bundle);
                            } else {
                                final Bundle bundle = new Bundle();
                                sendBundledMessage(Status.BAD_LOGIN.ordinal(), bundle);
                            }
                        }
                        catch (final JSONException ex) {
                            final Bundle bundle = new Bundle();
                            sendBundledMessage(Status.BAD_LOGIN.ordinal(), bundle);
                        }
                    }
                });
        task.start();
    }
}
