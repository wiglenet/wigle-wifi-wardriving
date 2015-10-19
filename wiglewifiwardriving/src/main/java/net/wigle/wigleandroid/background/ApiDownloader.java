package net.wigle.wigleandroid.background;

import android.support.v4.app.FragmentActivity;

import net.wigle.wigleandroid.DatabaseHelper;
import net.wigle.wigleandroid.MainActivity;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;

public class ApiDownloader extends AbstractBackgroundTask {
    private final ApiListener listener;
    private final String cacheFilename;

    public ApiDownloader(final FragmentActivity context, final DatabaseHelper dbHelper,
                         final String cacheFilename, final ApiListener listener) {

        super(context, dbHelper, "ApiDL", false);
        this.cacheFilename = cacheFilename;
        this.listener = listener;
    }

    @Override
    protected void subRun() throws IOException, InterruptedException {
        try {
            final String result = doDownload();
            cacheResult(result);
            final JSONObject json = new JSONObject(result);
            listener.requestComplete(json);
        }
        catch (final Exception ex) {
            MainActivity.error("ex: " + ex, ex);
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
        if (cacheFilename == null) return;

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
        final HttpURLConnection conn = HttpFileUploader.connect(
                MainActivity.SITE_STATS_URL, setBoundary );
        if (conn == null) {
            throw new IOException("No connection created");
        }

        // Send POST output.
        final DataOutputStream printout = new DataOutputStream (conn.getOutputStream ());
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
            catch (Exception ex) {
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

            MainActivity.info("siteStats: " + line);
        }
        return result.toString();
    }

}
