package net.wigle.wigleandroid.background;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

import net.wigle.wigleandroid.DBException;
import net.wigle.wigleandroid.DatabaseHelper;
import net.wigle.wigleandroid.MainActivity;
import android.annotation.SuppressLint;
import android.content.Context;
import android.database.Cursor;
import android.os.Bundle;
import android.os.Environment;
import android.support.v4.app.FragmentActivity;

public class KmlWriter extends AbstractBackgroundTask {
    private final Set<String> networks;

    public KmlWriter( final FragmentActivity context, final DatabaseHelper dbHelper ) {
        this( context, dbHelper, null);
    }

    public KmlWriter( final FragmentActivity context, final DatabaseHelper dbHelper, final Set<String> networks ) {
        super(context, dbHelper, "KmlWriter", true);

        // make a safe local copy
        this.networks = (networks == null) ? null : new HashSet<>(networks);
    }

    @SuppressLint("SimpleDateFormat")
    @Override
    protected void subRun() throws IOException {
        final Bundle bundle = new Bundle();


        final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        final SimpleDateFormat fileDateFormat = new SimpleDateFormat("yyyyMMddHHmmss");
        final String filename = "WigleWifi_" + fileDateFormat.format(new Date()) + ".kml";

        final FileOutputStream fos = MainActivity.createFile(context, filename);
        // header
        FileUploaderTask.writeFos( fos, "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<kml xmlns=\"http://www.opengis.net/kml/2.2\"><Document>"
                + "<Style id=\"red\"><IconStyle><Icon><href>http://maps.google.com/mapfiles/ms/icons/red-dot.png</href></Icon></IconStyle></Style>"
                + "<Style id=\"yellow\"><IconStyle><Icon><href>http://maps.google.com/mapfiles/ms/icons/yellow-dot.png</href></Icon></IconStyle></Style>"
                + "<Style id=\"green\"><IconStyle><Icon><href>http://maps.google.com/mapfiles/ms/icons/green-dot.png</href></Icon></IconStyle></Style>"
                + "<Folder><name>Wifi Networks</name>\n" );

        // body
        Cursor cursor = null;
        Status status = null;
        try {
            if ( this.networks == null ) {
                cursor = dbHelper.networkIterator();
                writeKmlFromCursor( fos, cursor, dateFormat, 0, dbHelper.getNetworkCount(), bundle );
            }
            else {
                int count = 0;
                for ( String network : networks ) {
                    // MainActivity.info( "network: " + network );
                    cursor = dbHelper.getSingleNetwork( network );
                    writeKmlFromCursor( fos, cursor, dateFormat, count, networks.size(), bundle );
                    cursor.close();
                    cursor = null;
                    count++;
                }
            }
            status = Status.WRITE_SUCCESS;
        }
        catch ( final InterruptedException ex ) {
            MainActivity.info("Writing Kml Interrupted: " + ex);
        }
        catch ( DBException ex ) {
            dbHelper.deathDialog("Writing Kml", ex);
            status = Status.EXCEPTION;
        }
        catch ( final Exception ex ) {
            ex.printStackTrace();
            MainActivity.error( "ex problem: " + ex, ex );
            MainActivity.writeError( this, ex, context );
            status = Status.EXCEPTION;
            bundle.putString( BackgroundGuiHandler.ERROR, "ex problem: " + ex );
        }
        finally {
            if ( cursor != null ) {
                cursor.close();
            }
        }
        // footer
        FileUploaderTask.writeFos( fos, "</Folder>\n</Document></kml>" );

        fos.close();

        bundle.putString( BackgroundGuiHandler.FILEPATH, MainActivity.getSDPath() + filename );
        bundle.putString( BackgroundGuiHandler.FILENAME, filename );
        MainActivity.info( "done with kml export" );

        // status is null on interrupted
        if ( status != null ) {
            // tell gui
            sendBundledMessage( status.ordinal(), bundle );
        }
    }

    private boolean writeKmlFromCursor( final OutputStream fos, final Cursor cursor, final SimpleDateFormat dateFormat,
                                        long startCount, long totalCount, final Bundle bundle ) throws IOException, InterruptedException {

        int lineCount = 0;

        for ( cursor.moveToFirst(); ! cursor.isAfterLast(); cursor.moveToNext() ) {
            if ( wasInterrupted() ) {
                throw new InterruptedException( "we were interrupted" );
            }

            // bssid,ssid,frequency,capabilities,lasttime,lastlat,lastlon
            final String bssid = cursor.getString(0);
            final String ssid = cursor.getString(1);
            final int frequency = cursor.getInt(2);
            final String capabilities = cursor.getString(3);
            final long lasttime = cursor.getLong(4);
            final double lastlat = cursor.getDouble(5);
            final double lastlon = cursor.getDouble(6);
            final String date = dateFormat.format( new Date( lasttime ) );

            String style = "green";
            if (capabilities.contains("WEP")) {
                style = "yellow";
            }
            if (capabilities.contains("WPA")) {
                style = "red";
            }

            // not unicode. ha ha for them!
            byte[] ssidFiltered = ssid.getBytes( MainActivity.ENCODING );
            filterIllegalXml( ssidFiltered );

            FileUploaderTask.writeFos( fos, "<Placemark>\n<name><![CDATA[" );
            fos.write( ssidFiltered );
            FileUploaderTask.writeFos( fos, "]]></name>\n" );
            FileUploaderTask.writeFos( fos, "<description><![CDATA[BSSID: <b>" + bssid + "</b><br/>"
                    + "Capabilities: <b>" + capabilities + "</b><br/>Frequency: <b>" + frequency + "</b><br/>"
                    + "Timestamp: <b>" + lasttime + "</b><br/>Date: <b>" + date + "</b>]]></description><styleUrl>#" + style + "</styleUrl>\n" );
            FileUploaderTask.writeFos( fos, "<Point>\n" );
            FileUploaderTask.writeFos( fos, "<coordinates>" + lastlon + "," + lastlat + "</coordinates>" );
            FileUploaderTask.writeFos( fos, "</Point>\n</Placemark>\n" );

            lineCount++;
            if ( (lineCount % 1000) == 0 ) {
                MainActivity.info("lineCount: " + lineCount + " of " + totalCount );
            }

            // update UI
            if ( totalCount == 0 ) {
                totalCount = 1;
            }
            final int percentDone = (int)(((lineCount + startCount) * 1000) / totalCount);
            sendPercentTimesTen( percentDone, bundle );
        }

        return true;
    }

    private void filterIllegalXml( byte[] data ) {
        for ( int i = 0; i < data.length; i++ ) {
            byte current = data[i];
            // (0x00, 0x08), (0x0B, 0x1F), (0x7F, 0x84), (0x86, 0x9F)
            //noinspection ConstantConditions
            if ( (current >= 0x00 && current <= 0x08) ||
                    (current >= 0x0B && current <= 0x1F) ||
                    (current >= 0x7F && current <= 0x84) ||
                    (current >= 0x86 && current <= 0x9F)
                    ) {
                data[i] = ' ';
            }
        }
    }

}
