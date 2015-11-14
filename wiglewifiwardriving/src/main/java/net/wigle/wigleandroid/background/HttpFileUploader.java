package net.wigle.wigleandroid.background;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.WritableByteChannel;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CoderResult;
import java.util.Map;
import java.util.zip.GZIPInputStream;

import net.wigle.wigleandroid.MainActivity;
import android.os.Handler;

/**
 * Based on   http://getablogger.blogspot.com/2008/01/android-how-to-post-file-to-php-server.html
 * Read more: http://getablogger.blogspot.com/2008/01/android-how-to-post-file-to-php-server.html#ixzz0iqTJF7SV
 */
final class HttpFileUploader {
    public static final String ENCODING = "UTF-8";
    public static final String LINE_END = "\r\n";
    public static final String TWO_HYPHENS = "--";
    public static final String BOUNDARY = "*****";

    /** don't allow construction */
    private HttpFileUploader(){
    }

    public static HttpURLConnection connect(String urlString, final boolean setBoundary)
            throws IOException {
        return connect(urlString, setBoundary, null);
    }

    public static HttpURLConnection connect(String urlString, final boolean setBoundary,
                                            final PreConnectConfigurator preConnectConfigurator) throws IOException {
        URL connectURL;
        try{
            connectURL = new URL( urlString );
        }
        catch( Exception ex ) {
            MainActivity.error( "MALFORMATED URL: " + ex, ex );
            return null;
        }

        return createConnection(connectURL, setBoundary, preConnectConfigurator);
    }

    private static HttpURLConnection createConnection(final URL connectURL, final boolean setBoundary,
                                                      final PreConnectConfigurator preConnectConfigurator)
            throws IOException {

        String javaVersion = "unknown";
        try {
            javaVersion =  System.getProperty("java.vendor") + " " +
                    System.getProperty("java.version") + ", jvm: " +
                    System.getProperty("java.vm.vendor") + " " +
                    System.getProperty("java.vm.name") + " " +
                    System.getProperty("java.vm.version") + " on " +
                    System.getProperty("os.name") + " " +
                    System.getProperty("os.version") +
                    " [" + System.getProperty("os.arch") + "]";
        } catch (RuntimeException ignored) { }
        final String userAgent = "WigleWifi ("+javaVersion+")";

        // Open a HTTP connection to the URL
        HttpURLConnection conn = (HttpURLConnection) connectURL.openConnection();
        // Allow Inputs
        conn.setDoInput(true);
        // Allow Outputs
        conn.setDoOutput(true);
        // Don't use a cached copy.
        conn.setUseCaches(false);
        conn.setInstanceFollowRedirects( false );

        // Use a post method.
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Connection", "Keep-Alive");
        if ( setBoundary ) {
            conn.setRequestProperty("Content-Type", "multipart/form-data;boundary=" + BOUNDARY);
        }
        else {
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
        }
        conn.setRequestProperty( "Accept-Encoding", "gzip" );
        conn.setRequestProperty( "User-Agent", userAgent );

        // chunk large stuff
        conn.setChunkedStreamingMode( 32*1024 );
        // shouldn't have to do this, but it makes their HttpURLConnectionImpl happy
        conn.setRequestProperty("Transfer-Encoding", "chunked");
        // 8 hours
        conn.setReadTimeout(8*60*60*1000);

        // allow caller to munge
        if (preConnectConfigurator != null) {
            preConnectConfigurator.configure(conn);
        }

        // connect
        MainActivity.info( "about to connect" );
        conn.connect();
        MainActivity.info( "connected" );

        return conn;
    }

    /**
     * upload utility method.
     *
     * @param urlString the url to POST the file to
     * @param filename the filename to use for the post
     * @param fileParamName the HTML form field name for the file
     * @param fileInputStream an open file stream to the file to post
     * @param params form data fields (key and value)
     * @param handler if non-null gets empty messages with updates on progress
     * @param filesize guess at filesize for UI callbacks
     */
    public static String upload( final String urlString, final String filename, final String fileParamName,
                                 final FileInputStream fileInputStream, final Map<String,String> params,
                                 final Handler handler, final long filesize)
                                throws IOException {

        String retval = null;
        HttpURLConnection conn = null;

        try {
            final boolean setBoundary = true;
            conn = connect(urlString, setBoundary);
            if (conn == null) {
                throw new IOException("No connection for: " + urlString);
            }
            OutputStream connOutputStream = conn.getOutputStream();

            // reflect out the chunking info
            for ( Method meth : connOutputStream.getClass().getMethods() ) {
                // MainActivity.info("meth: " + meth.getName() );
                try {
                    if ( "isCached".equals(meth.getName()) || "isChunked".equals(meth.getName())) {
                        Boolean val = (Boolean) meth.invoke( connOutputStream, (Object[]) null );
                        MainActivity.info( meth.getName() + " " + val );
                    }
                    else if ( "size".equals( meth.getName())) {
                        Integer val = (Integer) meth.invoke( connOutputStream, (Object[]) null );
                        MainActivity.info( meth.getName() + " " + val );
                    }
                }
                catch ( Exception ex ) {
                    // this block is just for logging, so don't splode if it has a problem
                    MainActivity.error("ex: " + ex, ex );
                }
            }

            WritableByteChannel wbc = Channels.newChannel( connOutputStream );

            StringBuilder header = new StringBuilder( 400 ); // find a better guess. it was 281 for me in the field 2010/05/16 -hck
            for ( Map.Entry<String, String> entry : params.entrySet() ) {
                header.append( TWO_HYPHENS ).append( BOUNDARY ).append( LINE_END );
                header.append("Content-Disposition: form-data; name=\"")
                        .append(entry.getKey()).append("\"").append(LINE_END);
                header.append( LINE_END );
                header.append( entry.getValue() );
                header.append( LINE_END );
            }

            header.append( TWO_HYPHENS + BOUNDARY + LINE_END );
            header.append("Content-Disposition: form-data; name=\"").append(fileParamName)
                    .append("\";filename=\"").append(filename).append("\"").append(LINE_END);
            header.append( "Content-Type: application/octet_stream" + LINE_END );
            header.append( LINE_END );

            MainActivity.info( "About to write headers, length: " + header.length() );
            CharsetEncoder enc = Charset.forName( ENCODING ).newEncoder();
            CharBuffer cbuff = CharBuffer.allocate( 1024 );
            ByteBuffer bbuff = ByteBuffer.allocate( 1024 );
            writeString( wbc, header.toString(), enc, cbuff, bbuff );

            MainActivity.info( "Headers are written, length: " + header.length() );
            int percentTimesTenDone = ( header.length() * 1000) / (int)filesize;
            if ( handler != null && percentTimesTenDone >= 0 ) {
                handler.sendEmptyMessage( BackgroundGuiHandler.WRITING_PERCENT_START + percentTimesTenDone );
            }

            FileChannel fc = fileInputStream.getChannel();
            long byteswritten = 0;
            final int chunk = 16 * 1024;
            while ( byteswritten < filesize ) {
                final long bytes = fc.transferTo( byteswritten, chunk, wbc );
                if ( bytes <= 0 ) {
                    MainActivity.info( "giving up transfering file. bytes: " + bytes );
                    break;
                }
                byteswritten += bytes;

                MainActivity.info( "transferred " + byteswritten + " of " + filesize );
                percentTimesTenDone = ((int)byteswritten * 1000) / (int)filesize;

                if ( handler != null && percentTimesTenDone >= 0 ) {
                    handler.sendEmptyMessage( BackgroundGuiHandler.WRITING_PERCENT_START + percentTimesTenDone );
                }
            }
            MainActivity.info( "done. transferred " + byteswritten + " of " + filesize );

            // send multipart form data necesssary after file data...
            header.setLength( 0 ); // clear()
            header.append(LINE_END);
            header.append(TWO_HYPHENS + BOUNDARY + TWO_HYPHENS + LINE_END);
            writeString( wbc, header.toString(), enc, cbuff, bbuff );

            // close streams
            MainActivity.info( "File is written" );
            wbc.close();
            fc.close();
            fileInputStream.close();

            int responseCode = conn.getResponseCode();
            MainActivity.info( "connection response code: " + responseCode );

            // read the response
            final InputStream is = getInputStream( conn );
            int ch;
            final StringBuilder b = new StringBuilder();
            final byte[] buffer = new byte[1024];

            while( ( ch = is.read( buffer ) ) != -1 ) {
                b.append( new String( buffer, 0, ch, ENCODING ) );
            }
            retval = b.toString();
            // MainActivity.info( "Response: " + retval );
        }
        finally {
            if ( conn != null ) {
                MainActivity.info( "conn disconnect" );
                conn.disconnect();
            }
        }

        return retval;
    }

    /**
     * get the InputStream, gunzip'ing if needed
     */
    public static InputStream getInputStream( HttpURLConnection conn ) throws IOException {
        InputStream input = conn.getInputStream();

        String encode = conn.getContentEncoding();
        MainActivity.info( "Encoding: " + encode );
        if ( "gzip".equalsIgnoreCase( encode ) ) {
            input = new GZIPInputStream( input );
        }
        return input;
    }

    /**
     * write a string out to a byte channel.
     *
     * @param wbc the byte channel to write to
     * @param str the string to write
     * @param enc the cbc encoder to use, will be reset
     * @param cbuff the scratch charbuffer, will be cleared
     * @param bbuff the scratch bytebuffer, will be cleared
     */
    private static void writeString( WritableByteChannel wbc, String str, CharsetEncoder enc, CharBuffer cbuff, ByteBuffer bbuff ) throws IOException {
        // clear existing state
        cbuff.clear();
        bbuff.clear();
        enc.reset();

        cbuff.put( str );
        cbuff.flip();

        CoderResult result = enc.encode( cbuff, bbuff, true );
        if ( CoderResult.UNDERFLOW != result ) {
            throw new IOException( "encode fail. result: " + result + " cbuff: " + cbuff + " bbuff: " + bbuff );
        }
        result = enc.flush( bbuff );
        if ( CoderResult.UNDERFLOW != result ) {
            throw new IOException( "flush fail. result: " + result + " bbuff: " + bbuff );
        }
        bbuff.flip();

        int remaining = bbuff.remaining();
        while ( remaining > 0 ) {
            remaining -= wbc.write( bbuff );
        }
    }

}
