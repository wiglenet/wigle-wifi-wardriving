package net.wigle.wigleandroid.background;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URLEncoder;

import net.wigle.wigleandroid.DatabaseHelper;
import net.wigle.wigleandroid.ListActivity;
import net.wigle.wigleandroid.Network;
import net.wigle.wigleandroid.NetworkType;
import android.content.Context;
import android.location.Location;
import android.os.Bundle;

public class HttpDownloader extends AbstractBackgroundTask {
  private final FileUploaderListener listener;
  
  public HttpDownloader( final Context context, final DatabaseHelper dbHelper, 
      final FileUploaderListener listener ) {
    
    super(context, dbHelper, "HttpDL");
    this.listener = listener;
  }
  
  protected void subRun() throws IOException, InterruptedException {
    Status status = Status.UNKNOWN;
    final Bundle bundle = new Bundle();
    try {
      final String username = getUsername();
      final String password = getPassword();
      status = validateUserPass( username, password );      
      if ( status == null ) {
        status = doDownload( username, password, bundle );
      }
      
    }
    catch ( final InterruptedException ex ) {
      ListActivity.info("Download Interrupted: " + ex);
    }      
    catch ( final Exception ex ) {
      ex.printStackTrace();
      ListActivity.error( "ex problem: " + ex, ex );
      ListActivity.writeError( this, ex, context );
      status = Status.EXCEPTION;
      bundle.putString( BackgroundGuiHandler.ERROR, "ex problem: " + ex );
    }
    finally {
      // tell the listener
      listener.transferComplete();
    }
    
    if ( status == null ) {
      status = Status.FAIL;
    }

    // tell the gui thread
    sendBundledMessage( status.ordinal(), bundle );
  }
   
  private Status doDownload( final String username, final String password, final Bundle bundle ) 
      throws IOException, InterruptedException {
    
    final boolean setBoundary = false;
    HttpURLConnection conn = HttpFileUploader.connect( 
        ListActivity.OBSERVED_URL, context.getResources(), setBoundary );
    
    // Send POST output.
    final DataOutputStream printout = new DataOutputStream (conn.getOutputStream ());
    String content = "observer=" + URLEncoder.encode ( username ) +
        "&password=" + URLEncoder.encode ( password );
    printout.writeBytes( content );
    printout.flush();
    printout.close();
    
    // get response data
    DataInputStream input = new DataInputStream ( HttpFileUploader.getInputStream( conn ) );
    insertObserved( input );
    return Status.WRITE_SUCCESS;
  }
  
  private void insertObserved( final DataInputStream reader ) 
      throws IOException, InterruptedException {
    
    final Bundle bundle = new Bundle();
    String line = null;
    int lineCount = 0;
    int totalCount = -1;
    final String COUNT_TAG = "count=";
    
    while ( (line = reader.readLine()) != null ) {
      if ( wasInterrupted() ) {
        throw new InterruptedException( "we were interrupted" );
      }
      
      if ( totalCount < 0 ) {
        if ( ! line.startsWith(COUNT_TAG) ) {
          continue;
        }
        else {
          totalCount = Integer.parseInt(line.substring(COUNT_TAG.length()));
          ListActivity.info("observed totalCount: " + totalCount);
        }
      }
      if ( line.length() != 12 || line.startsWith( "<" ) ) {
        continue;
      }
      
      // re-colon
      StringBuilder builder = new StringBuilder(15);
      for ( int i = 0; i < 12; i++ ) {
        builder.append(line.charAt(i));
        if ( i < 11 && (i%2) == 1 ) {
          builder.append(":");
        }
      }
      
      final String bssid = builder.toString();
      // ListActivity.info("line: " + line + " bssid: " + bssid);
      
      // do the insert      
      final String ssid = "";
      final int frequency = 0;
      final String capabilities = "";
      final int level = 0;
      final Network network = new Network(bssid, ssid, frequency, capabilities, level, NetworkType.WIFI);
      final Location location = new Location("wigle");
      final boolean newForRun = true;
      dbHelper.blockingAddObservation( network, location, newForRun );
      
      lineCount++;
      if ( (lineCount % 1000) == 0 ) {
        ListActivity.info("lineCount: " + lineCount + " of " + totalCount );
      }
      
      // update UI
      if ( totalCount == 0 ) {
        totalCount = 1;
      }
      final int percentDone = (int)((lineCount * 1000) / totalCount);
      sendPercentTimesTen( percentDone, bundle );      
    }        
  }
  
}
