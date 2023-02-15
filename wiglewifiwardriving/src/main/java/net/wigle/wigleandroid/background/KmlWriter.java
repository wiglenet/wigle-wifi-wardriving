package net.wigle.wigleandroid.background;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import net.wigle.wigleandroid.db.DBException;
import net.wigle.wigleandroid.db.DatabaseHelper;
import net.wigle.wigleandroid.MainActivity;
import net.wigle.wigleandroid.model.Network;
import net.wigle.wigleandroid.model.NetworkType;
import net.wigle.wigleandroid.util.FileAccess;
import net.wigle.wigleandroid.util.FileUtility;
import net.wigle.wigleandroid.util.Logging;

import android.database.Cursor;
import android.os.Bundle;
import androidx.fragment.app.FragmentActivity;

import static net.wigle.wigleandroid.util.FileUtility.KML_EXT;
import static net.wigle.wigleandroid.util.FileUtility.WIWI_PREFIX;

public class KmlWriter extends AbstractBackgroundTask {
    private final Set<String> networks;
    private final Set<String> btNetworks;
    private static final String NO_SSID = "(no SSID)";

    private final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.UK);

    public KmlWriter( final FragmentActivity context, final DatabaseHelper dbHelper ) {
        this( context, dbHelper, null, null);
    }

    public KmlWriter( final FragmentActivity context, final DatabaseHelper dbHelper, final Set<String> networks, final Set<String> btNetworks) {
        super(context, dbHelper, "KmlWriter", true);

        // make a safe local copy
        this.networks = (networks == null) ? null : new HashSet<>(networks);
        this.btNetworks = (btNetworks == null) ? null : new HashSet<>(btNetworks);
    }

    @Override
    protected void subRun() throws IOException {
        final Bundle bundle = new Bundle();


        final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
        final SimpleDateFormat fileDateFormat = new SimpleDateFormat("yyyyMMddHHmmss", Locale.US);
        final String filename = WIWI_PREFIX + fileDateFormat.format(new Date()) + KML_EXT;

        final FileOutputStream fos = FileUtility.createFile(context, filename, false);
        // header
        FileAccess.writeFos( fos, "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<kml xmlns=\"http://www.opengis.net/kml/2.2\"><Document>"
                + "<Style id=\"red\"><IconStyle><Icon><href>http://maps.google.com/mapfiles/ms/icons/red.png</href></Icon></IconStyle></Style>"
                + "<Style id=\"yellow\"><IconStyle><Icon><href>http://maps.google.com/mapfiles/ms/icons/yellow.png</href></Icon></IconStyle></Style>"
                + "<Style id=\"green\"><IconStyle><Icon><href>http://maps.google.com/mapfiles/ms/icons/green.png</href></Icon></IconStyle></Style>"
                + "<Style id=\"blue\"><IconStyle><Icon><href>http://maps.google.com/mapfiles/ms/icons/blue.png</href></Icon></IconStyle></Style>"
                + "<Style id=\"pink\"><IconStyle><Icon><href>http://maps.google.com/mapfiles/ms/icons/pink.png</href></Icon></IconStyle></Style>"
                + "<Style id=\"ltblue\"><IconStyle><Icon><href>http://maps.google.com/mapfiles/ms/icons/lightblue.png</href></Icon></IconStyle></Style>"
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
                cursor = dbHelper.networkIterator(DatabaseHelper.NetworkFilter.WIFI);
                long wifiCount = writeKmlFromCursor( fos, cursor, dateFormat, 0, dbHelper.getNetworkCount(), bundle);
                cursor.close();
                cursor = dbHelper.networkIterator(DatabaseHelper.NetworkFilter.CELL);
                FileAccess.writeFos( fos, "</Folder>\n<Folder><name>Cellular Networks</name>\n" );
                cursor.close();
                long cellCount = writeKmlFromCursor( fos, cursor, dateFormat, wifiCount, dbHelper.getNetworkCount(), bundle);
                cursor = dbHelper.networkIterator(DatabaseHelper.NetworkFilter.BT);
                FileAccess.writeFos( fos, "</Folder>\n<Folder><name>Bluetooth Networks</name>\n" );
                cursor.close();
                long btCount = writeKmlFromCursor( fos, cursor, dateFormat, wifiCount+cellCount, dbHelper.getNetworkCount(), bundle);
                Logging.info("Full KML Export: "+dbHelper.getNetworkCount()+" per db, wrote "+(btCount+cellCount+wifiCount)+" total.");
            } else {
                long count = 0;
                long btFailCount = 0L;
                long wifiFailCount = 0L;
                Set<String> cellSet = new HashSet<>();

                final int totalNets = networks.size() + (btNetworks==null?0:btNetworks.size());
                for (String network : networks) {
                    // DEBUG: MainActivity.info( "network: " + network );
                    cursor = dbHelper.getSingleNetwork(network, DatabaseHelper.NetworkFilter.WIFI);
                    final long found = writeKmlFromCursor(fos, cursor, dateFormat, count, totalNets, bundle);
                    // ALIBI: assume this was a cell net, if it didn't match for WiFi - avoid full second iteration
                    if (0L == found) {
                        //DEBUG: MainActivity.info("Didn't find network "+network+"; adding to cells.");
                        cellSet.add(network);
                        wifiFailCount++;
                    }
                    cursor.close();
                    cursor = null;
                    if (found > 0) {
                        count++;
                    }
                }
                FileAccess.writeFos( fos, "</Folder>\n<Folder><name>Cellular Networks</name>\n" );
                //ALIBI: cell networks are still mixed into the lamestatic list of WiFi for now. this can get more efficient when we partition them.
                for ( String network : cellSet ) {
                    // MainActivity.info( "network: " + network );
                    cursor = dbHelper.getSingleNetwork( network, DatabaseHelper.NetworkFilter.CELL);

                    final long found = writeKmlFromCursor(fos, cursor, dateFormat, count, totalNets, bundle);
                    cursor.close();
                    cursor = null;
                    if (found > 0) {
                        count++;
                    } else {
                        Logging.warn("unfound cell: ["+network+"]");
                    }
                }
                FileAccess.writeFos( fos, "</Folder>\n<Folder><name>Bluetooth Networks</name>\n" );
                if (btNetworks != null) {
                    for (String network : btNetworks) {
                        // MainActivity.info( "network: " + network );
                        cursor = dbHelper.getSingleNetwork(network, DatabaseHelper.NetworkFilter.BT);

                        final long found = writeKmlFromCursor(fos, cursor, dateFormat, count, totalNets, bundle);
                        if (0L == found) {
                            btFailCount++;
                            Logging.error("unfound BT network: [" + network + "]");
                        }
                        cursor.close();
                        cursor = null;
                        count++;
                    }
                }
                Logging.info("Completed; WiFi Fail: "+wifiFailCount+ " BT Fail: "+btFailCount
                        + " from total count: "+totalNets+" (non-bt-networks: "+ networks.size()
                        + " btnets:" + (btNetworks != null?btNetworks.size():"null")+")");
            }
            status = Status.WRITE_SUCCESS;
        }
        catch ( final InterruptedException ex ) {
            Logging.info("Writing Kml Interrupted: " + ex);
        }
        catch ( DBException ex ) {
            dbHelper.deathDialog("Writing Kml", ex);
            status = Status.EXCEPTION;
        }
        catch ( final Exception ex ) {
            ex.printStackTrace();
            Logging.error( "ex problem: " + ex, ex );
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
        FileAccess.writeFos( fos, "</Folder>\n</Document></kml>" );

        fos.close();

        //WARNING: ignored if no SD, so this is ok, but misleading...
        bundle.putString( BackgroundGuiHandler.FILEPATH, FileUtility.getSDPath() + filename );
        bundle.putString( BackgroundGuiHandler.FILENAME, filename );
        Logging.info( "done with kml export" );

        // status is null on interrupted
        if ( status != null ) {
            // tell gui
            sendBundledMessage( status.ordinal(), bundle );
        }
    }

    private long writeKmlFromCursor( final OutputStream fos, final Cursor cursor, final SimpleDateFormat dateFormat,
                                        long startCount, long totalCount, final Bundle bundle) throws IOException, InterruptedException {

        long lineCount = 0;

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

            final NetworkType type = NetworkType.typeForCode(typecode);

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

            } else if (NetworkType.isCellType(type)) {
                style = "pink";
            } else {
                style = "zeroConfidence";
            }

            //Regardless of reported quality, this freaks out
            if (lasttime == 0L) {
                style = "zeroConfidence";
            }

            byte[] sanitizedSsid = sanitizeKmlString(ssid);
            if (sanitizedSsid.length == 0) {
                sanitizedSsid = NO_SSID.getBytes(MainActivity.ENCODING);
            }

            if (type.equals(NetworkType.WIFI)) {

                final String encStatus = "Encryption: " + encryptionStringForCapabilities(capabilities) + "\n";

                FileAccess.writeFos(fos, "<Placemark>\n<name><![CDATA[");
                fos.write(sanitizedSsid);
                FileAccess.writeFos(fos, "]]></name>\n");
                FileAccess.writeFos(fos, "<description><![CDATA[Network ID: " + bssid + "\n"
                        + "Capabilities: " + capabilities + "\n" // ALIBI: not available on server
                        + "Frequency: " + frequency + "\n"       // ALIBI: not in server-side
                        + "Timestamp: " + lasttime + "\n"        // ALIBI: not in server-side
                        + "Time: " + date + "\n"                 // NOTE: server side contains server timezone
                        + "Signal: " + bestlevel + "\n"
                        + "Type: " + type.name() + "\n"
                        + encStatus
                        + "]]>"
                        + "</description><styleUrl>#" + style + "</styleUrl>\n");
                FileAccess.writeFos(fos, "<Point>\n");
                FileAccess.writeFos(fos, "<coordinates>" + lastlon + "," + lastlat + "</coordinates>");
                FileAccess.writeFos(fos, "</Point>\n</Placemark>\n");

            } else if (NetworkType.isCellType(type)) {
                FileAccess.writeFos(fos, "<Placemark>\n<name><![CDATA[");
                fos.write(sanitizedSsid);
                FileAccess.writeFos(fos, "]]></name>\n");
                FileAccess.writeFos(fos, "<description><![CDATA[Network ID: " + bssid + "\n"
                        + "Capabilities: " + capabilities + "\n" // ALIBI: not available on server
                        + "Frequency: " + frequency + "\n"       // ALIBI: not in server-side
                        + "Timestamp: " + lasttime + "\n"        // ALIBI: not in server-side
                        + "Time: " + date + "\n"                 // NOTE: server side contains server timezone
                        + "Signal: " + bestlevel + "\n"
                        + "Type: " + type.name()
                        + "]]>"
                        + "</description><styleUrl>#" + style + "</styleUrl>\n");
                FileAccess.writeFos(fos, "<Point>\n");
                FileAccess.writeFos(fos, "<coordinates>" + lastlon + "," + lastlat + "</coordinates>");
                FileAccess.writeFos(fos, "</Point>\n</Placemark>\n");

            } else if (NetworkType.isBtType(type)) {
                FileAccess.writeFos(fos, "<Placemark>\n<name><![CDATA[");
                fos.write(sanitizedSsid);
                FileAccess.writeFos(fos, "]]></name>\n");
                FileAccess.writeFos(fos, "<description><![CDATA[Network ID: " + bssid + "\n"
                        + "Capabilities: " + capabilities + "\n" // ALIBI: not available on server
                        + "Frequency: " + frequency + "\n"       // ALIBI: not in server-side
                        + "Timestamp: " + lasttime + "\n"        // ALIBI: not in server-side
                        + "Time: " + date + "\n"                 // NOTE: server side contains server timezone
                        + "Signal: " + bestlevel + "\n"
                        + "Type: " + type.name()
                        + "]]>"
                        + "</description><styleUrl>#" + style + "</styleUrl>\n");
                FileAccess.writeFos(fos, "<Point>\n");
                FileAccess.writeFos(fos, "<coordinates>" + lastlon + "," + lastlat + "</coordinates>");
                FileAccess.writeFos(fos, "</Point>\n</Placemark>\n");
            } else {
                Logging.warn("unknown network type "+type+"for network: "+bssid);
            }

            lineCount++;
            if ( (lineCount % 1000) == 0 ) {
                Logging.info("lineCount: " + lineCount + " of " + totalCount );
            }

            // update UI
            if ( totalCount == 0 ) {
                totalCount = 1;
            }
            final int percentDone = (int)(((lineCount + startCount) * 1000) / totalCount);
            sendPercentTimesTen( percentDone, bundle );
        }

        return lineCount;
    }

    private static String encryptionStringForCapabilities(final String capabilities) {
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

    // Sanitize string by escaping reserved XML tokens and replace invalid charset ranges
    private byte[] sanitizeKmlString(String value) throws UnsupportedEncodingException {
        // Escape reserved KML/XML tokens in order to prevent XXE attacks. The text is inserted into
        // a CDATA field, only "]]>" sanitization is required and the rest is redundant.
        final String escapedValue = value
                .replaceAll("]]>", "]]&gt;")
                .replaceAll("&", "&amp;")
                .replaceAll(">", "&gt;")
                .replaceAll("<", "&lt;")
                .replaceAll("\"", "&quot;")
                .replaceAll("'", "&apos;");

        // "not unicode. ha ha for them!"
        final byte[] escapedValueBytes = escapedValue.getBytes( MainActivity.ENCODING );

        // Filtering the illegal symbols from the given charset. The symbols will be replaced with a space character.
        // Targeted ranges: (0x00, 0x08), (0x0B, 0x1F), (0x7F, 0x84), (0x86, 0x9F)
        final byte replacementGlyph = ' ';
        for (int i = 0; i < escapedValueBytes.length; i++) {
            byte currentGlyph = escapedValueBytes[i];

            if (isBetween(currentGlyph, 0x00, 0x08)) escapedValueBytes[i] = replacementGlyph;
            if (isBetween(currentGlyph, 0x0B, 0x1F)) escapedValueBytes[i] = replacementGlyph;
            if (isBetween(currentGlyph, 0x7F, 0x84)) escapedValueBytes[i] = replacementGlyph;
            if (isBetween(currentGlyph, 0x86, 0x9F)) escapedValueBytes[i] = replacementGlyph;
        }

        return escapedValueBytes;
    }

    // Check if the given values is between two given values (inclusive)
    private boolean isBetween(byte value, int min, int max) {
        return value >= min && value <= max;
    }
}
