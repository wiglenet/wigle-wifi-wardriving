package net.wigle.wigleandroid;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.WritableByteChannel;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CoderResult;
import java.util.Map;

import android.content.Context;
import android.content.res.Resources;
import android.os.Handler;

/**
 * Based on   http://getablogger.blogspot.com/2008/01/android-how-to-post-file-to-php-server.html
 * Read more: http://getablogger.blogspot.com/2008/01/android-how-to-post-file-to-php-server.html#ixzz0iqTJF7SV
 */
final class HttpFileUploader {
  private static final String ENCODING = "UTF-8";
  
  /** don't allow construction */
  private HttpFileUploader(){
  }

    /** 
     * upload utility method.
     *
     * @param urlString the url to POST the file to
     * @param filename the filename to use for the post
     * @param fileParamName the HTML form field name for the file
     * @param fileInputStream an open file stream to the file to post
     * @param params form data fields (key and value)
     * @param res the app resources (needed for looking up SSL cert)
     * @param handler if non-null gets empty messages with updates on progress
     * @param filesize guess at filesize for UI callbacks
     */
  public static String upload( final String urlString, final String filename, final String fileParamName,
                               final FileInputStream fileInputStream, final Map<String,String> params, 
                               final Resources res, final Handler handler, final long filesize,
                               final Context context ) {
    
    URL connectURL = null;
    try{
      connectURL = new URL( urlString );
    }
    catch( Exception ex ) {
      ListActivity.error( "MALFORMATED URL: " + ex, ex );
      return null;
    }

    // test if we should be doing our own ssl
    boolean self_serving = true;
    try {
        URLConnection testcon = connectURL.openConnection();
        // we should probably time-bound this test-connection?
        testcon.connect();
        self_serving = false;
        // consider a pref to skip this? or just phase out after migration?
    } catch (IOException ex) {
        // we're specificly interested in javax.net.ssl.SSLException
    }

    
    final String lineEnd = "\r\n";
    final String twoHyphens = "--";
    final String boundary = "*****";
    String retval = null;
    HttpURLConnection conn = null;

    try {
      //------------------ CLIENT REQUEST
    
      ListActivity.info("Creating url connection");
      
      // Open a HTTP connection to the URL
      CharsetEncoder enc = Charset.forName( ENCODING ).newEncoder();
      CharBuffer cbuff = CharBuffer.allocate( 1024 );
      ByteBuffer bbuff = ByteBuffer.allocate( 1024 );

      conn = (HttpURLConnection) connectURL.openConnection();
      
      // Allow Inputs
      conn.setDoInput(true);
      // Allow Outputs
      conn.setDoOutput(true);
      // Don't use a cached copy.
      conn.setUseCaches(false);
      conn.setInstanceFollowRedirects( false );
      if ( ( conn instanceof javax.net.ssl.HttpsURLConnection ) && self_serving ) {
          final SSLConfigurator con = SSLConfigurator.getInstance( res );
          con.configure( (javax.net.ssl.HttpsURLConnection) conn );
          ListActivity.info("using ssl! conn: " + conn);
      }
      
      // Use a post method.
      conn.setRequestMethod("POST");
      conn.setRequestProperty("Connection", "Keep-Alive");
      conn.setRequestProperty("Content-Type", "multipart/form-data;boundary="+boundary);
      
      // chunk large stuff
      conn.setChunkedStreamingMode( 32*1024 );
      // shouldn't have to do this, but it makes their HttpURLConnectionImpl happy
      conn.setRequestProperty("Transfer-Encoding", "chunked");
    
      // connect
      ListActivity.info( "about to connect" );
      conn.connect();
      ListActivity.info( "connected" );
      
      OutputStream connOutputStream = conn.getOutputStream();
      if ( true ) {
        // reflect out the chunking info
        for ( Method meth : connOutputStream.getClass().getMethods() ) {
          // ListActivity.info("meth: " + meth.getName() );
          try {
            if ( "isCached".equals(meth.getName()) || "isChunked".equals(meth.getName())) {
              Boolean val = (Boolean) meth.invoke( connOutputStream, (Object[]) null );
              ListActivity.info( meth.getName() + " " + val );
            }
            else if ( "size".equals( meth.getName())) {
              Integer val = (Integer) meth.invoke( connOutputStream, (Object[]) null );
              ListActivity.info( meth.getName() + " " + val );
            }
          }
          catch ( Exception ex ) {
            // this block is just for logging, so don't splode if it has a problem
            ListActivity.error("ex: " + ex, ex );
          }
        }
      }
      
      WritableByteChannel wbc = Channels.newChannel( connOutputStream );
      
      StringBuilder header = new StringBuilder( 400 ); // find a better guess. it was 281 for me in the field 2010/05/16 -hck
      for ( Map.Entry<String, String> entry : params.entrySet() ) {
        header.append( twoHyphens ).append( boundary ).append( lineEnd );
        header.append( "Content-Disposition: form-data; name=\""+ entry.getKey() + "\"" + lineEnd );
        header.append( lineEnd );
        header.append( entry.getValue() );
        header.append( lineEnd );
      }
      
      header.append( twoHyphens + boundary + lineEnd );
      header.append( "Content-Disposition: form-data; name=\"" + fileParamName 
                     + "\";filename=\"" + filename +"\"" + lineEnd );
      header.append( "Content-Type: application/octet_stream" + lineEnd );
      header.append( lineEnd );

      ListActivity.info( "About to write headers, length: " + header.length() );
      writeString( wbc, header.toString(), enc, cbuff, bbuff );

      ListActivity.info( "Headers are written, length: " + header.length() );
      int percentDone = ( (int)header.length() * 100) / (int)filesize;
      if ( handler != null ) {
          handler.sendEmptyMessage( FileUploaderTask.WRITING_PERCENT_START + percentDone );
      }
    
      FileChannel fc = fileInputStream.getChannel();
      long byteswritten = 0;
      final int chunk = 16 * 1024;
      while ( byteswritten < filesize ) {
        final long bytes = fc.transferTo( byteswritten, chunk, wbc );
        if ( bytes <= 0 ) {
          ListActivity.info( "giving up transfering file. bytes: " + bytes );
          break;
        }
        byteswritten += bytes;
              
        ListActivity.info( "transferred " + byteswritten + " of " + filesize );
        percentDone = ((int)byteswritten * 100) / (int)filesize;

        if ( handler != null ) {
          handler.sendEmptyMessage( FileUploaderTask.WRITING_PERCENT_START + percentDone );
        }
      }
      ListActivity.info( "done. transferred " + byteswritten + " of " + filesize );

      // send multipart form data necesssary after file data...
      header.setLength( 0 ); // clear()
      header.append(lineEnd);
      header.append(twoHyphens + boundary + twoHyphens + lineEnd);
      writeString( wbc, header.toString(), enc, cbuff, bbuff );

      // close streams
      ListActivity.info( "File is written" );
      wbc.close();
      fc.close();
      fileInputStream.close();
      
      int responseCode = conn.getResponseCode();
      ListActivity.info( "connection response code: " + responseCode );

      // read the response
      final InputStream is = conn.getInputStream();
      int ch;
      final StringBuilder b = new StringBuilder();
      final byte[] buffer = new byte[1024];
      
      while( ( ch = is.read( buffer ) ) != -1 ) {
        b.append( new String( buffer, 0, ch ) );
      }
      retval = b.toString();
      // ListActivity.info( "Response: " + retval );
    }
    catch ( final MalformedURLException ex ) {
      ListActivity.error( "HttpFileUploader: " + ex, ex );
      ListActivity.writeError(Thread.currentThread(), ex, context);
      retval = ex.toString();
    }  
    catch ( final IOException ioe ) {
      ListActivity.error( "HttpFileUploader: " + ioe, ioe );
      ListActivity.writeError(Thread.currentThread(), ioe, context);
      retval = ioe.toString();
    }
    finally {
      if ( conn != null ) {
        ListActivity.info( "conn disconnect" );
        conn.disconnect();
      }
    }
    
    return retval;
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
