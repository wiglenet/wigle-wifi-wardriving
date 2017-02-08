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

/**
 * Abstract base class for WiGLE API connections
 * Created by arkasha on 1/28/17.
 */
public abstract class AbstractApiRequest extends AbstractBackgroundTask {

    protected final String cacheFilename;
    protected final String url;
    protected final String connectionMethod;
    protected final boolean doFormLogin;
    protected final boolean doBasicLogin;
    protected final boolean requiresLogin;
    protected final boolean useCacheIfPresent;
    protected boolean cacheOnly = false;
    protected final ApiListener listener;


    public static final String REQUEST_GET = "GET";
    public static final String REQUEST_POST = "POST";

    public AbstractApiRequest(final FragmentActivity context, final DatabaseHelper dbHelper,
                              final String name, final String cacheFilename, final String url,
                              final boolean doFormLogin, final boolean doBasicLogin,
                              final boolean requiresLogin, final boolean useCacheIfPresent,
                              final String connectionMethod, final ApiListener listener,
                              final boolean createDialog) {
        super(context, dbHelper, name,createDialog);
        this.cacheFilename = cacheFilename;
        this.url = url;
        this.connectionMethod = connectionMethod;
        this.doFormLogin = doFormLogin;
        this.doBasicLogin = doBasicLogin;
        this.requiresLogin = requiresLogin;
        this.useCacheIfPresent = useCacheIfPresent;
        this.listener = listener;
    }

    public void setCacheOnly(final boolean cacheOnly) {
        this.cacheOnly = cacheOnly;
    }

    public JSONObject getCached() {
        final File file = new File(MainActivity.getSDPath() + cacheFilename);
        if (! file.exists() || !file.canRead()) {
            MainActivity.warn("Cache file doesn't exist or can't be read: " + file);
            return null;
        }
        BufferedReader br = null;
        JSONObject json = null;
        final StringBuilder result = new StringBuilder();
        try {
            br = new BufferedReader(new FileReader(file));
            String line;

            while ((line = br.readLine()) != null) {
                result.append(line);
                result.append('\n');
            }
            br.close();
            json = new JSONObject(result.toString());
        }
        catch (final Exception ex) {
            MainActivity.error("Exception reading cache file: " + ex, ex);
        }
        finally {
            if (br != null) {
                try {
                    br.close();
                } catch (final IOException ex) {
                    MainActivity.error("exception closing br: " + ex, ex);
                }
            }
        }
        return json;
    }

    protected void cacheResult(final String result) {
        if (cacheFilename == null || result == null || result.length() < 1) return;

        FileOutputStream fos = null;
        try {
            fos = MainActivity.createFile(context, cacheFilename);
            // header
            FileUploaderTask.writeFos(fos, result);
        }
        catch (final IOException ex) {
            MainActivity.error("exception caching result: " + ex, ex);
        }
        finally {
            if (fos != null) {
                try {
                    fos.close();
                }
                catch (final IOException ex) {
                    MainActivity.error("exception closing fos: " + ex, ex);
                }
            }
        }
    }

    public void startDownload(final Fragment fragment) throws WiGLEAuthException {
        // if we have cached data, and are meant to use it, call the handler with that
        if (useCacheIfPresent) {
            final JSONObject cache = getCached();
            if (cache != null) listener.requestComplete(cache, true);
            if (cacheOnly) return;
        }

        // download token if needed
        final SharedPreferences prefs = fragment.getActivity().getSharedPreferences(
                ListFragment.SHARED_PREFS, 0);
        final boolean beAnonymous = prefs.getBoolean(ListFragment.PREF_BE_ANONYMOUS, false);
        final String authname = prefs.getString(ListFragment.PREF_AUTHNAME, null);
        MainActivity.info("authname: " + authname);
        if (beAnonymous && requiresLogin) {
            MainActivity.info("anonymous, not running ApiRequest: " + this);
            return;
        }
        if (authname == null && doBasicLogin) {
            MainActivity.info("No authname, going to request token");
            downloadTokenAndStart(fragment);
        } else {
            start();
        }
    }

    protected String getResultString(final BufferedReader reader) throws IOException, InterruptedException {
        // final Bundle bundle = new Bundle();
        String line;
        final StringBuilder result = new StringBuilder();
        while ( (line = reader.readLine()) != null ) {
            if ( wasInterrupted() ) {
                throw new InterruptedException( "we were interrupted" );
            }
            result.append(line);

            // MainActivity.info("AbstractApiRequest result: " + line);
        }
        return result.toString();
    }

    protected String doDownload(final String connectionMethod) throws IOException, InterruptedException {
        final boolean setBoundary = false;

        PreConnectConfigurator preConnectConfigurator = null;
        if (doBasicLogin) {
            final SharedPreferences prefs = context.getSharedPreferences(ListFragment.SHARED_PREFS, 0);
            final String authname = prefs.getString(ListFragment.PREF_AUTHNAME, null);
            final String token = prefs.getString(ListFragment.PREF_TOKEN, null);
            final String encoded = Base64.encodeToString((authname + ":" + token).getBytes("UTF-8"), Base64.NO_WRAP);
            // Cannot set request property after connection is made
            preConnectConfigurator = new PreConnectConfigurator() {
                @Override
                public void configure(HttpURLConnection connection) {
                    connection.setRequestProperty("Authorization", "Basic " + encoded);
                }
            };
        }

        final HttpURLConnection conn = HttpFileUploader.connect(url, setBoundary,
                preConnectConfigurator, connectionMethod);
        if (conn == null) {
            throw new IOException("No connection created");
        }

        if (ApiDownloader.REQUEST_POST.equals(connectionMethod)) {
            // Send request output.
            final DataOutputStream printout = new DataOutputStream(conn.getOutputStream());
            if (doFormLogin) {
                final String username = getUsername();
                final String password = getPassword();
                final String content = "credential_0=" + URLEncoder.encode(username, HttpFileUploader.ENCODING) +
                        "&credential_1=" + URLEncoder.encode(password, HttpFileUploader.ENCODING);
                printout.writeBytes(content);
            }
            printout.flush();
            printout.close();
        } else if (ApiDownloader.REQUEST_GET.equals(connectionMethod)) {
            MainActivity.info( "GET to " + conn.getURL() + " responded " + conn.getResponseCode());
        }

        // get response data
        final BufferedReader input = new BufferedReader(
                new InputStreamReader( HttpFileUploader.getInputStream( conn ), HttpFileUploader.ENCODING) );
        try {
            return getResultString(input);
        }
        finally {
            try {
                input.close();
            }
            catch (final Exception ex) {
                MainActivity.warn("Exception closing downloader reader: " + ex, ex);
            }
        }
    }

    /**
     * need to DRY this up vs. the bundle-notification in ObservationImporter
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