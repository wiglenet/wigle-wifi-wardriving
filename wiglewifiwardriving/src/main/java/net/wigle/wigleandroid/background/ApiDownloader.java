package net.wigle.wigleandroid.background;

import android.location.Location;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;

import net.wigle.wigleandroid.DatabaseHelper;
import net.wigle.wigleandroid.MainActivity;
import net.wigle.wigleandroid.Network;
import net.wigle.wigleandroid.NetworkType;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URLEncoder;

public class ApiDownloader extends AbstractBackgroundTask {

    public ApiDownloader(final FragmentActivity context, final DatabaseHelper dbHelper) {

        super(context, dbHelper, "ApiDL", false);
    }

    @Override
    protected void subRun() throws IOException, InterruptedException {
        Status status = Status.UNKNOWN;
        final Bundle bundle = new Bundle();

        try {
            doDownload();
        }
        catch (final Exception ex) {
            MainActivity.error("ex: " + ex, ex);
        }

        // tell the gui thread
        sendBundledMessage( status.ordinal(), bundle );
    }

    private Status doDownload() throws IOException, InterruptedException {

        final boolean setBoundary = false;
        final HttpURLConnection conn = HttpFileUploader.connect(
                MainActivity.SITE_STATS_URL, setBoundary );
        if (conn == null) {
            throw new IOException("No connection created");
        }

        // Send POST output.
        final DataOutputStream printout = new DataOutputStream (conn.getOutputStream ());
//        String content = "observer=" + URLEncoder.encode ( username, HttpFileUploader.ENCODING ) +
//                "&password=" + URLEncoder.encode ( password, HttpFileUploader.ENCODING );
//        printout.writeBytes( content );
        printout.flush();
        printout.close();

        // get response data
        final BufferedReader input = new BufferedReader(
                new InputStreamReader( HttpFileUploader.getInputStream( conn ), HttpFileUploader.ENCODING) );
        try {
            insertSiteStats(input);
        }
        finally {
            try {
                input.close();
            }
            catch (Exception ex) {
                MainActivity.warn("Exception closing downloader reader: " + ex, ex);
            }
        }
        return Status.WRITE_SUCCESS;
    }

    private void insertSiteStats( final BufferedReader reader ) throws IOException, InterruptedException {
        final Bundle bundle = new Bundle();
        String line;

        while ( (line = reader.readLine()) != null ) {
            if ( wasInterrupted() ) {
                throw new InterruptedException( "we were interrupted" );
            }

            MainActivity.info("siteStats: " + line);
        }
    }

}
