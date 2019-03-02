package net.wigle.wigleandroid.util;

import android.database.Cursor;

import net.wigle.wigleandroid.model.Network;

import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.text.DateFormat;
import java.text.FieldPosition;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * CSV line writer - manages writing rows from cursor + network.
 * Abstracted out from ObservationFileUpload / FileUpload task.
 */
public class CsvUtil {

    private static final String COMMA = ",";
    private static final String DOUBLE_QUOTE = "\"";
    private static final String NEWLINE = "\n";

    /**
     * Write a row for a network
     * @param cursor database Cursor instance pointed at the current record
     * @param network a network instance
     * @param date the current date
     * @param charBuffer character buffer instance (managed externally)
     * @param byteBuffer byte buffer instance (managed externally)
     * @param stringBuffer string buffer instance (managed externally)
     * @param fp FieldPosition instance
     * @param dateFormat date format with which to write
     * @param numberFormat number format with which to write
     * @return true if successful
     * @throws BufferOverflowException we can break our buffers here
     */
    public static boolean writeLine(final Cursor cursor, final Network network, final Date date,
                                    final CharBuffer charBuffer, final ByteBuffer byteBuffer,
                                    final StringBuffer stringBuffer,  final FieldPosition fp,
                                    final SimpleDateFormat dateFormat,
                                    final NumberFormat numberFormat)
            throws BufferOverflowException {

        String ssid = network.getSsid();
        // ALIBI: previous implementation used "_" for commas, and didn't handle double-quotes.
        //  this implementation standardizes w/ kismet
        if (ssid.contains(COMMA)) {
            // comma isn't a legal ssid character, but just in case
            ssid = ssid.replaceAll( COMMA, "\\\\054" );
            //ssid = ssid.replaceAll( COMMA, "_" );
        }
        if (ssid.contains(DOUBLE_QUOTE)) {
            ssid = ssid.replace(DOUBLE_QUOTE, "\\042");
        }

        charBuffer.clear();
        byteBuffer.clear();

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

        return true;
    }

    /**
     * (lifted directly from FileUploaderTask)
     * @param numberFormat
     * @param stringBuffer
     * @param charBuffer
     * @param fp
     * @param number
     */
    private static void singleCopyNumberFormat( final NumberFormat numberFormat,
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
    private static void singleCopyNumberFormat( final NumberFormat numberFormat,
                                         final StringBuffer stringBuffer,
                                         final CharBuffer charBuffer, final FieldPosition fp,
                                         final double number ) {
        stringBuffer.setLength( 0 );
        numberFormat.format( number, stringBuffer, fp );
        stringBuffer.getChars(0, stringBuffer.length(), charBuffer.array(), charBuffer.position() );
        charBuffer.position( charBuffer.position() + stringBuffer.length() );
    }

    /**
     * (lifted directly from FileUploaderTask)
     * @param dateFormat
     * @param stringBuffer
     * @param charBuffer
     * @param fp
     * @param date
     */
    private static void singleCopyDateFormat(final DateFormat dateFormat, final StringBuffer stringBuffer,
                                      final CharBuffer charBuffer, final FieldPosition fp,
                                      final Date date ) {
        stringBuffer.setLength( 0 );
        dateFormat.format( date, stringBuffer, fp );
        stringBuffer.getChars(0, stringBuffer.length(), charBuffer.array(), charBuffer.position() );
        charBuffer.position( charBuffer.position() + stringBuffer.length() );
    }

}
