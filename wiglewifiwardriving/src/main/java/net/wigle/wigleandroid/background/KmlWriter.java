package net.wigle.wigleandroid.background;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import net.wigle.wigleandroid.db.DBException;
import net.wigle.wigleandroid.db.DatabaseHelper;
import net.wigle.wigleandroid.MainActivity;
import net.wigle.wigleandroid.model.NetworkType;

import android.annotation.SuppressLint;
import android.database.Cursor;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;

public class KmlWriter extends AbstractBackgroundTask {
    private final Set<String> networks;
    private static final String NO_SSID = "(no SSID)";

    private final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.UK);

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

        final FileOutputStream fos = MainActivity.createFile(context, filename, false);
        // header
        ObservationUploader.writeFos( fos, "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<kml xmlns=\"http://www.opengis.net/kml/2.2\"><Document>"
                + "<Style id=\"red\"><IconStyle><Icon><href>http://maps.google.com/mapfiles/ms/icons/red.png</href></Icon></IconStyle></Style>"
                + "<Style id=\"yellow\"><IconStyle><Icon><href>http://maps.google.com/mapfiles/ms/icons/yellow.png</href></Icon></IconStyle></Style>"
                + "<Style id=\"green\"><IconStyle><Icon><href>http://maps.google.com/mapfiles/ms/icons/green.png</href></Icon></IconStyle></Style>"
                + "<Style id=\"blue\"><IconStyle><Icon><href>http://maps.google.com/mapfiles/ms/icons/blue.png</href></Icon></IconStyle></Style>"
                + "<Style id=\"pink\"><IconStyle><Icon><href>http://maps.google.com/mapfiles/ms/icons/red.png</href></Icon></IconStyle></Style>"
                + "<Style id=\"ltblue\"><IconStyle><Icon><href>http://maps.google.com/mapfiles/ms/icons/ltblue.png</href></Icon></IconStyle></Style>"
                + "<Style id=\"zeroConfidence\"><IconStyle><Icon><href>https://maps.google.com/mapfiles/kml/pal2/icon18.png</href></Icon></IconStyle></Style>"
                + "<Folder><name>Wifi Networks</name>\n" );

        // confidence styles from online generator; not applicable here because we don't have qos?
        /*+ "<Style id=\"highConfidence\"><IconStyle id=\"highConfidenceStyle\"> <scale>1.0</scale><heading>0.0</heading><Icon><href>http://maps.google.com/mapfiles/kml/pushpin/grn-pushpin.png</href><refreshInterval>0.0</refreshInterval><viewRefreshTime>0.0</viewRefreshTime><viewBoundScale>0.0</viewBoundScale></Icon></IconStyle></Style>"
        + "<Style id=\"mediumConfidence\"> <IconStyle id=\"medConfidenceStyle\"> <scale>1.0</scale><heading>0.0</heading><Icon><href>http://maps.google.com/mapfiles/kml/pushpin/ylw-pushpin.png</href><refreshInterval>0.0</refreshInterval><viewRefreshTime>0.0</viewRefreshTime><viewBoundScale>0.0</viewBoundScale></Icon></IconStyle></Style>"
        + "<Style id=\"lowConfidence\"> <IconStyle id=\"lowConfidenceStyle\"> <scale>1.0</scale><heading>0.0</heading><Icon><href>http://maps.google.com/mapfiles/kml/pushpin/red-pushpin.png</href><refreshInterval>0.0</refreshInterval><viewRefreshTime>0.0</viewRefreshTime><viewBoundScale>0.0</viewBoundScale></Icon></IconStyle></Style>"
        + "<Style id=\"zeroConfidence\"> <IconStyle id=\"zeroConfidenceStyle\"> <scale>1.0</scale><heading>0.0</heading><Icon><href>http://maps.google.com/mapfiles/kml/pushpin/wht-pushpin.png</href><refreshInterval>0.0</refreshInterval><viewRefreshTime>0.0</viewRefreshTime><viewBoundScale>0.0</viewBoundScale></Icon></IconStyle></Style>"*/


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
        ObservationUploader.writeFos( fos, "</Folder>\n</Document></kml>" );

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

            // bssid,ssid,frequency,capabilities,lasttime,lastlat,lastlon,bestlevel,typecode
            final String bssid = cursor.getString(0);
            final String ssid = cursor.getString(1);
            final int frequency = cursor.getInt(2);
            final String capabilities = cursor.getString(3);
            final long lasttime = cursor.getLong(4);
            final double lastlat = cursor.getDouble(5);
            final double lastlon = cursor.getDouble(6);
            final int bestlevel = cursor.getInt(7);
            final String typecode = cursor.getString( 8);
            final String date = sdf.format(new Date(lasttime));

            NetworkType type = NetworkType.typeForCode(typecode);

            String style = "green";

            if (type == null) {
                style = "zeroConfidence";
            } else if (NetworkType.WIFI.equals(type)) {
                if (capabilities.contains("WEP")) {
                    style = "yellow";
                }
                if (capabilities.contains("WPA")) {
                    style = "red";
                }
            } else if (NetworkType.BLE.equals(type)) {
                style = "ltblue";
            } else if (NetworkType.BT.equals(type)) {
                style = "blue";

            } else if (NetworkType.CDMA.equals(type) || NetworkType.GSM.equals(type) || NetworkType.LTE.equals(type) || NetworkType.WCDMA.equals(type)) {
                style = "pink";
            } else {
                style = "zeroConfidence";
            }

            //Regardless of reported quality, this freaks out
            if (lasttime == 0L) {
                style = "zeroConfidence";
            }

            // not unicode. ha ha for them!
            byte[] ssidFiltered = ssid.getBytes( MainActivity.ENCODING );
            filterIllegalXml( ssidFiltered );
            if (ssidFiltered.length == 0) {
                ssidFiltered = NO_SSID.getBytes( MainActivity.ENCODING);
            }

            final String encStatus = "Encryption: " + encryptionStringForCapabilities(capabilities) + "\n";

            ObservationUploader.writeFos( fos, "<Placemark>\n<name><![CDATA[" );
            fos.write( ssidFiltered );
            ObservationUploader.writeFos( fos, "]]></name>\n" );
            ObservationUploader.writeFos( fos, "<description><![CDATA[Network ID: " + bssid + "\n"
                    + "Capabilities: " + capabilities + "\n" // ALIBI: not available on server
                    + "Frequency: " + frequency + "\n"       // ALIBI: not in server-side
                    + "Timestamp: " + lasttime + "\n"        // ALIBI: not in server-side
                    + "Time: " + date + "\n"                 // NOTE: server side contains server timezone
                    + "Signal: " + bestlevel + "\n"
                    + "Type: " + type.name() + "\n"          // TODO: add to server side
                    + encStatus
                    + "]]>"
                    +"</description><styleUrl>#" + style + "</styleUrl>\n" );
            ObservationUploader.writeFos( fos, "<Point>\n" );
            ObservationUploader.writeFos( fos, "<coordinates>" + lastlon + "," + lastlat + "</coordinates>" );
            ObservationUploader.writeFos( fos, "</Point>\n</Placemark>\n" );

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

    private String encryptionStringForCapabilities(final String capabilities) {
        if (capabilities.contains("WPA3")) {
            return "WPA3";
        } else if (capabilities.contains("WPA2")) {
            return "WPA2";
        } else if (capabilities.contains("WPA")) {
            return "WPA";
        } else if (capabilities.contains("WEP")) {
            return "WEP";
        } else {
            return "Unknown";
        }
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
