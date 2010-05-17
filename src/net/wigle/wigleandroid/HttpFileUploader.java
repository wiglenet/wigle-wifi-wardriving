package net.wigle.wigleandroid;

import android.content.res.Resources; 
import android.os.Handler;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
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

/**
 * Based on   http://getablogger.blogspot.com/2008/01/android-how-to-post-file-to-php-server.html
 * Read more: http://getablogger.blogspot.com/2008/01/android-how-to-post-file-to-php-server.html#ixzz0iqTJF7SV
 */
final class HttpFileUploader {
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
                               final Resources res, final Handler handler, final long filesize ) {
    
    URL connectURL = null;
    try{
      connectURL = new URL(urlString);
    }
    catch( Exception ex ){
      WigleAndroid.error("MALFORMATED URL: " + ex);
    }
    
    final String lineEnd = "\r\n";
    final String twoHyphens = "--";
    final String boundary = "*****";
    String retval = null;

    try {
      //------------------ CLIENT REQUEST
    
      WigleAndroid.info("Creating url connection");

      // Open a HTTP connection to the URL
      CharsetEncoder enc = Charset.forName( WigleAndroid.ENCODING ).newEncoder();
      CharBuffer cbuff = CharBuffer.allocate( 1024 );
      ByteBuffer bbuff = ByteBuffer.allocate( 1024 );

      final HttpURLConnection conn = (HttpURLConnection) connectURL.openConnection();
    
      // Allow Inputs
      conn.setDoInput(true);
      // Allow Outputs
      conn.setDoOutput(true);
      // Don't use a cached copy.
      conn.setUseCaches(false);
      conn.setInstanceFollowRedirects( false );
      if ( conn instanceof javax.net.ssl.HttpsURLConnection ) {
          final SSLConfigurator con = SSLConfigurator.getInstance( res );
          con.configure( (javax.net.ssl.HttpsURLConnection) conn );
          WigleAndroid.info("using ssl!");
      }
    
      // Use a post method.
      conn.setRequestMethod("POST");
      conn.setRequestProperty("Connection", "Keep-Alive");
      conn.setRequestProperty("Content-Type", "multipart/form-data;boundary="+boundary);

      WritableByteChannel wbc = Channels.newChannel( conn.getOutputStream() );
      
      StringBuilder header = new StringBuilder( 400 ); // find a better guess. it was 281 for me in the field 2010/05/16 -hck
      for ( Map.Entry<String, String> entry : params.entrySet() ) {
        header.append( twoHyphens + boundary + lineEnd );
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

      writeString( wbc, header.toString(), enc, cbuff, bbuff );

      WigleAndroid.info( "Headers are written ("+header.length()+")" );
      int percentDone = ( (int)header.length() * 100) / (int)filesize;
      if ( handler != null ) {
          handler.sendEmptyMessage( FileUploaderTask.WRITING_PERCENT_START + percentDone );
      }

    
      FileChannel fc = fileInputStream.getChannel();
      long byteswritten = fc.transferTo( 0, Integer.MAX_VALUE, wbc ); // transfer it all. the integer cap is reasonable.
      WigleAndroid.info( "transferred "+byteswritten+" of "+filesize );
      percentDone = ((int)byteswritten * 100) / (int)filesize;

      // only send it the once... if we want to to send updates out to the ui:
      // hook on a wraper to the writeable byte channel, which of course would bugger the transfer() native call
      if ( handler != null ) {
          handler.sendEmptyMessage( FileUploaderTask.WRITING_PERCENT_START + percentDone );
      }

      // send multipart form data necesssary after file data...
      header.setLength( 0 ); // clear()
      header.append(lineEnd);
      header.append(twoHyphens + boundary + twoHyphens + lineEnd);
      writeString( wbc, header.toString(), enc, cbuff, bbuff );

      // close streams
      WigleAndroid.info( "File is written" );
      wbc.close();
      fc.close();
      fileInputStream.close();
    
      // this is dirty.  dirty.
      final InputStream is = conn.getInputStream();
      // retrieve the response from server
      int ch;
    
      final StringBuffer b = new StringBuffer();
      while( ( ch = is.read() ) != -1 ) {
        b.append( (char)ch );
      }
      retval = b.toString();
      // WigleAndroid.debug( "Response: " + retval );
    }
    catch ( final MalformedURLException ex ) {
      WigleAndroid.error( ex.toString() );
    }  
    catch ( final IOException ioe ) {
      WigleAndroid.error( ioe.toString() );
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

        if ( CoderResult.UNDERFLOW != enc.encode( cbuff, bbuff, true ) ) {
            throw new IOException("encode fail");
        }
        if ( CoderResult.UNDERFLOW != enc.flush( bbuff ) ) {
            throw new IOException("flush fail");
        }
        bbuff.flip();

        int remaining = bbuff.remaining();
        while ( remaining > 0 ) {
            remaining -= wbc.write( bbuff ); 
        }
    }
  
}
