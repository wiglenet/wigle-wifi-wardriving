package net.wigle.wigleandroid;

import net.wigle.wigleandroid.MainActivity.Doer;

import org.osmdroid.api.IMapController;
import org.osmdroid.api.IMapView;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

public class NetworkActivity extends Activity {
  private static final int MENU_EXIT = 11;
  private Network network;
  private IMapController mapControl;
  private IMapView mapView;

  /** Called when the activity is first created. */
  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.network);
    
    final Intent intent = getIntent();
    final String bssid = intent.getStringExtra( ListActivity.NETWORK_EXTRA_BSSID );
    ListActivity.info( "bssid: " + bssid );
    
    network = ListActivity.getNetworkCache().get(bssid);
    
    TextView tv = (TextView) findViewById( R.id.bssid );
    tv.setText( bssid );
    
    if ( network != null ) {
      tv = (TextView) findViewById( R.id.ssid );
      tv.setText( network.getSsid() );
      
      final ImageView ico = (ImageView) findViewById( R.id.wepicon );
      ico.setImageResource( NetworkListAdapter.getImage( network ) );
    
      GeoPoint point = network.getGeoPoint();
      // ListActivity.info("point: " + point );
      if ( point != null ) {
        // view
        final RelativeLayout rlView = (RelativeLayout) this.findViewById( R.id.netmap_rl );
        
        // possibly choose goog maps here
        OpenStreetMapViewWrapper osmvw = new OpenStreetMapViewWrapper( this );  
        osmvw.setSingleNetwork( network );
        mapView = osmvw;
        
        if ( mapView instanceof MapView ) {
          MapView osmMapView = (MapView) mapView;
          rlView.addView( osmMapView );
          osmMapView.setBuiltInZoomControls( true );
          osmMapView.setMultiTouchControls( true );
        }
        mapControl = mapView.getController();
        
        mapControl.setCenter( point );
        mapControl.setZoom( 16 );
        mapControl.setCenter( point );
      }
      
      setupButton( network );
    }
  }
  
  private void setupButton( final Network network ) {
    final Button connectButton = (Button) findViewById( R.id.connect_button );
    connectButton.setOnClickListener( new OnClickListener() {
      public void onClick( final View buttonView ) {    
        MainActivity.createConfirmation( NetworkActivity.this, "You have permission to access this network?", new Doer() {
          @Override
          public void execute() {          
            final WifiManager wifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);
            final String ssid = "\"" + network.getSsid() + "\"";            
            int netId = -2;
            
            for ( final WifiConfiguration config : wifiManager.getConfiguredNetworks() ) {
              ListActivity.info( "bssid: " + config.BSSID + " ssid: " + config.SSID + " status: " + config.status
                  + " id: " + config.networkId );
              if ( ssid.equals( config.SSID ) ) {
                netId = config.networkId;
                break;
              }
            }
            
            if ( netId < 0 ) {
              final WifiConfiguration newConfig = new WifiConfiguration();     
              newConfig.SSID = ssid;
              netId = wifiManager.addNetwork( newConfig );
            }
            
            if ( netId >= 0 ) {
              final boolean disableOthers = true;
              wifiManager.enableNetwork(netId, disableOthers);
            }
          }
        } );
      }
    });
  }
  
  /* Creates the menu items */
  @Override
  public boolean onCreateOptionsMenu( final Menu menu ) {
      MenuItem item = menu.add(0, MENU_EXIT, 0, "Return");
      item.setIcon( android.R.drawable.ic_menu_revert );
      return true;
  }

  /* Handles item selections */
  @Override
  public boolean onOptionsItemSelected( final MenuItem item ) {
      switch ( item.getItemId() ) {
        case MENU_EXIT:
          // call over to finish
          finish();
          return true;
      }
      return false;
  }
}
