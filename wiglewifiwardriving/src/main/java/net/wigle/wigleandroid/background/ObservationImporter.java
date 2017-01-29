package net.wigle.wigleandroid.background;

import android.content.SharedPreferences;
import android.location.Location;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;

import net.wigle.wigleandroid.DatabaseHelper;
import net.wigle.wigleandroid.ListFragment;
import net.wigle.wigleandroid.MainActivity;
import net.wigle.wigleandroid.WiGLEAuthException;
import net.wigle.wigleandroid.model.Network;
import net.wigle.wigleandroid.model.NetworkType;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;

/**
 * Use the ApiDL infra to get points from the v2 API
 * Created by arkasha on 1/28/17.
 */

public class ObservationImporter extends AbstractApiRequest {
    private final TransferListener listener;

    public ObservationImporter(final FragmentActivity context,
                               final DatabaseHelper dbHelper, final TransferListener listener) {
        super(context, dbHelper, "HttpDL", "observed-cache.json", MainActivity.OBSERVED_URL, false,
                true, true,
                AbstractApiRequest.REQUEST_GET, true);
        this.listener = listener;
    }

    public void startDownload(final Fragment fragment) {
        // download token if needed
        final SharedPreferences prefs = fragment.getActivity().getSharedPreferences(
                ListFragment.SHARED_PREFS, 0);
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

    @Override
    protected void subRun() throws IOException, InterruptedException {
        Status status = Status.UNKNOWN;
        final Bundle bundle = new Bundle();

        String result = null;
        try {
            result = doDownload(this.connectionMethod);
            if (cacheFilename != null) {
                cacheResult(result);
            }
            final JSONObject json = new JSONObject(result);
            try {
                if (json.getBoolean("success")) {
                    Integer total = json.getInt("count");
                    JSONArray results = json.getJSONArray("results");
                    if ((null != total) && (total > 0L) && (null != results) &&
                            (results.length() > 0)) {
                        status = Status.WRITE_SUCCESS;
                        for (int i = 0; i < results.length(); i++) {
                            String netId = results.getString(i);
                            //DEBUG: MainActivity.info(netId);
                            final String ssid = "";
                            final int frequency = 0;
                            final String capabilities = "";
                            final int level = 0;
                            final Network network = new Network(netId, ssid, frequency,
                                    capabilities, level, NetworkType.WIFI);
                            final Location location = new Location("wigle");
                            final boolean newForRun = true;
                            ListFragment.lameStatic.dbHelper.blockingAddObservation(
                                    network, location, newForRun);

                            if ((i % 1000) == 0) {
                                MainActivity.info("lineCount: " + i + " of " + total);
                            }
                            if (total == 0) {
                                total = 1;
                            }
                            final int percentDone = (i * 1000) / total;
                            sendPercentTimesTen(percentDone, bundle);
                        }
                    }
                } else {
                    MainActivity.error("MyObserved success: false");
                }
            } catch (JSONException jex) {
                MainActivity.error("MyObserved json parse error:", jex);
                status = Status.EXCEPTION;
                bundle.putString(BackgroundGuiHandler.ERROR, "ex problem: " + jex);
            } catch (InterruptedException e) {
                e.printStackTrace();
                status = Status.EXCEPTION;
                bundle.putString(BackgroundGuiHandler.ERROR, "ex problem: " + e);
            } finally {
                listener.transferComplete();
            }

            if (status == null) {
                status = Status.FAIL;
            }
        } catch (final Exception ex) {
            MainActivity.error("ex: " + ex + " result: " + result, ex);
            status = Status.EXCEPTION;
            bundle.putString(BackgroundGuiHandler.ERROR, "ex problem: " + ex);
        } finally {
            sendBundledMessage(status.ordinal(), bundle);
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
