package net.wigle.wigleandroid;

import java.text.SimpleDateFormat;

import net.wigle.wigleandroid.MainActivity.Doer;

import org.osmdroid.api.IGeoPoint;
import org.osmdroid.api.IMapController;
import org.osmdroid.api.IMapView;
import org.osmdroid.views.MapView;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.text.InputType;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.View.OnClickListener;
import android.view.ViewGroup.LayoutParams;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.CompoundButton.OnCheckedChangeListener;

public class NetworkActivity extends Activity {
  private static final int MENU_EXIT = 11;
  private static final int CRYPTO_DIALOG = 101;
  
  private static final int MSG_OBS_UPDATE = 1;
  private static final int MSG_OBS_DONE = 2;
  
  private Network network;
  private IMapController mapControl;
  private IMapView mapView;
  private SimpleDateFormat format;
  private int observations = 0;
  private ConcurrentLinkedHashMap<LatLon, Integer> obsMap = new ConcurrentLinkedHashMap<LatLon, Integer>( 512 );
  
  // used for shutting extraneous activities down on an error
  public static NetworkActivity networkActivity;

  /** Called when the activity is first created. */
  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.network);
    networkActivity = this;
    
    final Intent intent = getIntent();
    final String bssid = intent.getStringExtra( ListActivity.NETWORK_EXTRA_BSSID );
    ListActivity.info( "bssid: " + bssid );
    
    network = ListActivity.getNetworkCache().get(bssid);
    format = NetworkListAdapter.getConstructionTimeFormater( this );  
    
    TextView tv = (TextView) findViewById( R.id.bssid );
    tv.setText( bssid );
    
    if ( network != null ) {
      // do gui work
      tv = (TextView) findViewById( R.id.ssid );
      tv.setText( network.getSsid() );
      
      final int image = NetworkListAdapter.getImage( network );
      final ImageView ico = (ImageView) findViewById( R.id.wepicon );
      ico.setImageResource( image );
      final ImageView ico2 = (ImageView) findViewById( R.id.wepicon2 );
      ico2.setImageResource( image );
      
      tv = (TextView) findViewById( R.id.na_signal );
      final int level = network.getLevel();
      tv.setTextColor( NetworkListAdapter.getSignalColor( level ) );
      tv.setText( Integer.toString( level ) );
      
      tv = (TextView) findViewById( R.id.na_type ); 
      tv.setText( network.getType().name() );
      
      tv = (TextView) findViewById( R.id.na_firsttime ); 
      tv.setText( NetworkListAdapter.getConstructionTime( format, network ) );
      
      tv = (TextView) findViewById( R.id.na_chan ); 
      if ( ! NetworkType.WIFI.equals(network.getType()) ) {
        tv.setText( getString(R.string.na) );
      }
      else {
        Integer chan = network.getChannel();
        chan = chan != null ? chan : network.getFrequency();
        tv.setText( " " + Integer.toString(chan) + " " );
      }
      
      tv = (TextView) findViewById( R.id.na_cap ); 
      tv.setText( " " + network.getCapabilities().replace("][", "]\n[") );
      
      setupMap( network );
      // kick off the query now that we have our map
      setupQuery();      
      setupButton( network );
    }
  }
  
  public void onDestroy() {    
    networkActivity = null;
    super.onDestroy();
  }
  
  private void setupQuery() {
    // what runs on the gui thread
    final Handler handler = new Handler() {
      @Override
      public void handleMessage( final Message msg ) {        
        final TextView tv = (TextView) findViewById( R.id.na_observe );
        if ( msg.what == MSG_OBS_UPDATE ) {
          tv.setText( " " + Integer.toString( observations ) + "...");
        }
        else if ( msg.what == MSG_OBS_DONE ) {
          tv.setText( " " + Integer.toString( observations ) );
        }
      }
    };
    
    final String sql = "SELECT level,lat,lon FROM " 
      + DatabaseHelper.LOCATION_TABLE + " WHERE bssid = '" + network.getBssid() + "'";
    
    final QueryThread.Request request = new QueryThread.Request( sql, new QueryThread.ResultHandler() {
      public void handleRow( final Cursor cursor ) {
        observations++;
        obsMap.put( new LatLon( cursor.getFloat(1), cursor.getFloat(2) ), cursor.getInt(0) );
        if ( ( observations % 10 ) == 0 ) {
          // change things on the gui thread
          handler.sendEmptyMessage( MSG_OBS_UPDATE );
        }
      }
      
      public void complete() {
        handler.sendEmptyMessage( MSG_OBS_DONE );
        if ( mapView != null ) {
          // force a redraw
          ((View) mapView).postInvalidate();
        }
      }
    });
    ListActivity.lameStatic.dbHelper.addToQueue( request );
  }
    
  private void setupMap( final Network network ) {
    final IGeoPoint point = MappingActivity.getCenter( this, network.getGeoPoint(), null );
    if ( point != null ) {
      // view
      final RelativeLayout rlView = (RelativeLayout) this.findViewById( R.id.netmap_rl );
      
      // possibly choose goog maps here
      mapView = new MapView( this, 256 );     
      
      if ( mapView instanceof View ) {
        ViewGroup.LayoutParams params = new ViewGroup.LayoutParams(
          LayoutParams.FILL_PARENT, LayoutParams.FILL_PARENT);
        ((View) mapView).setLayoutParams(params);
      }
      
      if ( mapView instanceof MapView ) {
        final MapView osmMapView = (MapView) mapView;
        rlView.addView( osmMapView );
        osmMapView.setBuiltInZoomControls( true );
        osmMapView.setMultiTouchControls( true );
        
        final OpenStreetMapViewWrapper overlay = new OpenStreetMapViewWrapper( this );
        overlay.setSingleNetwork( network );
        overlay.setObsMap( obsMap );
        osmMapView.getOverlays().add( overlay );
      }
      mapControl = mapView.getController();
      
      mapControl.setCenter( point );
      mapControl.setZoom( 16 );
      mapControl.setCenter( point );
    }
  }
  
  private void setupButton( final Network network ) {
    final Button connectButton = (Button) findViewById( R.id.connect_button );
    if ( ! NetworkType.WIFI.equals(network.getType()) ) {
      connectButton.setEnabled( false );
    }
        
    connectButton.setOnClickListener( new OnClickListener() {
      public void onClick( final View buttonView ) {    
        if ( Network.CRYPTO_NONE == network.getCrypto() ) {
          doNonCryptoDialog();
        }
        else {
          NetworkActivity.this.showDialog( CRYPTO_DIALOG );
        }
      }
    });
  }
  
  private int getExistingSsid( final String ssid ) {
    final WifiManager wifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);
    final String quotedSsid = "\"" + ssid + "\"";      
    int netId = -2;
    
    for ( final WifiConfiguration config : wifiManager.getConfiguredNetworks() ) {
      ListActivity.info( "bssid: " + config.BSSID 
          + " ssid: " + config.SSID 
          + " status: " + config.status
          + " id: " + config.networkId
          + " preSharedKey: " + config.preSharedKey
          + " priority: " + config.priority
          + " wepTxKeyIndex: " + config.wepTxKeyIndex
          + " allowedAuthAlgorithms: " + config.allowedAuthAlgorithms
          + " allowedGroupCiphers: " + config.allowedGroupCiphers
          + " allowedKeyManagement: " + config.allowedKeyManagement
          + " allowedPairwiseCiphers: " + config.allowedPairwiseCiphers
          + " allowedProtocols: " + config.allowedProtocols
          + " hiddenSSID: " + config.hiddenSSID
          + " wepKeys: " + config.wepKeys
          );
      if ( quotedSsid.equals( config.SSID ) ) {
        netId = config.networkId;
        break;
      }
    }
    
    return netId;
  }
  
  private void doNonCryptoDialog() {
    MainActivity.createConfirmation( NetworkActivity.this, "You have permission to access this network?", new Doer() {
      @Override
      public void execute() {     
        connectToNetwork( null );
      }
    } );
  }
  
  private void connectToNetwork( final String password ) {
    final int preExistingNetId = getExistingSsid( network.getSsid() );    
    final WifiManager wifiManager = (WifiManager) getSystemService( Context.WIFI_SERVICE );
    int netId = -2;
    if ( preExistingNetId < 0 ) {
      final WifiConfiguration newConfig = new WifiConfiguration();     
      newConfig.SSID = "\"" + network.getSsid() + "\"";
      newConfig.hiddenSSID = false;
      if ( password != null ) {
        if ( Network.CRYPTO_WEP == network.getCrypto() ) {
          newConfig.wepKeys = new String[]{ "\"" + password + "\"" };
        }
        else {
          newConfig.preSharedKey = "\"" + password + "\"";
        }
      }
      
      netId = wifiManager.addNetwork( newConfig );      
    }
    
    if ( netId >= 0 ) {
      final boolean disableOthers = true;
      wifiManager.enableNetwork(netId, disableOthers);      
    }
  }
  
  @Override
  public Dialog onCreateDialog( int which ) {
    switch ( which ) {
      case CRYPTO_DIALOG:        
        final Dialog dialog = new Dialog( this );

        dialog.setContentView( R.layout.cryptodialog );
        dialog.setTitle( network.getSsid() );

        TextView text = (TextView) dialog.findViewById( R.id.security );
        text.setText( network.getCapabilities() );
        
        text = (TextView) dialog.findViewById( R.id.signal );
        text.setText( Integer.toString( network.getLevel() ) );
        
        final Button ok = (Button) dialog.findViewById( R.id.ok_button );
        
        final EditText password = (EditText) dialog.findViewById( R.id.edit_password );
        password.addTextChangedListener( new SettingsActivity.SetWatcher() {
          public void onTextChanged( final String s ) {
            if ( s.length() > 0 ) {
              ok.setEnabled(true);
            }
          } 
        });
        
        final CheckBox showpass = (CheckBox) dialog.findViewById( R.id.showpass );
        showpass.setOnCheckedChangeListener(new OnCheckedChangeListener() {
          public void onCheckedChanged( final CompoundButton buttonView, final boolean isChecked ) { 
            if ( isChecked ) {
              password.setInputType( InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD );
              password.setTransformationMethod( null );
            }
            else {
              password.setInputType( InputType.TYPE_TEXT_VARIATION_PASSWORD );
              password.setTransformationMethod(
                android.text.method.PasswordTransformationMethod.getInstance() ); 
            }
          }
        });
        
        ok.setOnClickListener( new OnClickListener() {
            public void onClick( final View buttonView ) {  
              try {
                connectToNetwork( password.getText().toString() );
                dialog.dismiss();
              }
              catch ( Exception ex ) {
                // guess it wasn't there anyways
                ListActivity.info( "exception dismissing crypto dialog: " + ex );
              }
            }
          } );
        
        Button cancel = (Button) dialog.findViewById( R.id.cancel_button );
        cancel.setOnClickListener( new OnClickListener() {
            public void onClick( final View buttonView ) {  
              try {
                dialog.dismiss();
              }
              catch ( Exception ex ) {
                // guess it wasn't there anyways
                ListActivity.info( "exception dismissing crypto dialog: " + ex );
              }
            }
          } );
        
        return dialog;
      default:
        ListActivity.error( "NetworkActivity: unhandled dialog: " + which );
    }
    return null;
  }
  
  /* Creates the menu items */
  @Override
  public boolean onCreateOptionsMenu( final Menu menu ) {
      MenuItem item = menu.add(0, MENU_EXIT, 0, getString(R.string.menu_return));
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
