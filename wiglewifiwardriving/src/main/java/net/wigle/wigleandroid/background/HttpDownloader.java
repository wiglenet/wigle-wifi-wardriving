package net.wigle.wigleandroid.background;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URLEncoder;

import net.wigle.wigleandroid.DatabaseHelper;
import net.wigle.wigleandroid.MainActivity;
import net.wigle.wigleandroid.model.Network;
import net.wigle.wigleandroid.model.NetworkType;
import android.location.Location;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;

public class HttpDownloader extends AbstractBackgroundTask {
    private final TransferListener listener;

    public HttpDownloader( final FragmentActivity context, final DatabaseHelper dbHelper,
                           final TransferListener listener ) {

        super(context, dbHelper, "HttpDL", true);
        this.listener = listener;
    }

    @Override
    protected void subRun() throws IOException, InterruptedException {
        Status status = Status.UNKNOWN;
        final Bundle bundle = new Bundle();
        try {
            final String username = getUsername();
            final String password = getPassword();
            status = validateUserPass( username, password );
            if ( status == null ) {
                status = doDownload( username, password );
            }

        }
        catch ( final InterruptedException ex ) {
            MainActivity.info("Download Interrupted: " + ex);
        }
        catch ( final Exception ex ) {
            ex.printStackTrace();
            MainActivity.error( "ex problem: " + ex, ex );
            MainActivity.writeError( this, ex, context );
            status = Status.EXCEPTION;
            bundle.putString( BackgroundGuiHandler.ERROR, "ex problem: " + ex );
        }
        finally {
            // tell the listener
            listener.transferComplete();
        }

        if ( status == null ) {
            status = Status.FAIL;
        }

        // tell the gui thread
        sendBundledMessage( status.ordinal(), bundle );
    }

    private Status doDownload( final String username, final String password)
            throws IOException, InterruptedException {

        final boolean setBoundary = false;
        final HttpURLConnection conn = HttpFileUploader.connect(
                MainActivity.OBSERVED_URL, setBoundary );
        if (conn == null) {
            throw new IOException("No connection created");
        }

        // Send POST output.
        final DataOutputStream printout = new DataOutputStream (conn.getOutputStream ());
        String content = "observer=" + URLEncoder.encode ( username, HttpFileUploader.ENCODING ) +
                "&password=" + URLEncoder.encode ( password, HttpFileUploader.ENCODING );
        printout.writeBytes( content );
        printout.flush();
        printout.close();

        // get response data
        final BufferedReader input = new BufferedReader(
                new InputStreamReader( HttpFileUploader.getInputStream( conn ), HttpFileUploader.ENCODING) );
        try {
            insertObserved( input );
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

    private void insertObserved( final BufferedReader reader )
            throws IOException, InterruptedException {
        final Bundle bundle = new Bundle();
        String line;
        int lineCount = 0;
        int totalCount = -1;
        final String COUNT_TAG = "count=";

        while ( (line = reader.readLine()) != null ) {
            if ( wasInterrupted() ) {
                throw new InterruptedException( "we were interrupted" );
            }

            if ( totalCount < 0 ) {
                if ( ! line.startsWith(COUNT_TAG) ) {
                    continue;
                }
                else {
                    totalCount = Integer.parseInt(line.substring(COUNT_TAG.length()));
                    MainActivity.info("observed totalCount: " + totalCount);
                }
            }
            if ( line.length() != 12 || line.startsWith( "<" ) ) {
                continue;
            }

            // re-colon
            StringBuilder builder = new StringBuilder(15);
            for ( int i = 0; i < 12; i++ ) {
                builder.append(line.charAt(i));
                if ( i < 11 && (i%2) == 1 ) {
                    builder.append(":");
                }
            }

            final String bssid = builder.toString();
            // MainActivity.info("line: " + line + " bssid: " + bssid);

            // do the insert
            final String ssid = "";
            final int frequency = 0;
            final String capabilities = "";
            final int level = 0;
            final Network network = new Network(bssid, ssid, frequency, capabilities, level, NetworkType.WIFI);
            final Location location = new Location("wigle");
            final boolean newForRun = true;
            dbHelper.blockingAddObservation( network, location, newForRun );

            lineCount++;
            if ( (lineCount % 1000) == 0 ) {
                MainActivity.info("lineCount: " + lineCount + " of " + totalCount );
            }

            // update UI
            if ( totalCount == 0 ) {
                totalCount = 1;
            }
            final int percentDone = (lineCount * 1000) / totalCount;
            sendPercentTimesTen( percentDone, bundle );
        }
    }

}
