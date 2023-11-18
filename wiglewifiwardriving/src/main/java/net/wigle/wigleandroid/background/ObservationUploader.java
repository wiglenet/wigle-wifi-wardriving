package net.wigle.wigleandroid.background;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.os.Bundle;
import android.preference.PreferenceManager;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;

import net.wigle.wigleandroid.db.DBException;
import net.wigle.wigleandroid.db.DatabaseHelper;
import net.wigle.wigleandroid.MainActivity;
import net.wigle.wigleandroid.R;
import net.wigle.wigleandroid.TokenAccess;
import net.wigle.wigleandroid.WiGLEAuthException;
import net.wigle.wigleandroid.model.Network;
import net.wigle.wigleandroid.net.WiGLEApiManager;
import net.wigle.wigleandroid.util.FileAccess;
import net.wigle.wigleandroid.util.FileUtility;
import net.wigle.wigleandroid.util.Logging;
import net.wigle.wigleandroid.util.PreferenceKeys;
import net.wigle.wigleandroid.util.UrlConfig;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.DuplicateHeaderMode;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.net.ConnectException;
import java.net.UnknownHostException;
import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.channels.ClosedByInterruptException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CodingErrorAction;
import java.text.DecimalFormat;
import java.text.FieldPosition;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

import javax.net.ssl.SSLException;

/**
 * replacement file upload task
 * Created by arkasha on 2/6/17.
 */

public class ObservationUploader extends AbstractProgressApiRequest {

    private static final String COMMA = ",";
    private static final String NEWLINE = "\n";

    private static final String ENCODING = "UTF-8";

    private static final CSVFormat CSV_FORMAT;

    private final boolean justWriteFile;
    private final boolean writeEntireDb;
    private final boolean writeRun;

    public final static String CSV_COLUMN_HEADERS = "MAC,SSID,AuthMode,FirstSeen,Channel,Frequency,RSSI,CurrentLatitude,CurrentLongitude,AltitudeMeters,AccuracyMeters,RCOIs,MfgrId,Type";

    private static class CountStats {
        int byteCount;
        int lineCount;
    }

    static {
        // Use the default (RFC4180) settings, except no carriage return on line end
        final CSVFormat.Builder builder = CSVFormat.Builder.create();
        builder.setDelimiter(',');
        builder.setQuote('"');
        builder.setRecordSeparator("\n"); // only change from default, not "\r\n"
        builder.setIgnoreEmptyLines(true);
        builder.setDuplicateHeaderMode(DuplicateHeaderMode.ALLOW_ALL);
        CSV_FORMAT = builder.build();
    }

    public ObservationUploader(final FragmentActivity context,
                               final DatabaseHelper dbHelper, final ApiListener listener,
                               boolean justWriteFile, boolean writeEntireDb, boolean writeRun) {
        super(context, dbHelper, "ApiUL", null, UrlConfig.FILE_POST_URL, false,
                true, false, false,
                AbstractApiRequest.REQUEST_POST, listener, true);
        this.justWriteFile = justWriteFile;

        if (writeRun && writeEntireDb) {
            throw new IllegalArgumentException("Cannot specify both individual run and entire db");
        }
        this.writeEntireDb = writeEntireDb;
        this.writeRun = writeRun;
    }

    @Override
    protected void subRun() throws IOException, InterruptedException, WiGLEAuthException {
        try {
            if ( justWriteFile ) {
                justWriteFile();
            } else {
                doRun();
            }
        } catch ( final InterruptedException ex ) {
            Logging.info( "file upload interrupted" );
        } catch (final WiGLEAuthException waex) {
            // ALIBI: allow auth exception through
            throw waex;
        } catch ( final Throwable throwable ) {
            MainActivity.writeError( Thread.currentThread(), throwable, context );
            throw new RuntimeException( "ObservationUploader throwable: " + throwable, throwable );
        } finally {
            // tell the listener
            if (listener != null) {
                listener.requestComplete(null, false);
            }
        }

    }

    private void doRun() throws InterruptedException, WiGLEAuthException {
        final String username = getUsername();
        final String password = getPassword();

        Status status = null;
        final Bundle bundle = new Bundle();
        if (!validAuth()) {
            status = validateUserPass(username, password);
        }
        if ( status == null ) {
            status = doUpload(bundle);
        }
        // tell the gui thread
        sendBundledMessage( status.ordinal(), bundle );
    }

    /**
     * override base startDownload - but instead perform an upload
     * TODO: a misnomer, really
     * @param fragment the fragment from which the upload was started
     * @throws WiGLEAuthException
     */
    @Override
    public void startDownload(final Fragment fragment) throws WiGLEAuthException {
        // download token if needed
        SharedPreferences prefs;
        if (null != fragment) {
            Activity a = fragment.getActivity();
            if (a != null) {
                prefs = fragment.getActivity().getSharedPreferences(
                        PreferenceKeys.SHARED_PREFS, 0);
            } else {
                prefs = null;
                Logging.error("Failed to get activity for non-null fragment - prefs access failed on upload.");
            }
        } else {
            prefs = PreferenceManager.getDefaultSharedPreferences(MainActivity.getMainActivity().getApplicationContext());
        }
        if (prefs != null) {
            final boolean beAnonymous = prefs.getBoolean(PreferenceKeys.PREF_BE_ANONYMOUS, false);
            final String authName = prefs.getString(PreferenceKeys.PREF_AUTHNAME, null);
            final String userName = prefs.getString(PreferenceKeys.PREF_USERNAME, null);
            final String userPass = prefs.getString(PreferenceKeys.PREF_PASSWORD, null);
            Logging.info("authName: " + authName);
            if ((!beAnonymous) && (authName == null) && (userName != null) && (userPass != null)) {
                Logging.info("No authName, going to request token");
                if (null != fragment) {
                    downloadTokenAndStart(fragment);
                }
            } else {
                start();
            }
        }
    }

    /**
     * upload guts. lifted from FileUploaderTask
     * @return the Status of the upload
     * @throws InterruptedException if the upload is interrupted
     */
    private Status doUpload( final Bundle bundle )
            throws InterruptedException {

        Status status;

        final Object[] fileFilename = new Object[2];
        try (final OutputStream fos = FileAccess.getOutputStream( context, bundle, fileFilename )) {
            final File file = (File) fileFilename[0];
            final String filename = (String) fileFilename[1];

            // write file
            ObservationUploader.CountStats countStats = new ObservationUploader.CountStats();
            long maxId = writeFile( fos, bundle, countStats );

            final Map<String,String> params = new HashMap<>();

            final SharedPreferences prefs = context.getSharedPreferences( PreferenceKeys.SHARED_PREFS, 0);
            if ( prefs.getBoolean(PreferenceKeys.PREF_DONATE, false) ) {
                params.put("donate","on");
            }
            final boolean beAnonymous = prefs.getBoolean(PreferenceKeys.PREF_BE_ANONYMOUS, false);
            final String authName = prefs.getString(PreferenceKeys.PREF_AUTHNAME, null);
            if (!beAnonymous && null == authName) {
                return Status.BAD_LOGIN;
            }
            final String userName = prefs.getString(PreferenceKeys.PREF_USERNAME, null);
            final String token = TokenAccess.getApiToken(prefs);

            // don't upload empty files
            if ( countStats.lineCount == 0 && ! "ark-mobile".equals(userName) &&
                    ! "bobzilla".equals(userName) ) {
                return Status.EMPTY_FILE;
            }
            Logging.info("preparing upload...");

            // show on the UI
            sendBundledMessage( Status.UPLOADING.ordinal(), bundle );

            // send file
            final boolean hasSD = FileUtility.hasSD();

            final String absolutePath = hasSD ? file.getAbsolutePath() : context.getFileStreamPath(filename).getAbsolutePath();

            Logging.info("authName: " + authName);

            if (beAnonymous) {
                Logging.info("anonymous upload");
            }

            final String response = OkFileUploader.upload(UrlConfig.FILE_POST_URL, absolutePath,
                    "file", params, authName, token, getHandler());

            if ( ! prefs.getBoolean(PreferenceKeys.PREF_DONATE, false) ) {
                if ( response != null && response.indexOf("donate=Y") > 0 ) {
                    final SharedPreferences.Editor editor = prefs.edit();
                    editor.putBoolean( PreferenceKeys.PREF_DONATE, true );
                    editor.apply();
                }
            }

            //TODO: any reason to parse this JSON object? all we care about are two strings.
            Logging.info(response);
            if ( response != null && response.indexOf("\"success\":true") > 0 ) {
                status = Status.SUCCESS;

                // save in the prefs
                final SharedPreferences.Editor editor = prefs.edit();
                editor.putLong( PreferenceKeys.PREF_DB_MARKER, maxId );
                editor.putLong( PreferenceKeys.PREF_MAX_DB, maxId );
                editor.putLong( PreferenceKeys.PREF_NETS_UPLOADED, dbHelper.getNetworkCount() );
                editor.apply();
            } else if ( response != null && response.indexOf("File upload failed.") > 0 ) {
                status = Status.FAIL;
            } else {
                String error;
                if ( response != null && response.trim().equals( "" ) ) {
                    error = "no response from server";
                } else {
                    error = "response: " + response;
                }
                Logging.error( error );
                bundle.putString( BackgroundGuiHandler.ERROR, error );
                status = Status.FAIL;
            }
        } catch ( final InterruptedException ex ) {
            Logging.info("ObservationUploader interrupted");
            throw ex;

        } catch (final ClosedByInterruptException | UnknownHostException | ConnectException | FileNotFoundException ex) {
            Logging.error( "connection problem: " + ex, ex );
            ex.printStackTrace();
            status = Status.EXCEPTION;
            bundle.putString( BackgroundGuiHandler.ERROR, context.getString(R.string.no_wigle_conn) );
        } catch (final SSLException ex) {
            Logging.error( "security problem: " + ex, ex );
            ex.printStackTrace();
            status = Status.EXCEPTION;
            bundle.putString( BackgroundGuiHandler.ERROR, context.getString(R.string.no_secure_wigle_conn) );
        } catch ( final IOException ex ) {
            ex.printStackTrace();
            Logging.error( "io problem: " + ex, ex );
            status = Status.EXCEPTION;
            bundle.putString( BackgroundGuiHandler.ERROR, "io problem: " + ex );
        } catch ( final Exception ex ) {
            ex.printStackTrace();
            Logging.error( "ex problem: " + ex, ex );
            MainActivity.writeError( this, ex, context, "Has data connection: " + WiGLEApiManager.hasDataConnection(context) );
            status = Status.EXCEPTION;
            bundle.putString( BackgroundGuiHandler.ERROR, "ex problem: " + ex );
        }

        return status;
    }

    /**
     * Given a stream of observations, write a file.
     * (directly lifted from FileUploaderTask)
     * @return the Status of the write operation
     */
    public Status justWriteFile() {
        Status status = null;
        final ObservationUploader.CountStats countStats = new ObservationUploader.CountStats();
        final Bundle bundle = new Bundle();

        try {
            try (OutputStream fos = FileAccess.getOutputStream(context, bundle, new Object[2])) {
                writeFile(fos, bundle, countStats);
                // show on the UI
                status = Status.WRITE_SUCCESS;
                sendBundledMessage(status.ordinal(), bundle);
            }
        }
        catch ( InterruptedException ex ) {
            Logging.info("justWriteFile interrupted: " + ex);
        }
        catch ( IOException ex ) {
            ex.printStackTrace();
            Logging.error( "io problem: " + ex, ex );
            MainActivity.writeError( this, ex, context );
            status = Status.EXCEPTION;
            bundle.putString( BackgroundGuiHandler.ERROR, "io problem: " + ex );
        }
        catch ( final Exception ex ) {
            ex.printStackTrace();
            Logging.error( "ex problem: " + ex, ex );
            MainActivity.writeError( this, ex, context );
            status = Status.EXCEPTION;
            bundle.putString( BackgroundGuiHandler.ERROR, "ex problem: " + ex );
        }

        return status;
    }

    /**
     * (directly lifted from FileUploadTask)
     */
    private long writeFile( final OutputStream fos, final Bundle bundle,
                            final ObservationUploader.CountStats countStats ) throws IOException,
            PackageManager.NameNotFoundException, InterruptedException, DBException {

        final SharedPreferences prefs = context.getSharedPreferences( PreferenceKeys.SHARED_PREFS, 0);
        long maxId = prefs.getLong( PreferenceKeys.PREF_DB_MARKER, 0L );
        if ( writeEntireDb ) {
            maxId = 0;
        }
        else if ( writeRun ) {
            // max id at startup
            maxId = prefs.getLong( PreferenceKeys.PREF_MAX_DB, 0L );
        }
        Logging.info( "Writing file starting with observation id: " + maxId);
        final Cursor cursor = dbHelper.locationIterator( maxId );

        //noinspection
        try {
            return writeFileWithCursor( context, fos, bundle, countStats, cursor, prefs );
        } finally {
            fos.close();
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    /**
     * (lifted directly from FileUploaderTask)
     */
    private long writeFileWithCursor(final Context context, final OutputStream fos, final Bundle bundle,
                                     final ObservationUploader.CountStats countStats,
                                     final Cursor cursor, final SharedPreferences prefs ) throws IOException,
            PackageManager.NameNotFoundException, InterruptedException {
        long maxId = prefs.getLong( PreferenceKeys.PREF_DB_MARKER, 0L );

        final long start = System.currentTimeMillis();
        final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
        dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
        countStats.lineCount = 0;
        final int total = cursor.getCount();
        long fileWriteMillis = 0;
        long netMillis = 0;

        sendBundledMessage( Status.WRITING.ordinal(), bundle );

        final PackageManager pm = context.getPackageManager();
        final PackageInfo pi = pm.getPackageInfo(context.getPackageName(), 0);

        // print header
        final StringBuffer headerBuffer = new StringBuffer();
        //noinspection resource
        final CSVPrinter headerPrinter = new CSVPrinter(headerBuffer, CSV_FORMAT);
        headerPrinter.printRecord(
                "WigleWifi-1.6",
                "appRelease=" + pi.versionName,
                "model=" + android.os.Build.MODEL,
                "release=" + android.os.Build.VERSION.RELEASE,
                "device=" + android.os.Build.DEVICE,
                "display=" + android.os.Build.DISPLAY,
                "board=" + android.os.Build.BOARD,
                "brand=" + android.os.Build.BRAND,
                "star=Sol", // assuming for now
                "body=3",
                "subBody=0"
        );
        headerBuffer.append(CSV_COLUMN_HEADERS).append(NEWLINE);
        final byte[] headerBytes = headerBuffer.toString().getBytes(ENCODING);
        fos.write( headerBytes );
        countStats.byteCount = headerBytes.length;
        // Logging.debug("headerBuffer: " + headerBuffer);

        // print body
        if ( total > 0 ) {
            ByteBuffer byteBuffer = ByteBuffer.allocate( 1024 ); // this ensures hasArray() is true
            CharBuffer charBuffer = CharBuffer.allocate( 1024 );
            //noinspection resource
            final CSVPrinter printer = new CSVPrinter(charBuffer, CSV_FORMAT);

            final CharsetEncoder encoder = Charset.forName( ENCODING ).newEncoder();
            // don't stop when a goofy character is found
            encoder.onUnmappableCharacter( CodingErrorAction.REPLACE );
            final NumberFormat numberFormat = NumberFormat.getNumberInstance( Locale.US );
            // no commas in the comma-separated file
            numberFormat.setGroupingUsed( false );
            if ( numberFormat instanceof DecimalFormat) {
                final DecimalFormat dc = (DecimalFormat) numberFormat;
                dc.setMaximumFractionDigits( 16 );
            }
            final StringBuffer stringBuffer = new StringBuffer();
            final FieldPosition fp = new FieldPosition(NumberFormat.INTEGER_FIELD);
            final Date date = new Date();

            // loop!
            for ( cursor.moveToFirst(); ! cursor.isAfterLast(); cursor.moveToNext() ) {
                if ( wasInterrupted() ) {
                    throw new InterruptedException( "we were interrupted" );
                }
                // _id,bssid,level,lat,lon,time
                final long id = cursor.getLong(0);
                if ( id > maxId ) {
                    maxId = id;
                }
                final String bssid = cursor.getString(1);
                final long netStart = System.currentTimeMillis();
                final Network network = dbHelper.getNetwork( bssid );
                netMillis += System.currentTimeMillis() - netStart;
                if ( network == null ) {
                    // weird condition, skipping
                    Logging.error("network not in database: " + bssid );
                    continue;
                }

                countStats.lineCount++;
                // ListActivity.debug("writing network: " + ssid );

                // reset the buffers
                charBuffer.clear();
                byteBuffer.clear();
                // fill in the line
                try {
                    // MAC
                    printer.print( network.getBssid() );
                    // SSID, can be unicode
                    printer.print( network.getSsid() );
                    // AuthMode
                    printer.print( network.getCapabilities() );
                    // FirstSeen
                    charBuffer.append( COMMA ); // prepend COMMA before any non-printer.prints
                    date.setTime( cursor.getLong(7) );
                    FileAccess.singleCopyDateFormat( dateFormat, stringBuffer, charBuffer, fp, date );
                    // Channel
                    charBuffer.append( COMMA );
                    final Integer channel = network.getChannel();
                    if ( channel != null ) {
                        FileAccess.singleCopyNumberFormat(numberFormat, stringBuffer, charBuffer, fp, channel);
                    }
                    // Frequency
                    charBuffer.append( COMMA );
                    final int frequency = network.getFrequency();
                    if ( frequency != 0 ) {
                        FileAccess.singleCopyNumberFormat(numberFormat, stringBuffer, charBuffer, fp, frequency);
                    }
                    // RSSI
                    charBuffer.append( COMMA );
                    FileAccess.singleCopyNumberFormat( numberFormat, stringBuffer, charBuffer, fp, cursor.getInt(2) );
                    // CurrentLatitude
                    charBuffer.append( COMMA );
                    FileAccess.singleCopyNumberFormat( numberFormat, stringBuffer, charBuffer, fp, cursor.getDouble(3) );
                    // CurrentLongitude
                    charBuffer.append( COMMA );
                    FileAccess.singleCopyNumberFormat( numberFormat, stringBuffer, charBuffer, fp, cursor.getDouble(4) );
                    // AltitudeMeters
                    charBuffer.append( COMMA );
                    FileAccess.singleCopyNumberFormat( numberFormat, stringBuffer, charBuffer, fp, cursor.getDouble(5) );
                    // AccuracyMeters
                    charBuffer.append( COMMA );
                    FileAccess.singleCopyNumberFormat( numberFormat, stringBuffer, charBuffer, fp, cursor.getDouble(6) );
                    // RCOIs
                    printer.print(network.getRcoisOrBlank());
                    // MfgrId
                    charBuffer.append( COMMA );
                    final int mfgrid = cursor.getInt(8);
                    if (mfgrid != 0) {
                        FileAccess.singleCopyNumberFormat( numberFormat, stringBuffer, charBuffer, fp, mfgrid );
                    }
                    // Type
                    printer.print( network.getType().name() );
                    // newline
                    printer.println();
                }
                catch ( BufferOverflowException ex ) {
                    Logging.info("buffer overflow: " + ex, ex );
                    // double the buffer
                    charBuffer = CharBuffer.allocate( charBuffer.capacity() * 2 );
                    byteBuffer = ByteBuffer.allocate( byteBuffer.capacity() * 2 );
                    // try again
                    cursor.moveToPrevious();
                    continue;
                }

                // tell the encoder to stop here and to start at the beginning
                charBuffer.flip();

                // do the encoding
                encoder.reset();
                encoder.encode( charBuffer, byteBuffer, true );
                try {
                    encoder.flush( byteBuffer );
                }
                catch ( IllegalStateException ex ) {
                    Logging.error("exception flushing: " + ex, ex);
                    continue;
                }

                // figure out where in the byteBuffer to stop
                final int end = byteBuffer.position();
                final int offset = byteBuffer.arrayOffset();
                // do the write
                final long writeStart = System.currentTimeMillis();
                fos.write(byteBuffer.array(), offset, end+offset );
                fileWriteMillis += System.currentTimeMillis() - writeStart;

                countStats.byteCount += end;

                // debug logging
                // byte[] dst = new byte[end];
                // System.arraycopy(byteBuffer.array(), offset, dst, 0, end);
                // final String out = new String(dst, ENCODING);
                // Logging.debug("bytes! " + out);

                // update UI
                final int percentDone = (countStats.lineCount * 1000) / total;
                sendPercentTimesTen( percentDone, bundle );
            }
        }

        Logging.info("wrote file in: " + (System.currentTimeMillis() - start) +
                "ms. fileWriteMillis: " + fileWriteMillis + " netmillis: " + netMillis );

        return maxId;
    }
}
