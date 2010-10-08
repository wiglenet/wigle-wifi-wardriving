package net.wigle.wigleandroid;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

import android.content.Context;
import android.database.Cursor;
import android.os.Bundle;
import android.os.Environment;
import android.os.Process;

public class KmlWriter extends Thread {
  private static final String FILENAME = "filename";
  private static final String FILEPATH = "filepath";
  private static final int UPLOAD_PRIORITY = Process.THREAD_PRIORITY_BACKGROUND;
  
  private final Context applicationContext;
  private final DatabaseHelper dbHelper;
  
  public KmlWriter( final Context context, final DatabaseHelper dbHelper ) {
    if ( context == null ) {
      throw new IllegalArgumentException( "context is null" );
    }
    if ( dbHelper == null ) {
      throw new IllegalArgumentException( "dbHelper is null" );
    }
    
    this.applicationContext = context.getApplicationContext();
    this.dbHelper = dbHelper;
  }
  
  private boolean writeKml( Bundle bundle ) throws FileNotFoundException, IOException {
    final boolean hasSD = ListActivity.hasSD();
    if ( ! hasSD ) {
      return false;
    }
    final String filepath = Environment.getExternalStorageDirectory().getCanonicalPath() + "/wiglewifi/";
    final File path = new File( filepath );
    path.mkdirs();

    final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    final SimpleDateFormat fileDateFormat = new SimpleDateFormat("yyyyMMddHHmmss");
    final String filename = "WigleWifi_" + fileDateFormat.format(new Date()) + ".kml";
    String openString = filepath + filename;
    ListActivity.info("openString: " + openString );
    File file = new File( openString );
    if ( ! file.exists() && hasSD ) {
      file.createNewFile();
    }
    
    FileOutputStream fos = new FileOutputStream( file );
    FileUploaderTask.writeFos( fos, "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
        + "<kml xmlns=\"http://www.opengis.net/kml/2.2\"><Document>"
        + "<Style id=\"red\"><IconStyle><Icon><href>http://maps.google.com/mapfiles/ms/icons/red-dot.png</href></Icon></IconStyle></Style>"
        + "<Style id=\"yellow\"><IconStyle><Icon><href>http://maps.google.com/mapfiles/ms/icons/yellow-dot.png</href></Icon></IconStyle></Style>"
        + "<Style id=\"green\"><IconStyle><Icon><href>http://maps.google.com/mapfiles/ms/icons/green-dot.png</href></Icon></IconStyle></Style>"
        + "<Folder><name>Wifi Networks</name>\n" );
    final Cursor cursor = dbHelper.networkIterator();
    int lineCount = 0;
    final int total = cursor.getCount();
    for ( cursor.moveToFirst(); ! cursor.isAfterLast(); cursor.moveToNext() ) {
      // bssid,ssid,frequency,capabilities,lasttime,lastlat,lastlon
      final String bssid = cursor.getString(0);
      final String ssid = cursor.getString(1);
      final int frequency = cursor.getInt(2);
      final String capabilities = cursor.getString(3);
      final long lasttime = cursor.getLong(4);
      final double lastlat = cursor.getDouble(5);
      final double lastlon = cursor.getDouble(6);
      final String date = dateFormat.format( new Date( lasttime ) );
      
      String style = "green";
      if ( capabilities.indexOf("WEP") >= 0 ) {
        style = "yellow";
      }
      if ( capabilities.indexOf("WPA") >= 0 ) {
        style = "red";
      }
      String ssidFiltered = new String( ssid.getBytes( ListActivity.ENCODING ) );
      ssidFiltered = ssidFiltered.replaceAll("[^\\w", "");
      
      FileUploaderTask.writeFos( fos, "<Placemark>\n<name><![CDATA[" + ssidFiltered + "]]></name>\n" );
      FileUploaderTask.writeFos( fos, "<description><![CDATA[BSSID: <b>" + bssid + "</b><br/>"
          + "Capabilities: <b>" + capabilities + "</b><br/>Frequency: <b>" + frequency + "</b><br/>"
          + "Timestamp: <b>" + lasttime + "</b><br/>Date: <b>" + date + "</b>]]></description><styleUrl>#" + style + "</styleUrl>\n" );
      FileUploaderTask.writeFos( fos, "<Point>\n" );
      FileUploaderTask.writeFos( fos, "<coordinates>" + lastlon + "," + lastlat + "</coordinates>" );
      FileUploaderTask.writeFos( fos, "</Point>\n</Placemark>\n" );

      lineCount++;
      if ( (lineCount % 1000) == 0 ) {
        ListActivity.info("lineCount: " + lineCount + " of " + total );
      }
if ( lineCount > 10000) {
  break;
}
    }
    FileUploaderTask.writeFos( fos, "</Folder>\n</Document></kml>" );
    
    cursor.close();
    fos.close();    
    
    bundle.putString( FILEPATH, filepath );
    bundle.putString( FILENAME, filename );
    ListActivity.info( "done with kml export" );
    return true;
  }
  
  public void run() {
    try {
      ListActivity.info( "setting file export thread priority (-20 highest, 19 lowest) to: " + UPLOAD_PRIORITY );
      Process.setThreadPriority( UPLOAD_PRIORITY );
      
      Bundle bundle = new Bundle();
      writeKml( bundle );
    }
    catch ( final Throwable throwable ) {
      ListActivity.writeError( Thread.currentThread(), throwable, applicationContext );
      throw new RuntimeException( "FileUploaderTask throwable: " + throwable, throwable );
    }
    finally {
      // tell the listener
//      listener.uploadComplete();
    }
  }
}
