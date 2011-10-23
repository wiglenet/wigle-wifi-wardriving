package net.wigle.wigleandroid.background;

import java.io.BufferedReader;
import java.io.IOException;

import net.wigle.wigleandroid.DatabaseHelper;
import net.wigle.wigleandroid.ListActivity;
import android.content.Context;
import android.os.Bundle;

public class HttpDownloader extends AbstractBackgroundTask {
  
  public HttpDownloader( final Context context, final DatabaseHelper dbHelper ) {
    super(context, dbHelper, "HttpDL");
  }
  
  protected void subRun() throws IOException {
    // todo
  }
  
  private boolean insertObserved( final BufferedReader reader, final Bundle bundle ) throws IOException, InterruptedException {
    
    String line = null;
    int lineCount = 0;
    int totalCount = -1;
    
    while ( (line = reader.readLine()) != null ) {
      if ( wasInterrupted() ) {
        throw new InterruptedException( "we were interrupted" );
      }
      
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
    
    return true;
  }
}
