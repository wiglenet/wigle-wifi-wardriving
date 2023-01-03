package net.wigle.wigleandroid.background;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;

import net.wigle.wigleandroid.db.DatabaseHelper;
import net.wigle.wigleandroid.ListFragment;
import net.wigle.wigleandroid.TokenAccess;
import net.wigle.wigleandroid.util.Logging;
import net.wigle.wigleandroid.util.PreferenceKeys;
import net.wigle.wigleandroid.util.UrlConfig;

import org.json.JSONException;

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
    /*
     * need to DRY this up vs. the exception-based version in ApiDownloader
     */
    protected void downloadTokenAndStart(final Fragment fragment) {
        final ApiDownloader task = new ApiDownloader(fragment.getActivity(), ListFragment.lameStatic.dbHelper,
                null, UrlConfig.TOKEN_URL, true, false, true, AbstractApiRequest.REQUEST_POST,
                (json, isCache) -> {
                    try {
                        // {"success": true, "authname": "AID...", "token": "..."}
                        if (json.getBoolean("success")) {
                            final String authname = json.getString("authname");
                            final String token = json.getString("token");
                            final Context c = fragment.getContext();
                            if (null != c) {
                                final SharedPreferences prefs = c.getSharedPreferences(PreferenceKeys.SHARED_PREFS, 0);
                                final SharedPreferences.Editor edit = prefs.edit();
                                edit.putString(PreferenceKeys.PREF_AUTHNAME, authname);
                                edit.apply();
                                TokenAccess.setApiToken(prefs, token);
                                // execute ourselves, the pending task
                                start();
                            }
                        } else if (json.has("credential_0")) {
                            String message = "login failed for " +
                                    json.getString("credential_0");
                            Logging.warn(message);
                            final Bundle bundle = new Bundle();
                            sendBundledMessage(Status.BAD_LOGIN.ordinal(), bundle);
                        } else {
                            final Bundle bundle = new Bundle();
                            sendBundledMessage(Status.BAD_LOGIN.ordinal(), bundle);
                        }
                    } catch (final JSONException ex) {
                        final Bundle bundle = new Bundle();
                        sendBundledMessage(Status.BAD_LOGIN.ordinal(), bundle);
                    } catch (final Exception e) {
                        Logging.error("Failed to log in " + e + " payload: " + json, e);
                        final Bundle bundle = new Bundle();
                        sendBundledMessage(Status.BAD_LOGIN.ordinal(), bundle);
                    }
                });
        task.start();
    }
}
