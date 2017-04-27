package net.wigle.wigleandroid.background;

import android.content.SharedPreferences;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.util.Base64;

import net.wigle.wigleandroid.DatabaseHelper;
import net.wigle.wigleandroid.ListFragment;
import net.wigle.wigleandroid.MainActivity;
import net.wigle.wigleandroid.TokenAccess;
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
import java.net.URL;
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

    public static final String BOUNDARY = "*****";

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

    public static HttpURLConnection connect(String urlString, final boolean setBoundary,
                                            final String connectionMethod) throws IOException {
        return connect(urlString, setBoundary, null, connectionMethod);
    }

    public static HttpURLConnection connect(String urlString, final boolean setBoundary,
                                            final PreConnectConfigurator preConnectConfigurator,
                                            final String connectionMethod) throws IOException {
        URL connectURL;
        try{
            connectURL = new URL( urlString );
        }
        catch( Exception ex ) {
            MainActivity.error( "MALFORMED URL: " + ex, ex );
            return null;
        }

        return createConnection(connectURL, setBoundary, preConnectConfigurator, connectionMethod);
    }

    private static HttpURLConnection createConnection(final URL connectURL, final boolean setBoundary,
                                                      final PreConnectConfigurator preConnectConfigurator,
                                                      final String connectionMethod)
            throws IOException {

        String javaVersion = "unknown";
        try {
            javaVersion =  System.getProperty("java.vendor") + " " +
                    System.getProperty("java.version") + ", jvm: " +
                    System.getProperty("java.vm.vendor") + " " +
                    System.getProperty("java.vm.name") + " " +
                    System.getProperty("java.vm.version") + " on " +
                    System.getProperty("os.name") + " " +
                    System.getProperty("os.version") +
                    " [" + System.getProperty("os.arch") + "]";
        } catch (RuntimeException ignored) { }
        final String userAgent = "WigleWifi ("+javaVersion+")";

        // Open a HTTP connection to the URL
        HttpURLConnection conn = (HttpURLConnection) connectURL.openConnection();

        // IFF it's a POST, Allow Outputs
        if (ApiDownloader.REQUEST_POST.equals(connectionMethod)) {
            conn.setDoOutput(true);
            // Allow Inputs
            conn.setDoInput(true);
            conn.setRequestProperty("Connection", "Keep-Alive");
            if ( setBoundary ) {
                conn.setRequestProperty("Content-Type", "multipart/form-data;boundary=" + BOUNDARY);
            }
            else {
                conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            }
            // chunk large stuff
            conn.setChunkedStreamingMode( 32*1024 );

            // shouldn't have to do this, but it makes their HttpURLConnectionImpl happy
            conn.setRequestProperty("Transfer-Encoding", "chunked");
        }

        // Don't use a cached copy.
        conn.setUseCaches(false);

        // Don't follow redirects.
        conn.setInstanceFollowRedirects( false );

        // Use the specified method.
        conn.setRequestMethod(connectionMethod);
        conn.setRequestProperty( "Accept-Encoding", "gzip" );
        conn.setRequestProperty( "User-Agent", userAgent );

        // 8 hours
        conn.setReadTimeout(8*60*60*1000);

        // allow caller to munge
        if (preConnectConfigurator != null) {
            preConnectConfigurator.configure(conn);
        }

        // connect
        MainActivity.info( "about to connect" );
        conn.connect();
        MainActivity.info( "connected" );

        return conn;
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
            final String token = TokenAccess.getApiToken(prefs);
            final String encoded = Base64.encodeToString((authname + ":" + token).getBytes("UTF-8"), Base64.NO_WRAP);
            // Cannot set request property after connection is made
            preConnectConfigurator = new PreConnectConfigurator() {
                @Override
                public void configure(HttpURLConnection connection) {
                    //TODO: for non-upload tasks, how to handle anonymity
                    connection.setRequestProperty("Authorization", "Basic " + encoded);
                }
            };
        }

        final HttpURLConnection conn = AbstractApiRequest.connect(url, setBoundary,
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
                                edit.apply();
                                TokenAccess.setApiToken(prefs, token);
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
                        } catch (final JSONException ex) {
                            MainActivity.warn("json exception: " + ex + " json: " + json, ex);
                            throw new WiGLEAuthException("Unable to log in.");
                        } catch (final Exception e) {
                            MainActivity.warn("response exception: " + e + " json: " + json, e);
                            throw new WiGLEAuthException("Unable to log in.");
                        }
                    }
                });
        task.start();
    }
}