package net.wigle.wigleandroid;

import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

import android.app.Activity;
import android.content.SharedPreferences;
import android.location.Location;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Handler;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.TextView;

public class DashboardActivity extends Activity {
  private Handler timer;
  private AtomicBoolean finishing;
  private NumberFormat numberFormat;
  
  private static final int MENU_RETURN = 12;
  
  /** Called when the activity is first created. */
  @Override
  public void onCreate( final Bundle savedInstanceState ) {
    super.onCreate( savedInstanceState );
    setContentView( R.layout.dash );
    
    // media volume
    this.setVolumeControlStream( AudioManager.STREAM_MUSIC );  
    
    finishing = new AtomicBoolean( false );
    numberFormat = NumberFormat.getNumberInstance( Locale.US );
    if ( numberFormat instanceof DecimalFormat ) {
      ((DecimalFormat) numberFormat).setMaximumFractionDigits( 2 );
    }
    
    setupTimer();
  }
  
  private void setupTimer() {
    if ( timer == null ) {
      timer = new Handler();
      final Runnable mUpdateTimeTask = new Runnable() {
        public void run() {              
            // make sure the app isn't trying to finish
            if ( ! finishing.get() ) {
              updateUI();
              
              final long period = 1000L;
              // info("wifitimer: " + period );
              timer.postDelayed( this, period );
            }
            else {
              WigleAndroid.info( "finishing mapping timer" );
            }
        }
      };
      timer.removeCallbacks( mUpdateTimeTask );
      timer.postDelayed( mUpdateTimeTask, 100 );
    }
  }
  
  private void updateUI() {
    TextView tv = (TextView) findViewById( R.id.runnets );
    tv.setText( "Run Nets: " + WigleAndroid.lameStatic.runNets );
    
    tv = (TextView) findViewById( R.id.newnets );
    tv.setText( "New Nets: " + WigleAndroid.lameStatic.newNets );
    
    tv = (TextView) findViewById( R.id.currnets );
    tv.setText( "Visible Nets: " + WigleAndroid.lameStatic.currNets );
    
    updateDist( R.id.rundist, WigleAndroid.PREF_DISTANCE_RUN, "Run Distance: " );
    updateDist( R.id.totaldist, WigleAndroid.PREF_DISTANCE_TOTAL, "Total Distance: " );
    
    tv = (TextView) findViewById( R.id.queuesize );
    tv.setText( "DB Queue: " + WigleAndroid.lameStatic.preQueueSize );
    
    tv = (TextView) findViewById( R.id.gpsstatus );
    Location location = WigleAndroid.lameStatic.location;
    String gpsStatus = "No Location!";
    if ( location != null ) {
      gpsStatus = location.getProvider();
    }
    tv.setText( "Loc: " + gpsStatus );
  }
  
  private void updateDist( int id, String pref, String title ) {
    final SharedPreferences prefs = this.getSharedPreferences( WigleAndroid.SHARED_PREFS, 0 );
    
    float dist = prefs.getFloat( pref, 0f );
    String distString = null;
    // english? metric? we've got the f'in gun!
    if ( dist > 1000f ) {
      distString = numberFormat.format( dist / 1609.344f ) + " miles";
    }
    else {
      distString = numberFormat.format( dist ) + " meters";
    }
    final TextView tv = (TextView) findViewById( id );
    tv.setText( title + distString );
    
  }
  
  @Override
  public void finish() {
    WigleAndroid.info( "finish dash." );
    finishing.set( true );
    
    super.finish();
  }
  
  @Override
  public void onDestroy() {
    WigleAndroid.info( "destroy dash." );
    finishing.set( true );
    
    super.onDestroy();
  }
  
  /* Creates the menu items */
  @Override
  public boolean onCreateOptionsMenu( final Menu menu ) {
      MenuItem item = menu.add(0, MENU_RETURN, 0, "Return");
      item.setIcon( android.R.drawable.ic_media_previous );
      
      return true;
  }

  /* Handles item selections */
  @Override
  public boolean onOptionsItemSelected( final MenuItem item ) {
      switch ( item.getItemId() ) {
        case MENU_RETURN:
          finish();
          return true;
      }
      return false;
  }
}
