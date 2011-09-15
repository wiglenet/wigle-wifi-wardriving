package net.wigle.wigleandroid;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

import org.osmdroid.api.IGeoPoint;
import org.osmdroid.api.IMapView;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.location.Address;
import android.location.Location;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;

public class DBResultActivity extends Activity {
  private static final int MENU_RETURN = 12;
  private static final int MENU_SETTINGS = 13;
  private static final int MSG_OBS_DONE = 2;  
  private static final int LIMIT = 50;
  
  private NetworkListAdapter listAdapter;
  private IMapView mapView;
  private List<Network> resultList = new ArrayList<Network>();
  
  @Override
  public void onCreate( final Bundle savedInstanceState) {
    super.onCreate( savedInstanceState );
    setContentView( R.layout.dbresult );
      
    // force media volume controls
    setVolumeControlStream( AudioManager.STREAM_MUSIC );
    
    setupList();
    
    QueryArgs queryArgs = ListActivity.lameStatic.queryArgs;
    ListActivity.info("queryArgs: " + queryArgs);
    final TextView tv = (TextView) findViewById( R.id.dbstatus );    
    
    if ( queryArgs != null ) {
      tv.setText( getString(R.string.status_working) + "...");
      Address address = queryArgs.getAddress();
      IGeoPoint center = MappingActivity.DEFAULT_POINT;
      if ( address != null ) {
        center = new GeoPoint(address.getLatitude(), address.getLongitude());
      }
      setupMap( center );
      setupQuery( queryArgs );
    }
    else {
      tv.setText( getString(R.string.status_fail) + "...");
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
  
  private void setupQuery( final QueryArgs queryArgs ) {
    // what runs on the gui thread
    final Handler handler = new Handler() {
      @Override
      public void handleMessage( final Message msg ) {        
        if ( msg.what == MSG_OBS_DONE ) {
          final TextView tv = (TextView) findViewById( R.id.dbstatus );          
          tv.setText( getString(R.string.status_success)  );
          
          listAdapter.clear();
          for ( final Network network : resultList ) {
            listAdapter.add( network );
          }
          resultList.clear();
        }
      }
    };
    
    String sql = "SELECT bssid,lastlat,lastlon FROM " + DatabaseHelper.NETWORK_TABLE + " WHERE 1=1 ";
    final String ssid = queryArgs.getSSID();
    final Address address = queryArgs.getAddress();
    if ( ssid != null && ! "".equals(ssid) ) {
      sql += " AND ssid = '" + ssid + "'";
    }
    if ( address != null ) {
      final double diff = 0.5d;
      final double lat = address.getLatitude();
      final double lon = address.getLongitude();
      sql += " AND lastlat > '" + (lat - diff) + "' AND lastlat < '" + (lat + diff) + "'";
      sql += " AND lastlon > '" + (lon - diff) + "' AND lastlon < '" + (lon + diff) + "'";
    }
    sql += " LIMIT " + LIMIT;
    
    final TreeMap<Float,String> top = new TreeMap<Float,String>();
    final float[] results = new float[1];
    final int[] count = new int[1];
    
    final QueryThread.Request request = new QueryThread.Request( sql, new QueryThread.ResultHandler() {
      public void handleRow( final Cursor cursor ) {
        final String bssid = cursor.getString(0);
        final float lat = cursor.getFloat(1);
        final float lon = cursor.getFloat(2);
        count[0]++;
        
        if ( address == null ) {
          top.put( (float) count[0], bssid );
        }
        else {
          Location.distanceBetween( lat, lon, address.getLatitude(), address.getLongitude(), results );        
          final float meters = results[0];
          
          if ( top.size() <= LIMIT ) {
            putWithBackoff( top, bssid, meters );
          }
          else {
            Float last = top.lastKey();
            if ( meters < last ) {
              top.remove( last );
              putWithBackoff( top, bssid, meters );
            }
          }
        }
      }
      
      public void complete() {
        boolean first = false;
        for ( String bssid : top.values() ) {          
          Network network = ListActivity.lameStatic.dbHelper.getNetwork( bssid );
          resultList.add( network );
          if ( address == null && first ) {
            final IGeoPoint center = MappingActivity.getCenter( DBResultActivity.this, network.getGeoPoint(), null );
            mapView.getController().setCenter( center );
            first = false;
          }
        }
        
        handler.sendEmptyMessage( MSG_OBS_DONE );
        if ( mapView != null ) {
          // force a redraw
          ((View) mapView).postInvalidate();
        }
      }
    });
    
    // queue it up
    ListActivity.lameStatic.dbHelper.addToQueue( request );
  }    
  
  private static void putWithBackoff( TreeMap<Float,String> top, String s, float diff ) {
    String old = top.put( diff, s );
    // protect against infinite loops
    int count = 0;
    while ( old != null && count < 1000 ) {
      // ut oh, two at the same difference away. add a slight bit and put it back
      // info( "collision at diff: " + diff + " old: " + old.getCallsign() + " orig: " + s.getCallsign() );
      diff += 0.0001f;
      old = top.put( diff, old );
      count++;
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
