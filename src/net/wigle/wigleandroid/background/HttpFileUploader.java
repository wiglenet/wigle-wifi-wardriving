package net.wigle.wigleandroid.background;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.net.UnknownHostException;
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

import net.wigle.wigleandroid.ListActivity;
import net.wigle.wigleandroid.SSLConfigurator;
import android.content.Context;
import android.content.res.Resources;
import android.os.Handler;

/**
 * Based on   http://getablogger.blogspot.com/2008/01/android-how-to-post-file-to-php-server.html
 * Read more: http://getablogger.blogspot.com/2008/01/android-how-to-post-file-to-php-server.html#ixzz0iqTJF7SV
 */
final class HttpFileUploader {
  private static final String ENCODING = "UTF-8";
  public static final String LINE_END = "\r\n";
  public static final String TWO_HYPHENS = "--";
  public static final String BOUNDARY = "*****";  
  
  /** don't allow construction */
  private HttpFileUploader(){
  }
  
  public static HttpURLConnection connect(String urlString, final Resources res,
      final boolean setBoundary) throws IOException {
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
    boolean fallback=true;
    try {
        ListActivity.info("testcon1");
        URLConnection testcon = connectURL.openConnection();
        // we should probably time-bound this test-connection?
        testcon.connect();
        self_serving = false;
        // consider a pref to skip this? or just phase out after migration?
    } 
    catch (UnknownHostException ex) {
        // dns is broke, try the last known ip
        urlString = urlString.replace("wigle.net", "205.234.142.193");
        connectURL = new URL( urlString );
        try {
            ListActivity.info("testcon1.1");
            URLConnection testcon = connectURL.openConnection();
            // we should probably time-bound this test-connection?
            testcon.connect();
            self_serving = false;
        } 
        catch (IOException ioEx) {
            // we're specifically interested in javax.net.ssl.SSLException
        }
    } 
    catch (IOException ex) {
        // we're specifically interested in javax.net.ssl.SSLException
    }

    if ( self_serving ) {
        try {
            ListActivity.info("testcon2");
            URLConnection testcon = connectURL.openConnection();
            if ( testcon instanceof javax.net.ssl.HttpsURLConnection ) {
                SSLConfigurator con = SSLConfigurator.getInstance( res );
                con.configure( (javax.net.ssl.HttpsURLConnection) testcon );
                testcon.connect();
                fallback = false;
            }
        } 
        catch (IOException ex) {
          // ListActivity.info("testcon ex: " + ex, ex);
        }
    }
    ListActivity.info("end testcons");
    
    HttpURLConnection conn = null;
    ListActivity.info("Creating url connection. self_serving: " + self_serving + " fallback: " + fallback);
    
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
    } catch (Exception e) { }                                                        
                                                                                     
    final String userAgent = "WigleWifi ("+javaVersion+")";                    
    
    // Open a HTTP connection to the URL    
    conn = (HttpURLConnection) connectURL.openConnection();    
    // Allow Inputs
    conn.setDoInput(true);
    // Allow Outputs
    conn.setDoOutput(true);
    // Don't use a cached copy.
    conn.setUseCaches(false);
    conn.setInstanceFollowRedirects( false );
    if ( ( conn instanceof javax.net.ssl.HttpsURLConnection ) && self_serving ) {
        final SSLConfigurator con = SSLConfigurator.getInstance( res, fallback );
        con.configure( (javax.net.ssl.HttpsURLConnection) conn );
        ListActivity.info("using ssl! conn: " + conn);
    }
    
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
    
    // connect
    ListActivity.info( "about to connect" );
    conn.connect();  
    ListActivity.info( "connected" );
    
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
   * @param res the app resources (needed for looking up SSL cert)
   * @param handler if non-null gets empty messages with updates on progress
   * @param filesize guess at filesize for UI callbacks
   */
  public static String upload( final String urlString, final String filename, final String fileParamName,
                               final FileInputStream fileInputStream, final Map<String,String> params, 
                               final Resources res, final Handler handler, final long filesize,
                               final Context context ) throws IOException {
     
    String retval = null;
    HttpURLConnection conn = null;
    
    try {
      final boolean setBoundary = true;
      conn = connect( urlString, res, setBoundary);    
      OutputStream connOutputStream = conn.getOutputStream();

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
      
      WritableByteChannel wbc = Channels.newChannel( connOutputStream );
      
      StringBuilder header = new StringBuilder( 400 ); // find a better guess. it was 281 for me in the field 2010/05/16 -hck
      for ( Map.Entry<String, String> entry : params.entrySet() ) {
        header.append( TWO_HYPHENS ).append( BOUNDARY ).append( LINE_END );
        header.append( "Content-Disposition: form-data; name=\""+ entry.getKey() + "\"" + LINE_END );
        header.append( LINE_END );
        header.append( entry.getValue() );
        header.append( LINE_END );
      }
      
      header.append( TWO_HYPHENS + BOUNDARY + LINE_END );
      header.append( "Content-Disposition: form-data; name=\"" + fileParamName 
                     + "\";filename=\"" + filename +"\"" + LINE_END );
      header.append( "Content-Type: application/octet_stream" + LINE_END );
      header.append( LINE_END );

      ListActivity.info( "About to write headers, length: " + header.length() );
      CharsetEncoder enc = Charset.forName( ENCODING ).newEncoder();
      CharBuffer cbuff = CharBuffer.allocate( 1024 );
      ByteBuffer bbuff = ByteBuffer.allocate( 1024 );
      writeString( wbc, header.toString(), enc, cbuff, bbuff );

      ListActivity.info( "Headers are written, length: " + header.length() );
      int percentTimesTenDone = ( (int)header.length() * 1000) / (int)filesize;
      if ( handler != null && percentTimesTenDone >= 0 ) {
          handler.sendEmptyMessage( BackgroundGuiHandler.WRITING_PERCENT_START + percentTimesTenDone );
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
        percentTimesTenDone = ((int)byteswritten * 1000) / (int)filesize;

        if ( handler != null && percentTimesTenDone >= 0 ) {
          handler.sendEmptyMessage( BackgroundGuiHandler.WRITING_PERCENT_START + percentTimesTenDone );
        }
      }
      ListActivity.info( "done. transferred " + byteswritten + " of " + filesize );

      // send multipart form data necesssary after file data...
      header.setLength( 0 ); // clear()
      header.append(LINE_END);
      header.append(TWO_HYPHENS + BOUNDARY + TWO_HYPHENS + LINE_END);
      writeString( wbc, header.toString(), enc, cbuff, bbuff );

      // close streams
      ListActivity.info( "File is written" );
      wbc.close();
      fc.close();
      fileInputStream.close();
      
      int responseCode = conn.getResponseCode();
      ListActivity.info( "connection response code: " + responseCode );

      // read the response
      final InputStream is = getInputStream( conn );
      int ch;
      final StringBuilder b = new StringBuilder();
      final byte[] buffer = new byte[1024];
      
      while( ( ch = is.read( buffer ) ) != -1 ) {
          b.append( new String( buffer, 0, ch, ENCODING ) );
      }
      retval = b.toString();
      // ListActivity.info( "Response: " + retval );
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
   * get the InputStream, gunzip'ing if needed
   */
  public static InputStream getInputStream( HttpURLConnection conn ) throws IOException {
    InputStream input = conn.getInputStream();

    String encode = conn.getContentEncoding();
    ListActivity.info( "Encoding: " + encode );
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
