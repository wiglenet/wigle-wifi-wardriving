package net.wigle.wigleandroid.background;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.preference.PreferenceManager;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;

import net.wigle.wigleandroid.db.DBException;
import net.wigle.wigleandroid.db.DatabaseHelper;
import net.wigle.wigleandroid.ListFragment;
import net.wigle.wigleandroid.MainActivity;
import net.wigle.wigleandroid.R;
import net.wigle.wigleandroid.TokenAccess;
import net.wigle.wigleandroid.WiGLEAuthException;
import net.wigle.wigleandroid.model.Network;
import net.wigle.wigleandroid.util.FileUtility;
import net.wigle.wigleandroid.util.Logging;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
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
import java.text.DateFormat;
import java.text.DecimalFormat;
import java.text.FieldPosition;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.zip.GZIPOutputStream;

import javax.net.ssl.SSLException;

import static net.wigle.wigleandroid.util.FileUtility.CSV_EXT;
import static net.wigle.wigleandroid.util.FileUtility.GZ_EXT;
import static net.wigle.wigleandroid.util.FileUtility.WIWI_PREFIX;

/**
 * replacement file upload task
 * Created by arkasha on 2/6/17.
 */

public class ObservationUploader extends AbstractProgressApiRequest {

    private static final String COMMA = ",";
    private static final String NEWLINE = "\n";

    private final boolean justWriteFile;
    private final boolean writeEntireDb;
    private final boolean writeRun;

    public final static String CSV_COLUMN_HEADERS = "MAC,SSID,AuthMode,FirstSeen,Channel,RSSI,CurrentLatitude,CurrentLongitude,AltitudeMeters,AccuracyMeters,Type";

    private static class CountStats {
        int byteCount;
        int lineCount;
    }

    public ObservationUploader(final FragmentActivity context,
                               final DatabaseHelper dbHelper, final ApiListener listener,
                               boolean justWriteFile, boolean writeEntireDb, boolean writeRun) {
        super(context, dbHelper, "ApiUL", null, MainActivity.FILE_POST_URL, false,
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
        }
        finally {
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
                        ListFragment.SHARED_PREFS, 0);
            } else {
                prefs = null;
                Logging.error("Failed to get activity for non-null fragment - prefs access failed on upload.");
            }
        } else {
            prefs = PreferenceManager.getDefaultSharedPreferences(MainActivity.getMainActivity().getApplicationContext());
        }
        if (prefs != null) {
            final boolean beAnonymous = prefs.getBoolean(ListFragment.PREF_BE_ANONYMOUS, false);
            final String authName = prefs.getString(ListFragment.PREF_AUTHNAME, null);
            final String userName = prefs.getString(ListFragment.PREF_USERNAME, null);
            final String userPass = prefs.getString(ListFragment.PREF_PASSWORD, null);
            Logging.info("authName: " + authName);
            if ((!beAnonymous) && (authName == null) && (userName != null) && (userPass != null)) {
                Logging.info("No authName, going to request token");
                downloadTokenAndStart(fragment);
            } else {
                start();
            }
        }
    }

    /**
     * upload guts. lifted from FileUploaderTask
     * @param bundle
     * @return the Status of the upload
     * @throws InterruptedException if the upload is interrupted
     */
    private Status doUpload( final Bundle bundle )
            throws InterruptedException {

        Status status;

        try {
            final Object[] fileFilename = new Object[2];
            final OutputStream fos = getOutputStream( context, bundle, fileFilename );
            final File file = (File) fileFilename[0];
            final String filename = (String) fileFilename[1];

            // write file
            ObservationUploader.CountStats countStats = new ObservationUploader.CountStats();
            long maxId = writeFile( fos, bundle, countStats );

            final Map<String,String> params = new HashMap<>();

            final SharedPreferences prefs = context.getSharedPreferences( ListFragment.SHARED_PREFS, 0);
            if ( prefs.getBoolean(ListFragment.PREF_DONATE, false) ) {
                params.put("donate","on");
            }
            final boolean beAnonymous = prefs.getBoolean(ListFragment.PREF_BE_ANONYMOUS, false);
            final String authName = prefs.getString(ListFragment.PREF_AUTHNAME, null);
            if (!beAnonymous && null == authName) {
                return Status.BAD_LOGIN;
            }
            final String userName = prefs.getString(ListFragment.PREF_USERNAME, null);
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

            final String response = OkFileUploader.upload(MainActivity.FILE_POST_URL, absolutePath,
                    "file", params, authName, token, getHandler());

            if ( ! prefs.getBoolean(ListFragment.PREF_DONATE, false) ) {
                if ( response != null && response.indexOf("donate=Y") > 0 ) {
                    final SharedPreferences.Editor editor = prefs.edit();
                    editor.putBoolean( ListFragment.PREF_DONATE, true );
                    editor.apply();
                }
            }

            //TODO: any reason to parse this JSON object? all we care about are two strings.
            Logging.info(response);
            if ( response != null && response.indexOf("\"success\":true") > 0 ) {
                status = Status.SUCCESS;

                // save in the prefs
                final SharedPreferences.Editor editor = prefs.edit();
                editor.putLong( ListFragment.PREF_DB_MARKER, maxId );
                editor.putLong( ListFragment.PREF_MAX_DB, maxId );
                editor.putLong( ListFragment.PREF_NETS_UPLOADED, dbHelper.getNetworkCount() );
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
            MainActivity.writeError( this, ex, context, "Has data connection: " + hasDataConnection(context) );
            status = Status.EXCEPTION;
            bundle.putString( BackgroundGuiHandler.ERROR, "ex problem: " + ex );
        }

        return status;
    }

    public static boolean hasDataConnection(final Context context) {
        final ConnectivityManager connMgr =
                (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (null == connMgr) {
            Logging.error("null ConnectivityManager trying to determine connection info");
            return false;
        }
        final NetworkInfo wifi = connMgr.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
        final NetworkInfo mobile = connMgr.getNetworkInfo(ConnectivityManager.TYPE_MOBILE);
        //noinspection SimplifiableIfStatement
        if (wifi != null && wifi.isAvailable()) {
            return true;
        }
        return mobile != null && mobile.isAvailable();
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
            OutputStream fos = null;
            try {
                fos = getOutputStream( context, bundle, new Object[2] );
                writeFile( fos, bundle, countStats );
                // show on the UI
                status = Status.WRITE_SUCCESS;
                sendBundledMessage( status.ordinal(), bundle );
            }
            finally {
                if ( fos != null ) {
                    fos.close();
                }
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
     * @param fos
     * @param bundle
     * @param countStats
     * @return
     * @throws IOException
     * @throws PackageManager.NameNotFoundException
     * @throws InterruptedException
     * @throws DBException
     */
    private long writeFile( final OutputStream fos, final Bundle bundle,
                            final ObservationUploader.CountStats countStats ) throws IOException,
            PackageManager.NameNotFoundException, InterruptedException, DBException {

        final SharedPreferences prefs = context.getSharedPreferences( ListFragment.SHARED_PREFS, 0);
        long maxId = prefs.getLong( ListFragment.PREF_DB_MARKER, 0L );
        if ( writeEntireDb ) {
            maxId = 0;
        }
        else if ( writeRun ) {
            // max id at startup
            maxId = prefs.getLong( ListFragment.PREF_MAX_DB, 0L );
        }
        Logging.info( "Writing file starting with observation id: " + maxId);
        final Cursor cursor = dbHelper.locationIterator( maxId );

        //noinspection TryFinallyCanBeTryWithResources
        try {
            return writeFileWithCursor( fos, bundle, countStats, cursor );
        } finally {
            fos.close();
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    /**
     * (lifted directly from FileUploaderTask)
     * @param fos
     * @param bundle
     * @param countStats
     * @param cursor
     * @return
     * @throws IOException
     * @throws PackageManager.NameNotFoundException
     * @throws InterruptedException
     */
    private long writeFileWithCursor( final OutputStream fos, final Bundle bundle,
                                      final ObservationUploader.CountStats countStats,
                                      final Cursor cursor ) throws IOException,
            PackageManager.NameNotFoundException, InterruptedException {

        final SharedPreferences prefs = context.getSharedPreferences( ListFragment.SHARED_PREFS, 0);
        long maxId = prefs.getLong( ListFragment.PREF_DB_MARKER, 0L );

        final long start = System.currentTimeMillis();
        final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
        countStats.lineCount = 0;
        final int total = cursor.getCount();
        long fileWriteMillis = 0;
        long netMillis = 0;

        sendBundledMessage( Status.WRITING.ordinal(), bundle );

        final PackageManager pm = context.getPackageManager();
        final PackageInfo pi = pm.getPackageInfo(context.getPackageName(), 0);

        // name, version, header
        final String header = "WigleWifi-1.4"
                + ",appRelease=" + pi.versionName
                + ",model=" + android.os.Build.MODEL
                + ",release=" + android.os.Build.VERSION.RELEASE
                + ",device=" + android.os.Build.DEVICE
                + ",display=" + android.os.Build.DISPLAY
                + ",board=" + android.os.Build.BOARD
                + ",brand=" + android.os.Build.BRAND
                + NEWLINE
                + CSV_COLUMN_HEADERS
                + NEWLINE;
        writeFos( fos, header );

        // assume header is all byte per char
        countStats.byteCount = header.length();

        if ( total > 0 ) {
            CharBuffer charBuffer = CharBuffer.allocate( 1024 );
            ByteBuffer byteBuffer = ByteBuffer.allocate( 1024 ); // this ensures hasArray() is true
            final CharsetEncoder encoder = Charset.forName( MainActivity.ENCODING ).newEncoder();
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
                String ssid = network.getSsid();
                if (ssid.contains(COMMA)) {
                    // comma isn't a legal ssid character, but just in case
                    ssid = ssid.replaceAll( COMMA, "_" );
                }
                // ListActivity.debug("writing network: " + ssid );

                // reset the buffers
                charBuffer.clear();
                byteBuffer.clear();
                // fill in the line
                try {
                    charBuffer.append( network.getBssid() );
                    charBuffer.append( COMMA );
                    // ssid can be unicode
                    charBuffer.append( ssid );
                    charBuffer.append( COMMA );
                    charBuffer.append( network.getCapabilities() );
                    charBuffer.append( COMMA );
                    date.setTime( cursor.getLong(7) );
                    singleCopyDateFormat( dateFormat, stringBuffer, charBuffer, fp, date );
                    charBuffer.append( COMMA );
                    Integer channel = network.getChannel();
                    if ( channel == null ) {
                        channel = network.getFrequency();
                    }
                    singleCopyNumberFormat( numberFormat, stringBuffer, charBuffer, fp, channel );
                    charBuffer.append( COMMA );
                    singleCopyNumberFormat( numberFormat, stringBuffer, charBuffer, fp, cursor.getInt(2) );
                    charBuffer.append( COMMA );
                    singleCopyNumberFormat( numberFormat, stringBuffer, charBuffer, fp, cursor.getDouble(3) );
                    charBuffer.append( COMMA );
                    singleCopyNumberFormat( numberFormat, stringBuffer, charBuffer, fp, cursor.getDouble(4) );
                    charBuffer.append( COMMA );
                    singleCopyNumberFormat( numberFormat, stringBuffer, charBuffer, fp, cursor.getDouble(5) );
                    charBuffer.append( COMMA );
                    singleCopyNumberFormat( numberFormat, stringBuffer, charBuffer, fp, cursor.getDouble(6) );
                    charBuffer.append( COMMA );
                    charBuffer.append( network.getType().name() );
                    charBuffer.append( NEWLINE );
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
                // byteBuffer = encoder.encode( charBuffer );  (old way)

                // figure out where in the byteBuffer to stop
                final int end = byteBuffer.position();
                final int offset = byteBuffer.arrayOffset();
                //if ( end == 0 ) {
                // if doing the encode without giving a long-term byteBuffer (old way), the output
                // byteBuffer position is zero, and the limit and capacity are how long to write for.
                //  end = byteBuffer.limit();
                //}

                // MainActivity.info("buffer: arrayOffset: " + byteBuffer.arrayOffset() + " limit: "
                // + byteBuffer.limit()
                //     + " capacity: " + byteBuffer.capacity() + " pos: " + byteBuffer.position() +
                // " end: " + end
                //     + " result: " + result );
                final long writeStart = System.currentTimeMillis();
                fos.write(byteBuffer.array(), offset, end+offset );
                fileWriteMillis += System.currentTimeMillis() - writeStart;

                countStats.byteCount += end;

                // update UI
                final int percentDone = (countStats.lineCount * 1000) / total;
                sendPercentTimesTen( percentDone, bundle );
            }
        }

        Logging.info("wrote file in: " + (System.currentTimeMillis() - start) +
                "ms. fileWriteMillis: " + fileWriteMillis + " netmillis: " + netMillis );

        return maxId;
    }

    /**
     * (lifted directly from FileUploaderTask)
     * @param fos
     * @param data
     * @throws IOException
     */
    public static void writeFos( final OutputStream fos, final String data ) throws IOException {
        if ( data != null ) {
            fos.write( data.getBytes( MainActivity.ENCODING ) );
        }
    }


    /**
     * (lifted directly from FileUploaderTask)
     * @param numberFormat
     * @param stringBuffer
     * @param charBuffer
     * @param fp
     * @param number
     */
    private void singleCopyNumberFormat( final NumberFormat numberFormat,
                                         final StringBuffer stringBuffer,
                                         final CharBuffer charBuffer, final FieldPosition fp,
                                         final int number ) {
        stringBuffer.setLength( 0 );
        numberFormat.format( number, stringBuffer, fp );
        stringBuffer.getChars(0, stringBuffer.length(), charBuffer.array(), charBuffer.position() );
        charBuffer.position( charBuffer.position() + stringBuffer.length() );
    }

    /**
     * (lifted directly from FileUploaderTask)
     * @param numberFormat
     * @param stringBuffer
     * @param charBuffer
     * @param fp
     * @param number
     */
    private void singleCopyNumberFormat( final NumberFormat numberFormat,
                                         final StringBuffer stringBuffer,
                                         final CharBuffer charBuffer, final FieldPosition fp,
                                         final double number ) {
        stringBuffer.setLength( 0 );
        numberFormat.format( number, stringBuffer, fp );
        stringBuffer.getChars(0, stringBuffer.length(), charBuffer.array(), charBuffer.position() );
        charBuffer.position( charBuffer.position() + stringBuffer.length() );
    }

    /**
     * Copy a date according to format into a position in a string
     * (lifted directly from FileUploaderTask)
     * @param dateFormat the DateFormat to use
     * @param stringBuffer
     * @param charBuffer
     * @param fp the position at which to insert
     * @param date the date
     */
    private void singleCopyDateFormat(final DateFormat dateFormat, final StringBuffer stringBuffer,
                                      final CharBuffer charBuffer, final FieldPosition fp,
                                      final Date date ) {
        stringBuffer.setLength( 0 );
        dateFormat.format( date, stringBuffer, fp );
        stringBuffer.getChars(0, stringBuffer.length(), charBuffer.array(), charBuffer.position() );
        charBuffer.position( charBuffer.position() + stringBuffer.length() );
    }

    public static OutputStream getOutputStream(final Context context, final Bundle bundle,
                                               final Object[] fileFilename)
            throws IOException {
        final SimpleDateFormat fileDateFormat = new SimpleDateFormat("yyyyMMddHHmmss", Locale.US);
        final String filename = WIWI_PREFIX + fileDateFormat.format(new Date()) + CSV_EXT + GZ_EXT;


        final boolean hasSD = FileUtility.hasSD();
        File file = null;
        bundle.putString( BackgroundGuiHandler.FILENAME, filename );
        final String filePath = FileUtility.getUploadFilePath(context);

        if ( hasSD && filePath != null) {
            final File path = new File( filePath );
            //noinspection ResultOfMethodCallIgnored
            path.mkdirs();
            String openString = filePath + filename;
            Logging.info("Opening file: " + openString);
            file = new File( openString );
            if ( ! file.exists() ) {
                if (!file.createNewFile()) {
                    throw new IOException("Could not create file: " + openString);
                }
            }
            bundle.putString( BackgroundGuiHandler.FILEPATH, filePath );
        }

        final FileOutputStream rawFos = (hasSD && null != file) ? new FileOutputStream( file )
                    : context.openFileOutput( filename, Context.MODE_PRIVATE );

        final GZIPOutputStream fos = new GZIPOutputStream( rawFos );
        fileFilename[0] = file;
        fileFilename[1] = filename;
        return fos;
    }

}
