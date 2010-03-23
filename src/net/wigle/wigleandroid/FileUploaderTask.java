package net.wigle.wigleandroid;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPOutputStream;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;

public class FileUploaderTask extends Thread {
  private final Context context;
  private final Handler handler;
  private final List<Network> networksList;
  
  public FileUploaderTask( Context context, Handler handler, List<Network> networksList ) {
    this.context = context;
    this.handler = handler;
    this.networksList = networksList;
  }
  
  public void run() {
    SharedPreferences prefs = context.getSharedPreferences( WigleAndroid.SHARED_PREFS, 0);
    String username = prefs.getString( WigleAndroid.PREF_USERNAME, "" );
    String password = prefs.getString( WigleAndroid.PREF_PASSWORD, "" );
    
    SimpleDateFormat fileDateFormat = new SimpleDateFormat("yyyyMMddHHmmss");
    String filename = "WigleWifi_" + fileDateFormat.format(new Date()) + ".csv.gz";
    
    if ( "".equals( username ) ) {
      // TODO: error
      WigleAndroid.error( "username not defined" );
      return;
    }
    
    if ( "".equals( password ) && ! "anonymous".equals( username.toLowerCase() ) ) {
      // TODO: error
      WigleAndroid.error( "password not defined and username isn't 'anonymous'" );
      return;
    }
    
    SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    boolean ok = false;
    
    try {
      FileOutputStream rawFos = context.openFileOutput( filename, Context.MODE_PRIVATE );
      GZIPOutputStream fos = new GZIPOutputStream( rawFos );
      // name, version
      writeFos( fos, "WigleWifi-1.0\n" );
      // header
      writeFos( fos, "MAC,SSID,AuthMode,FirstSeen,Channel,RSSI,CurrentLatitude,CurrentLongitude\n" );
      // write file
      for ( Network network : networksList ) {
        String ssid = network.getSsid();
        ssid = ssid.replaceAll(",", "_"); // comma isn't a legal ssid character, but just in case
        WigleAndroid.debug("writing network: " + ssid + " observations: " + network.getObservations() );
        for ( Observation observation : network.getObservations() ) {
          writeFos( fos, network.getBssid(), "," );
          writeFos( fos, ssid, "," );
          writeFos( fos, network.getCapabilities(), "," );
          writeFos( fos, dateFormat.format( new Date( observation.getTime() ) ), "," );
          writeFos( fos, Integer.toString( network.getChannel() ), "," );
          writeFos( fos, Integer.toString( observation.getLevel() ), "," );
          writeFos( fos, Double.toString( observation.getLat() ), "," );
          writeFos( fos, Double.toString( observation.getLon() ), "\n" );
          WigleAndroid.debug("writing observation: " + observation.getLevel() + " lat: " + observation.getLat() 
              + " lon: " + observation.getLon() );
        }          
      }
      fos.close();
      
      // send file
      FileInputStream fis = context.openFileInput( filename );
      Map<String,String> params = new HashMap<String,String>();
      
      params.put("observer", username);
      params.put("password", password);
      String response = HttpFileUploader.upload( WigleAndroid.FILE_POST_URL, filename, "stumblefile", fis, params );
      
      if ( response.indexOf("uploaded successfully") > 0 ) {
        ok = true;
      }
    } 
    catch (FileNotFoundException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
      WigleAndroid.error( "file problem: " + e );
    }
    catch ( IOException ex ) {
      ex.printStackTrace();
      WigleAndroid.error( "file problem: " + ex );
    }
    
    // tell the gui thread
    handler.sendEmptyMessage( ok ? 1 : 0 );
  }
  
  private void writeFos( OutputStream fos, String... data ) throws IOException, UnsupportedEncodingException {
    if ( data != null ) {
      for ( String item : data ) {
        if ( item != null ) {
          fos.write( item.getBytes( WigleAndroid.ENCODING ) );
        }
      }
    }
  }
   
}
