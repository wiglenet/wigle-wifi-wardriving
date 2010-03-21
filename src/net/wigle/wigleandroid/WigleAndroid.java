package net.wigle.wigleandroid;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.location.GpsStatus;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.location.GpsStatus.Listener;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

public class WigleAndroid extends Activity {
    // private static final String WIFI_LOCK_NAME = "wifilock";
    private LayoutInflater mInflater;
    private ArrayAdapter<String> listAdapter;
    private final Map<String,Network> networks = new ConcurrentHashMap<String,Network>();
    private GpsStatus gpsStatus;
    private Location location;
    private Handler wifiTimer;
    private static final String LOG_TAG = "wigle";
    
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        
        setupList();
        setupWifi();
        setupLocation();
    }
    
    @Override
    public void onPause() {
      info( "paused" );
      super.onPause();
    }
    
    @Override
    public void onResume() {
      info( "redumed" );
      super.onResume();
    }
    
    private static final int MENU_SETTINGS = 10;
    private static final int MENU_EXIT = 11;
    
    /* Creates the menu items */
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuItem item = menu.add(0, MENU_EXIT, 0, "Exit");
        item.setIcon( android.R.drawable.ic_menu_close_clear_cancel );
        
        item = menu.add(0, MENU_SETTINGS, 0, "Settings");
        item.setIcon( android.R.drawable.ic_menu_preferences );
        return true;
    }

    /* Handles item selections */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch ( item.getItemId() ) {
          case MENU_SETTINGS:
            info("settings");
            Intent intent = new Intent( this, SettingsActivity.class );
            this.startActivity( intent );
            return true;
          case MENU_EXIT:
            finish();
            return true;
        }
        return false;
    }
    
    private void setupList() {
        mInflater = (LayoutInflater) getSystemService( Context.LAYOUT_INFLATER_SERVICE );
        
        listAdapter = new ArrayAdapter<String>( this, R.layout.row ) {
            @Override
            public View getView( int position, View convertView, ViewGroup parent ) {
              View row;
        
              if ( null == convertView ) {
                row = mInflater.inflate( R.layout.row, null );
              } else {
                row = convertView;
              }
        
              String bssid = getItem(position);
              Network network = networks.get( bssid );
              
              TextView tv = (TextView) row.findViewById( R.id.ssid );              
              tv.setText( network.getSsid() );
              
              int channel = network.getChannel() != null ? network.getChannel() : network.getFrequency();
              
              tv = (TextView) row.findViewById( R.id.detail ); 
              StringBuilder detail = new StringBuilder();
              detail.append( network.getLevel() );
              detail.append( " | " ).append( network.getBssid() );
              detail.append( " - " ).append( channel );
              detail.append( " - " ).append( network.getShowCapabilities() );
              
              tv.setText( detail.toString() );
        
              return row;
            }
          };
        
        ListView listView = (ListView) findViewById( R.id.ListView01 );
        listView.setAdapter( listAdapter ); 
    }
    
    private void setupWifi() {
        final WifiManager wifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);
        // wifi scan listener
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction (WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);
        registerReceiver( new BroadcastReceiver(){
            public void onReceive(Context c, Intent i){
              List<ScanResult> results = wifiManager.getScanResults(); // Returns a <list> of scanResults
              for ( ScanResult result : results ) {
                Network network = networks.get( result.BSSID );
                if ( network == null ) {
                  info( "new network: " + result.SSID );
                  network = new Network( result );
                  networks.put( result.BSSID, network );
                  listAdapter.add( result.BSSID );
                }
                info( "network: " + result.SSID + " level: " + result.level );
                // always set new signal level
                network.addObservation( result.level, location );
                // notify
                listAdapter.notifyDataSetChanged();
              }
            }
          }, intentFilter );
        
        // WifiLock wifiLock = wifiManager.createWifiLock( WIFI_LOCK_NAME );
        // wifiLock.acquire();  
        
        wifiTimer = new Handler();
        Runnable mUpdateTimeTask = new Runnable() {
          public void run() {
              long period = 1000L;
              info( "timer start scan" );
              wifiManager.startScan();
              wifiTimer.postDelayed(this, period );
          }
        };
        wifiTimer.removeCallbacks(mUpdateTimeTask);
        wifiTimer.postDelayed(mUpdateTimeTask, 100);

        // starts scan, sends event when done
        boolean scanOK = wifiManager.startScan();
        info( "scanOK: " + scanOK );        
    }
    
    private void setupLocation() {
      // set on UI if we already have one
      setLocationUI( location );
      
      final LocationManager locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
      locationManager.addGpsStatusListener( new Listener(){
          public void onGpsStatusChanged( int event ) {
            gpsStatus = locationManager.getGpsStatus( gpsStatus );
          }
        });
      
      List<String> providers = locationManager.getAllProviders();
      for ( String provider : providers ) {
        info( "provider: " + provider );
        locationManager.requestLocationUpdates(provider, 5000L, 0, new LocationListener(){
          public void onLocationChanged( Location newLocation ) {
            location = newLocation;
            setLocationUI( location );
          }
          public void onProviderDisabled( String provider ) {
          }
          public void onProviderEnabled( String provider ) { 
          }
          public void onStatusChanged( String provider, int status, Bundle extras ) {
          }
        });
      }
    }
    
    private void setLocationUI( Location location ) {
      if ( location != null ) {
        TextView tv = (TextView) findViewById( R.id.LocationTextView01 );
        tv.setText( "Lat: " + (float) location.getLatitude() );
        
        tv = (TextView) findViewById( R.id.LocationTextView02 );
        tv.setText( "Lon: " + (float) location.getLongitude() );
        
        tv = (TextView) findViewById( R.id.LocationTextView03 );
        tv.setText( "+/- " + location.getAccuracy() + "m" );
      }
    }
    
    public static void info( String value ) {
      Log.i( LOG_TAG, value );
    }
    public static void debug( String value ) {
      Log.d( LOG_TAG, value );
    }
}