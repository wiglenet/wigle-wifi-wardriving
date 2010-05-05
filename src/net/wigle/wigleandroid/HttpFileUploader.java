package net.wigle.wigleandroid;

import android.content.res.Resources; 

import java.io.DataOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Map;

/**
 * Based on   http://getablogger.blogspot.com/2008/01/android-how-to-post-file-to-php-server.html
 * Read more: http://getablogger.blogspot.com/2008/01/android-how-to-post-file-to-php-server.html#ixzz0iqTJF7SV
 */
final class HttpFileUploader {
  /** don't allow construction */
  private HttpFileUploader(){
  }

  public static String upload( final String urlString, final String filename, final String fileParamName,
       final FileInputStream fileInputStream, final Map<String,String> params, final Resources res ){
    
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
      final DataOutputStream dos = new DataOutputStream( conn.getOutputStream() );
      
      for ( Map.Entry<String, String> entry : params.entrySet() ) {
        dos.writeBytes( twoHyphens + boundary + lineEnd );
        dos.writeBytes( "Content-Disposition: form-data; name=\""+ entry.getKey() + "\"" + lineEnd );
        dos.writeBytes( lineEnd );
        dos.writeBytes( entry.getValue() );
        dos.writeBytes( lineEnd );
      }
      
      dos.writeBytes( twoHyphens + boundary + lineEnd );
      dos.writeBytes( "Content-Disposition: form-data; name=\"" + fileParamName 
          + "\";filename=\"" + filename +"\"" + lineEnd );
      dos.writeBytes( "Content-Type: application/octet_stream" + lineEnd );
      dos.writeBytes( lineEnd);
    
      WigleAndroid.info( "Headers are written" );
    
      // create a buffer of maximum size
      int bytesAvailable = fileInputStream.available();
      final int maxBufferSize = 1024;
      int bufferSize = Math.min(bytesAvailable, maxBufferSize);
      byte[] buffer = new byte[bufferSize];
    
      // read file and write it into form...
      int bytesRead = fileInputStream.read(buffer, 0, bufferSize);
    
      while (bytesRead > 0) {
        WigleAndroid.info( "writing " + bufferSize + " bytes" );
        dos.write(buffer, 0, bufferSize);
        bytesAvailable = fileInputStream.available();
        bufferSize = Math.min(bytesAvailable, maxBufferSize);
        bytesRead = fileInputStream.read(buffer, 0, bufferSize);
      }
    
      // send multipart form data necesssary after file data...
      dos.writeBytes(lineEnd);
      dos.writeBytes(twoHyphens + boundary + twoHyphens + lineEnd);
    
      // close streams
      WigleAndroid.info( "File is written" );
      fileInputStream.close();
      dos.flush();
    
      final InputStream is = conn.getInputStream();
      // retrieve the response from server
      int ch;
    
      final StringBuffer b = new StringBuffer();
      while( ( ch = is.read() ) != -1 ) {
        b.append( (char)ch );
      }
      retval = b.toString();
      // WigleAndroid.debug( "Response: " + retval );

      dos.close();
    }
    catch ( final MalformedURLException ex ) {
      WigleAndroid.error( ex.toString() );
    }  
    catch ( final IOException ioe ) {
      WigleAndroid.error( ioe.toString() );
    }
    
    return retval;
  }
  
}
