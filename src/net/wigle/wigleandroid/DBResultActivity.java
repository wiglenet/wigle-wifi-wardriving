package net.wigle.wigleandroid;

import org.osmdroid.api.IGeoPoint;
import org.osmdroid.api.IMapView;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;

import android.app.Activity;
import android.content.Intent;
import android.location.Address;
import android.media.AudioManager;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;

public class DBResultActivity extends Activity {
  private static final int MENU_RETURN = 12;
  private static final int MENU_SETTINGS = 13;
  
  private NetworkListAdapter listAdapter;
  private IMapView mapView;
  
  @Override
  public void onCreate( final Bundle savedInstanceState) {
    super.onCreate( savedInstanceState );
    setContentView( R.layout.dbresult );
      
    // force media volume controls
    setVolumeControlStream( AudioManager.STREAM_MUSIC );
    
    setupList();
    
    QueryArgs queryArgs = ListActivity.lameStatic.queryArgs;
    ListActivity.info("queryArgs: " + queryArgs);
    if ( queryArgs != null ) {
      Address address = queryArgs.getAddress();
      IGeoPoint center = MappingActivity.DEFAULT_POINT;
      if ( address != null ) {
        center = new GeoPoint(address.getLatitude(), address.getLongitude());
      }
      setupMap( center );
      // final IGeoPoint center = MappingActivity.getCenter( this, network.getGeoPoint(), null );
    }
  }
  
  private void setupList() {
    // not set by nonconfig retain
    listAdapter = new NetworkListAdapter( getApplicationContext(), R.layout.row );
    ListActivity.setupListAdapter( this, listAdapter, R.id.dblist );
  }
  
  private void setupMap( final IGeoPoint center ) {
    mapView = new MapView( this, 256 );
    final OpenStreetMapViewWrapper overlay = NetworkActivity.setupMap( this, center, mapView, R.id.db_map_rl );
    if ( overlay != null ) {            
    }
  }
  
  /* Creates the menu items */
  @Override
  public boolean onCreateOptionsMenu( final Menu menu ) {
    MenuItem item = menu.add(0, MENU_RETURN, 0, getString(R.string.menu_return));
    item.setIcon( android.R.drawable.ic_media_previous );
            
    item = menu.add( 0, MENU_SETTINGS, 0, getString(R.string.menu_settings) );
    item.setIcon( android.R.drawable.ic_menu_preferences );      
      
    return true;
  }

  /* Handles item selections */
  @Override
  public boolean onOptionsItemSelected( final MenuItem item ) {
      switch ( item.getItemId() ) {
        case MENU_RETURN:
          finish();
          return true;
        case MENU_SETTINGS:
          final Intent settingsIntent = new Intent( this, SettingsActivity.class );
          startActivity( settingsIntent );
          break;        
      }
      return false;
  }
      
}
