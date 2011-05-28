package net.wigle.wigleandroid;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

import android.content.Context;
import android.database.Cursor;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.os.Process;

public class KmlWriter extends Thread {
  private static final int UPLOAD_PRIORITY = Process.THREAD_PRIORITY_BACKGROUND;
  private static final int WRITING_DONE = 10;
  
  private final DatabaseHelper dbHelper;
  private final Set<String> networks;
  private final Handler handler;
  
  public KmlWriter( final Context context, final DatabaseHelper dbHelper ) {
    this( context, dbHelper, (Set<String>) null );
  }
  
  public KmlWriter( final Context context, final DatabaseHelper dbHelper, final Set<String> networks ) {
    if ( context == null ) {
      throw new IllegalArgumentException( "context is null" );
    }
    if ( dbHelper == null ) {
      throw new IllegalArgumentException( "dbHelper is null" );
    }
    
    this.dbHelper = dbHelper;
    // make a safe local copy
    this.networks = (networks == null) ? null : new HashSet<String>( networks );
    
    this.handler = new Handler() {
      @Override
      public void handleMessage( final Message msg ) {
        if ( msg.what == WRITING_DONE ) {
          FileUploaderTask.buildAlertDialog( context, msg, FileUploaderTask.Status.WRITE_SUCCESS );        }
        }
    };

  }
  
  private boolean writeKml( Bundle bundle ) throws FileNotFoundException, IOException {
    final boolean hasSD = ListActivity.hasSD();
    if ( ! hasSD ) {
      return false;
    }
    final String filepath = MainActivity.safeFilePath( Environment.getExternalStorageDirectory() ) + "/wiglewifi/";
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
    // header
    FileUploaderTask.writeFos( fos, "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
        + "<kml xmlns=\"http://www.opengis.net/kml/2.2\"><Document>"
        + "<Style id=\"red\"><IconStyle><Icon><href>http://maps.google.com/mapfiles/ms/icons/red-dot.png</href></Icon></IconStyle></Style>"
        + "<Style id=\"yellow\"><IconStyle><Icon><href>http://maps.google.com/mapfiles/ms/icons/yellow-dot.png</href></Icon></IconStyle></Style>"
        + "<Style id=\"green\"><IconStyle><Icon><href>http://maps.google.com/mapfiles/ms/icons/green-dot.png</href></Icon></IconStyle></Style>"
        + "<Folder><name>Wifi Networks</name>\n" );
    
    boolean retval = false;
    
    // body
    Cursor cursor = null;
    if ( true ) {
      try {
        if ( this.networks == null ) {
          cursor = dbHelper.networkIterator();    
          retval = writeKmlFromCursor( fos, cursor, dateFormat );
        }
        else {
          for ( String network : networks ) {
            // ListActivity.info( "network: " + network );
            cursor = dbHelper.getSingleNetwork( network ); 
            // avoiding |= operator cuz of old validator wierdness
            retval = writeKmlFromCursor( fos, cursor, dateFormat ) || retval;
            cursor.close();
            cursor = null;
          }
        }
      }    
      catch ( DBException ex ) {
        dbHelper.deathDialog("Writing Kml", ex);
      }
      finally {
        if ( cursor != null ) {
          cursor.close();
        }
      }
    } 
    // footer
    FileUploaderTask.writeFos( fos, "</Folder>\n</Document></kml>" );
    
    fos.close();    
    
    bundle.putString( FileUploaderTask.FILEPATH, filepath );
    bundle.putString( FileUploaderTask.FILENAME, filename );
    ListActivity.info( "done with kml export" );
    
    // tell gui
    Message message = new Message();
    message.what = WRITING_DONE;
    message.setData( bundle );
    handler.sendMessage( message );
    
    return retval;
  }
  
  private boolean writeKmlFromCursor( final OutputStream fos, final Cursor cursor, final SimpleDateFormat dateFormat ) 
      throws IOException {
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
      
      // not unicode. ha ha for them!
      byte[] ssidFiltered = ssid.getBytes( ListActivity.ENCODING );
      filterIllegalXml( ssidFiltered );
      
      FileUploaderTask.writeFos( fos, "<Placemark>\n<name><![CDATA[" );
      fos.write( ssidFiltered );
      FileUploaderTask.writeFos( fos, "]]></name>\n" );
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
      // if ( lineCount > 10000) {
      //  break;
      // }
    }
    
    return true;
  }
  
  private void filterIllegalXml( byte[] data ) {
    for ( int i = 0; i < data.length; i++ ) {
      byte current = data[i];
      // (0x00, 0x08), (0x0B, 0x1F), (0x7F, 0x84), (0x86, 0x9F)
      if ( (current >= 0x00 && current <= 0x08) ||
           (current >= 0x0B && current <= 0x1F) ||
           (current >= 0x7F && current <= 0x84) ||
           (current >= 0x86 && current <= 0x9F)
          ) {
        data[i] = ' ';
      }
    }
  }
  
  public void run() {
    // set thread name
    setName( "KmlWriter-" + getName() );
    
    try {
      ListActivity.info( "setting file export thread priority (-20 highest, 19 lowest) to: " + UPLOAD_PRIORITY );
      Process.setThreadPriority( UPLOAD_PRIORITY );
      
      Bundle bundle = new Bundle();
      writeKml( bundle );      
    }
    catch ( final Exception ex ) {
      dbHelper.deathDialog("Writing Kml", ex);
    }    
  }
}
