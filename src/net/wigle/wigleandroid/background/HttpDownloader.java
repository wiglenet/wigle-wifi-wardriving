package net.wigle.wigleandroid.background;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URLEncoder;

import net.wigle.wigleandroid.DatabaseHelper;
import net.wigle.wigleandroid.ListActivity;
import android.content.Context;
import android.os.Bundle;

public class HttpDownloader extends AbstractBackgroundTask {
  private final FileUploaderListener listener;
  
  public HttpDownloader( final Context context, final DatabaseHelper dbHelper, 
      final FileUploaderListener listener ) {
    
    super(context, dbHelper, "HttpDL");
    this.listener = listener;
  }
  
  protected void subRun() throws IOException, InterruptedException {
    try {
      final String username = getUsername();
      final String password = getPassword();
      Status status = validateUserPass( username, password );
      final Bundle bundle = new Bundle();
      if ( status == null ) {
        status = doDownload( username, password, bundle );
      }
      
      // tell the gui thread
      sendBundledMessage( status.ordinal(), bundle );
    }
    finally {
      // tell the listener
      listener.transferComplete();
    }
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
        }
      }
      if ( line.length() != 12 || line.startsWith( "<" ) ) {
        continue;
      }
      
      ListActivity.info("line: " + line);
      
//      todo: insert line into db
      
      lineCount++;
      if ( (lineCount % 1000) == 0 ) {
        ListActivity.info("lineCount: " + lineCount + " of " + totalCount );
      }
      
      // update UI
      if ( totalCount == 0 ) {
        totalCount = 1;
      }
      final int percentDone = (int)((lineCount * 100) / totalCount);
      sendPercent( percentDone, bundle );      
    }        
  }
  
}
