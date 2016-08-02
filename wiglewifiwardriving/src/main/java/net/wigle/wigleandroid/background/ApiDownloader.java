package net.wigle.wigleandroid.background;

import android.content.SharedPreferences;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.util.Base64;

import net.wigle.wigleandroid.DatabaseHelper;
import net.wigle.wigleandroid.ListFragment;
import net.wigle.wigleandroid.MainActivity;

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

public class ApiDownloader extends AbstractBackgroundTask {
    private final ApiListener listener;
    private final String cacheFilename;
    private final String url;
    private final boolean doFormLogin;
    private final boolean doBasicLogin;
    private final boolean requiresLogin;
    private boolean cacheOnly = false;

    public ApiDownloader(final FragmentActivity context, final DatabaseHelper dbHelper,
                         final String cacheFilename, final String url, final boolean doFormLogin,
                         final boolean doBasicLogin, final boolean requiresLogin,
                         final ApiListener listener) {

        super(context, dbHelper, "ApiDL", false);
        this.cacheFilename = cacheFilename;
        this.url = url;
        this.doFormLogin = doFormLogin;
        this.doBasicLogin = doBasicLogin;
        this.requiresLogin = requiresLogin;
        this.listener = listener;
    }

    public void setCacheOnly(final boolean cacheOnly) {
        this.cacheOnly = cacheOnly;
    }

    @Override
    protected void subRun() throws IOException, InterruptedException {
        String result = null;
        try {
            result = doDownload();
            if (cacheFilename != null) {
                cacheResult(result);
            }
            final JSONObject json = new JSONObject(result);
            listener.requestComplete(json, false);
        }
        catch (final Exception ex) {
            MainActivity.error("ex: " + ex + " result: " + result, ex);
        }
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

    private void cacheResult(final String result) {
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

    private String doDownload() throws IOException, InterruptedException {
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

        final HttpURLConnection conn = HttpFileUploader.connect(url, setBoundary, preConnectConfigurator);
        if (conn == null) {
            throw new IOException("No connection created");
        }

        // Send POST output.
        final DataOutputStream printout = new DataOutputStream (conn.getOutputStream ());
        if (doFormLogin) {
            final String username = getUsername();
            final String password = getPassword();
            final String content = "credential_0=" + URLEncoder.encode(username, HttpFileUploader.ENCODING) +
                    "&credential_1=" + URLEncoder.encode(password, HttpFileUploader.ENCODING);
            printout.writeBytes(content);
        }
        printout.flush();
        printout.close();

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

    private String getResultString(final BufferedReader reader) throws IOException, InterruptedException {
        // final Bundle bundle = new Bundle();
        String line;
        final StringBuilder result = new StringBuilder();
        while ( (line = reader.readLine()) != null ) {
            if ( wasInterrupted() ) {
                throw new InterruptedException( "we were interrupted" );
            }
            result.append(line);

            // MainActivity.info("apiDownloader result: " + line);
        }
        return result.toString();
    }

    public void startDownload(final Fragment fragment) {
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

    private void downloadTokenAndStart(final Fragment fragment) {
        final ApiDownloader task = new ApiDownloader(fragment.getActivity(), ListFragment.lameStatic.dbHelper,
                null, MainActivity.TOKEN_URL, true, false, true,
                new ApiListener() {
                    @Override
                    public void requestComplete(final JSONObject json, final boolean isCache) {
                        try {
                            // {"success":true,"result":{"authname":"AID...","token":"..."}}
                            if (json.getBoolean("success")) {
                                final JSONObject result = json.getJSONObject("result");
                                final String authname = result.getString("authname");
                                final String token = result.getString("token");
                                final SharedPreferences prefs = fragment.getContext()
                                        .getSharedPreferences(ListFragment.SHARED_PREFS, 0);
                                final SharedPreferences.Editor edit = prefs.edit();
                                edit.putString(ListFragment.PREF_AUTHNAME, authname);
                                edit.putString(ListFragment.PREF_TOKEN, token);
                                edit.apply();

                                // execute ourselves, the pending task
                                start();
                            }
                        }
                        catch (final JSONException ex) {
                            MainActivity.warn("json exception: " + ex + " json: " + json, ex);
                        }
                    }
                });

        task.start();
    }

}
