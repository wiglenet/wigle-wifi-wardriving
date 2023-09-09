package net.wigle.wigleandroid.util;

import static net.wigle.wigleandroid.util.FileUtility.CSV_EXT;
import static net.wigle.wigleandroid.util.FileUtility.GZ_EXT;
import static net.wigle.wigleandroid.util.FileUtility.WIWI_PREFIX;

import android.content.Context;
import android.os.Bundle;

import net.wigle.wigleandroid.MainActivity;
import net.wigle.wigleandroid.background.BackgroundGuiHandler;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.CharBuffer;
import java.text.DateFormat;
import java.text.FieldPosition;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.zip.GZIPOutputStream;

/**
 * A collection of tools used in writing files from the application
 */
public class FileAccess {
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
    /**
     * (lifted directly from FileUploaderTask)
     */
    public static void singleCopyNumberFormat(final NumberFormat numberFormat,
                                              final StringBuffer stringBuffer,
                                              final CharBuffer charBuffer, final FieldPosition fp,
                                              final int number) {
        stringBuffer.setLength( 0 );
        numberFormat.format( number, stringBuffer, fp );
        stringBuffer.getChars(0, stringBuffer.length(), charBuffer.array(), charBuffer.position() );
        charBuffer.position( charBuffer.position() + stringBuffer.length() );
    }

    /**
     * (lifted directly from FileUploaderTask)
     */
    public static void singleCopyNumberFormat( final NumberFormat numberFormat,
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
     */
    public static void singleCopyDateFormat(final DateFormat dateFormat, final StringBuffer stringBuffer,
                                            final CharBuffer charBuffer, final FieldPosition fp,
                                            final Date date ) {
        stringBuffer.setLength( 0 );
        dateFormat.format( date, stringBuffer, fp );
        stringBuffer.getChars(0, stringBuffer.length(), charBuffer.array(), charBuffer.position() );
        charBuffer.position( charBuffer.position() + stringBuffer.length() );
    }

    /**
     * (lifted directly from FileUploaderTask)
     */
    public static void writeFos( final OutputStream fos, final String data ) throws IOException {
        if ( data != null ) {
            fos.write( data.getBytes( MainActivity.ENCODING ) );
        }
    }

}
