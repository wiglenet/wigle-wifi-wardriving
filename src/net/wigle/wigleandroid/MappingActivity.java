package net.wigle.wigleandroid;

import java.util.concurrent.atomic.AtomicBoolean;

import org.andnav.osm.util.GeoPoint;
import org.andnav.osm.views.OpenStreetMapViewController;

import android.app.Activity;
import android.location.Location;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Handler;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.TextView;

/**
 * show a map!
 */
public class MappingActivity extends Activity {
  private OpenStreetMapViewController mapControl;
  private OpenStreetMapViewWrapper mapView;
  private Handler timer;
  private AtomicBoolean finishing;
  
  private static final int MENU_RETURN = 12;
  
  /** Called when the activity is first created. */
  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.map);
    finishing = new AtomicBoolean( false );
    
    // media volume
    this.setVolumeControlStream(AudioManager.STREAM_MUSIC);  
    
    setupMapView();
    setupTimer();
  }
  
  private void setupMapView() {
    mapView = (OpenStreetMapViewWrapper) this.findViewById( R.id.mapview );
    mapControl = new OpenStreetMapViewController( mapView );
    mapControl.setCenter( new GeoPoint( 41973076, -87672736 ) );
    mapControl.setZoom( 15 );
    mapControl.setCenter( new GeoPoint( 41973076, -87672736 ) );
    
    WigleAndroid.info("done setupMapView");
  }
  
  private void setupTimer() {
    if ( timer == null ) {
      timer = new Handler();
      Runnable mUpdateTimeTask = new Runnable() {
        public void run() {              
            // make sure the app isn't trying to finish
            if ( ! finishing.get() ) {
              Location location = WigleAndroid.lameStatic.location;
              if ( location != null ) {
                // WigleAndroid.info( "mapping center location: " + location );
								final GeoPoint locGeoPoint = new GeoPoint( location );
                mapView.latestLocation( locGeoPoint );
                mapControl.animateTo( locGeoPoint );
              }
              String savedStats = WigleAndroid.lameStatic.savedStats;
              if ( savedStats != null ) {
                TextView tv = (TextView) findViewById( R.id.stats );
                tv.setText( savedStats );
              }
              
              long period = 1000L;
              // info("wifitimer: " + period );
              timer.postDelayed( this, period );
            }
            else {
              WigleAndroid.info( "finishing mapping timer" );
            }
        }
      };
      timer.removeCallbacks(mUpdateTimeTask);
      timer.postDelayed(mUpdateTimeTask, 100);
    }
  }
    
  @Override
  public void finish() {
    WigleAndroid.info( "finish mapping." );
    finishing.set( true );
    
    super.finish();
  }
  
  @Override
  public void onDestroy() {
    WigleAndroid.info( "destroy mapping." );
    finishing.set( true );
    
    super.onDestroy();
  }
  
  /* Creates the menu items */
  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
      MenuItem item = menu.add(0, MENU_RETURN, 0, "Return");
      item.setIcon( android.R.drawable.ic_media_previous );
      return true;
  }

  /* Handles item selections */
  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
      switch ( item.getItemId() ) {
        case MENU_RETURN:
          finish();
          return true;
      }
      return false;
  }
  
}
