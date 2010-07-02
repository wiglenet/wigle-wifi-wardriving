// -*- Mode: Java; tab-width: 2; indent-tabs-mode: nil; c-basic-offset: 2 -*-
// vim:ts=2:sw=2:tw=80:et

package net.wigle.wigleandroid;

import static android.location.LocationManager.GPS_PROVIDER;
import static android.location.LocationManager.NETWORK_PROVIDER;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import org.andnav.osm.util.GeoPoint;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Color;
import android.location.GpsSatellite;
import android.location.GpsStatus;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.location.GpsStatus.Listener;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnErrorListener;
import android.net.Uri;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiManager.WifiLock;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.provider.Settings;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.View.OnClickListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

public final class WigleAndroid extends Activity {
    // state. anything added here should be added to the retain copy-construction
    private ArrayAdapter<Network> listAdapter;
    private Set<String> runNetworks;
    private GpsStatus gpsStatus;
    private Location location;
    private Location networkLocation;
    private Location prevGpsLocation;
    private Handler wifiTimer;
    private DatabaseHelper dbHelper;
    private ServiceConnection serviceConnection;
    private AtomicBoolean finishing;
    private String savedStats;
    private long prevNewNetCount;
    private Long satCountLowTime = 0L;

    // set these times to avoid NPE in locationOK() seen by <DooMMasteR>
    private Long lastLocationTime = 0L;
    private Long lastNetworkLocationTime = 0L;
    private long scanRequestTime = Long.MIN_VALUE;

    private MediaPlayer soundPop;
    private MediaPlayer soundNewPop;
    private WifiLock wifiLock;
    
    // created every time, even after retain
    private Listener gpsStatusListener;
    private LocationListener locationListener;
    private BroadcastReceiver wifiReceiver;
    private NumberFormat numberFormat1;
    private NumberFormat numberFormat8;
    private TTS tts;
    private AudioManager audioManager;
    private long previousTalkTime = System.currentTimeMillis();
    private boolean inEmulator;
    private boolean isPhoneActive;
    private int pendingCount = 0;
    
    public static final String FILE_POST_URL = "https://wigle.net/gps/gps/main/confirmfile/";
    private static final String LOG_TAG = "wigle";
    private static final int MENU_SETTINGS = 10;
    private static final int MENU_EXIT = 11;
    private static final int MENU_MAP = 12;
    private static final int MENU_DASH = 13;
    public static final String ENCODING = "ISO-8859-1";
    private static final long GPS_TIMEOUT = 15000L;
    private static final long NET_LOC_TIMEOUT = 60000L;
    private static final float MIN_DISTANCE_ACCURACY = 32f;
    
    // color by signal strength
    public static final int COLOR_1 = Color.rgb( 70, 170,  0);
    public static final int COLOR_2 = Color.rgb(170, 170,  0);
    public static final int COLOR_3 = Color.rgb(170,  95, 30);
    public static final int COLOR_4 = Color.rgb(180,  60, 40);
    public static final int COLOR_5 = Color.rgb(180,  45, 70);
    
    // preferences
    static final String SHARED_PREFS = "WiglePrefs";
    static final String PREF_USERNAME = "username";
    static final String PREF_PASSWORD = "password";
    static final String PREF_SHOW_CURRENT = "showCurrent";
    static final String PREF_BE_ANONYMOUS = "beAnonymous";
    static final String PREF_DB_MARKER = "dbMarker";
    static final String PREF_SCAN_PERIOD = "scanPeriod";
    static final String PREF_FOUND_SOUND = "foundSound";
    static final String PREF_FOUND_NEW_SOUND = "foundNewSound";
    static final String PREF_SPEECH_PERIOD = "speechPeriod";
    static final String PREF_SPEECH_GPS = "speechGPS";
    static final String PREF_MUTED = "muted";
    static final String PREF_WIFI_WAS_OFF = "wifiWasOff";
    static final String PREF_DISTANCE_RUN = "distRun";
    static final String PREF_DISTANCE_TOTAL = "distTotal";
    static final String PREF_MAP_ONLY_NEWDB = "mapOnlyNewDB";
    
    static final long DEFAULT_SPEECH_PERIOD = 60L;
    
    static final String ANONYMOUS = "anonymous";
    private static final String WIFI_LOCK_NAME = "wigleWifiLock";
    //static final String THREAD_DEATH_MESSAGE = "threadDeathMessage";
    
    /** cross-activity communication */
    public static class TrailStat {
      public int newForRun = 0;
      public int newForDB = 0;
    }
    public static class LameStatic {
      public Location location; 
      public String savedStats;
      public ConcurrentLinkedHashMap<GeoPoint,TrailStat> trail = 
        new ConcurrentLinkedHashMap<GeoPoint,TrailStat>( 512 );
      public int runNets;
      public long newNets;
      public int currNets;
      public int preQueueSize;
    }
    public static final LameStatic lameStatic = new LameStatic();
    
    // cache
    private static final ThreadLocal<CacheMap<String,Network>> networkCache = new ThreadLocal<CacheMap<String,Network>>() {
      protected CacheMap<String,Network> initialValue() {
          return new CacheMap<String,Network>( 16, 64 );
      }
    };
    
    private static final Comparator<Network> signalCompare = new Comparator<Network>() {
      public int compare( Network a, Network b ) {
        return b.getLevel() - a.getLevel();
      }
    };
    
    /** Called when the activity is first created. */
    @Override
    public void onCreate( final Bundle savedInstanceState ) {
        super.onCreate( savedInstanceState );
        setContentView( R.layout.main );
        
        final String id = Settings.Secure.getString( getContentResolver(), Settings.Secure.ANDROID_ID );
        inEmulator = id == null;
        inEmulator |= "sdk".equals( android.os.Build.PRODUCT );
        inEmulator |= "google_sdk".equals( android.os.Build.PRODUCT );
        info( "id: '" + id + "' inEmulator: " + inEmulator + " product: " + android.os.Build.PRODUCT );
        info( "release: '" + Build.VERSION.RELEASE + "'" );
        
//        Thread.setDefaultUncaughtExceptionHandler( new Thread.UncaughtExceptionHandler(){
//          public void uncaughtException( Thread thread, Throwable throwable ) {
//            String error = "Thread: " + thread + " throwable: " + throwable;
//            WigleAndroid.error( error );
//            throwable.printStackTrace();
//            
//            WigleAndroid.writeError( thread, throwable );
//            
//            // throw new RuntimeException( error, throwable );
//            WigleAndroid.this.finish();
//            System.exit( -1 );
//          }
//        });
        
        final Object stored = getLastNonConfigurationInstance();
        if ( stored != null && stored instanceof WigleAndroid ) {
          // pry an orientation change, which calls destroy, but we set this in onRetainNonConfigurationInstance
          final WigleAndroid retained = (WigleAndroid) stored;
          this.listAdapter = retained.listAdapter;
          this.runNetworks = retained.runNetworks;
          this.gpsStatus = retained.gpsStatus;
          this.location = retained.location;
          this.wifiTimer = retained.wifiTimer;
          this.dbHelper = retained.dbHelper;
          this.serviceConnection = retained.serviceConnection;
          this.finishing = retained.finishing;
          this.savedStats = retained.savedStats;
          this.prevNewNetCount = retained.prevNewNetCount;
          this.soundPop = retained.soundPop;
          this.soundNewPop = retained.soundNewPop;
          this.wifiLock = retained.wifiLock;
          
          final TextView tv = (TextView) findViewById( R.id.stats );
          tv.setText( savedStats );
        }
        else {
          runNetworks = new HashSet<String>();
          finishing = new AtomicBoolean( false );
          
          // new run, reset
          final SharedPreferences prefs = this.getSharedPreferences( SHARED_PREFS, 0 );
          Editor edit = prefs.edit();
          edit.putFloat( PREF_DISTANCE_RUN, 0f );
          edit.commit();
        }
        
        numberFormat1 = NumberFormat.getNumberInstance( Locale.US );
        if ( numberFormat1 instanceof DecimalFormat ) {
          ((DecimalFormat) numberFormat1).setMaximumFractionDigits( 1 );
        }
        
        numberFormat8 = NumberFormat.getNumberInstance( Locale.US );
        if ( numberFormat8 instanceof DecimalFormat ) {
          ((DecimalFormat) numberFormat8).setMaximumFractionDigits( 8 );
        }
        
        setupService();
        setupDatabase();
        setupMaxidDebug();
        setupUploadButton();
        setupList();
        setupSound();
        setupWifi();
        setupLocation();
    }
    
    @Override
    public Object onRetainNonConfigurationInstance() {
      info( "onRetainNonConfigurationInstance" );
      // return this whole class to copy data from
      return this;
    }
    
    @Override
    public void onPause() {
      info( "paused. networks: " + runNetworks.size() );
      super.onPause();
    }
    
    @Override
    public void onResume() {
      info( "resumed. networks: " + runNetworks.size() );
      super.onResume();
    }
    
    @Override
    public void onStart() {
      info( "start. networks: " + runNetworks.size() );
      super.onStart();
    }
    
    @Override
    public void onStop() {
      info( "stop. networks: " + runNetworks.size() );
      super.onStop();
    }

    @Override
    public void onRestart() {
      info( "restart. networks: " + runNetworks.size() );
      super.onRestart();
    }
    
    @Override
    public void onDestroy() {
      info( "destroy. networks: " + runNetworks.size() );
      try {
        this.unregisterReceiver( wifiReceiver );
      }
      catch ( final IllegalArgumentException ex ) {
        info( "wifiReceiver not registered: " + ex );
      }
      // stop the service, so when we die it's both stopped and unbound and will die
      final Intent serviceIntent = new Intent( this, WigleService.class );
      this.stopService( serviceIntent );
      try {
        this.unbindService( serviceConnection );
      }
      catch ( final IllegalArgumentException ex ) {
        info( "serviceConnection not registered: " + ex );
      }
      if ( wifiLock != null && wifiLock.isHeld() ) {
        wifiLock.release();
      }

      // clean up.
      if ( soundPop != null ) {
          soundPop.release();
      }
      if ( soundNewPop != null ) {
          soundNewPop.release();
      }
 
      super.onDestroy();
    }
    
    @Override
    public void finish() {
      info( "finish. networks: " + runNetworks.size() );
      finishing.set( true );
      
      // close the db. not in destroy, because it'll still write after that.
      dbHelper.close();
      
      final LocationManager locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
      if ( gpsStatusListener != null ) {
        locationManager.removeGpsStatusListener( gpsStatusListener );
      }
      if ( locationListener != null ) {
        locationManager.removeUpdates( locationListener );
      }
      
      try {
        this.unregisterReceiver( wifiReceiver );
      }
      catch ( final IllegalArgumentException ex ) {
        info( "wifiReceiver not registered: " + ex );
      }

      // stop the service, so when we die it's both stopped and unbound and will die
      final Intent serviceIntent = new Intent( this, WigleService.class );
      this.stopService( serviceIntent );
      try {
        this.unbindService( serviceConnection );
      }
      catch ( final IllegalArgumentException ex ) {
        info( "serviceConnection not registered: " + ex );
      }    
      
      // release the lock before turning wifi off
      if ( wifiLock != null && wifiLock.isHeld() ) {
        wifiLock.release();
      }
      
      final SharedPreferences prefs = this.getSharedPreferences( SHARED_PREFS, 0 );
      final boolean wifiWasOff = prefs.getBoolean( PREF_WIFI_WAS_OFF, false );
      // don't call on emulator, it crashes it
      if ( wifiWasOff && ! inEmulator ) {
        // well turn it of now that we're done
        final WifiManager wifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);
        info( "turning back off wifi" );
        wifiManager.setWifiEnabled( false );
      }
      
      if ( tts != null ) {
        tts.shutdown();
      }
      
      super.finish();
    }
    
    /* Creates the menu items */
    @Override
    public boolean onCreateOptionsMenu( final Menu menu ) {
      MenuItem item = menu.add(0, MENU_DASH, 0, "Dashboard");
      item.setIcon( android.R.drawable.ic_menu_info_details );
      
      item = menu.add(0, MENU_MAP, 0, "Map");
      item.setIcon( android.R.drawable.ic_menu_mapmode );
      
      item = menu.add(0, MENU_EXIT, 0, "Exit");
      item.setIcon( android.R.drawable.ic_menu_close_clear_cancel );
        
      item = menu.add(0, MENU_SETTINGS, 0, "Settings");
      item.setIcon( android.R.drawable.ic_menu_preferences );
        
      return true;
    }

    /* Handles item selections */
    @Override
    public boolean onOptionsItemSelected( final MenuItem item ) {
        switch ( item.getItemId() ) {
          case MENU_SETTINGS: {
            info("start settings activity");
            final Intent intent = new Intent( this, SettingsActivity.class );
            this.startActivity( intent );
            return true;
          }
          case MENU_MAP: {
            info("start map activity");
            final Intent intent = new Intent( this, MappingActivity.class );
            this.startActivity( intent );
            return true;
          }
          case MENU_DASH: {
            info("start dashboard activity");
            final Intent intent = new Intent( this, DashboardActivity.class );
            this.startActivity( intent );
            return true;
          }
          case MENU_EXIT:
            // stop the service, so when we die it's both stopped and unbound and will die
            final Intent serviceIntent = new Intent( this, WigleService.class );
            this.stopService( serviceIntent );
            // call over to finish
            finish();
            // actually kill            
            //System.exit( 0 );
            return true;
        }
        return false;
    }

    // why is this even here? this is retarded. via:
    // http://stackoverflow.com/questions/456211/activity-restart-on-rotation-android
    @Override
    public void onConfigurationChanged( final Configuration newConfig ) {
      super.onConfigurationChanged( newConfig );
      setContentView( R.layout.main );
      info( "on config change" );
      
      // have to redo linkages/listeners
      setupUploadButton();
      setupMuteButton();
      final ListView listView = (ListView) findViewById( R.id.ListView01 );
      listView.setAdapter( listAdapter ); 
    }
    
    private void setupDatabase() {
      // could be set by nonconfig retain
      if ( dbHelper == null ) {
        dbHelper = new DatabaseHelper( this );
        dbHelper.open();
        dbHelper.start();
      }
      
      dbHelper.checkDB();
    }
    
    private void setupList() {
      final LayoutInflater mInflater = (LayoutInflater) getSystemService( Context.LAYOUT_INFLATER_SERVICE );
        
      // may have been set by nonconfig retain
      if ( listAdapter == null ) {
        listAdapter = new ArrayAdapter<Network>( this, R.layout.row ) {
//          @Override
//          public boolean areAllItemsEnabled() {
//            return false;
//          }
//          
//          @Override
//          public boolean isEnabled( int position ) {
//            return false;
//          }
          
          @Override
          public View getView( final int position, final View convertView, final ViewGroup parent ) {
            // long start = System.currentTimeMillis();
            View row;
            
            if ( null == convertView ) {
              row = mInflater.inflate( R.layout.row, parent, false );
            } 
            else {
              row = convertView;
            }
        
            final Network network = getItem(position);
            // info( "listing net: " + network.getBssid() );
            
            final ImageView ico = (ImageView) row.findViewById( R.id.wepicon );   
            switch ( network.getCrypto() ) {
              case Network.CRYPTO_WEP:
                ico.setImageResource( R.drawable.wep_ico );
                break;
              case Network.CRYPTO_WPA:
                ico.setImageResource( R.drawable.wpa_ico );
                break;
              case Network.CRYPTO_NONE:
                ico.setImageResource( R.drawable.no_ico );
                break;
              default:
                throw new IllegalArgumentException( "unhanded crypto: " + network.getCrypto() 
                    + " in network: " + network );
            }
              
            TextView tv = (TextView) row.findViewById( R.id.ssid );              
            tv.setText( network.getSsid() );
              
            tv = (TextView) row.findViewById( R.id.level_string );
            int level = network.getLevel();
            if ( level <= -90 ) {
              tv.setTextColor( COLOR_5 );
            }
            else if ( level <= -80 ) {
              tv.setTextColor( COLOR_4 );
            }
            else if ( level <= -70 ) {
              tv.setTextColor( COLOR_3 );
            }
            else if ( level <= -60 ) {
              tv.setTextColor( COLOR_2 );
            }
            else {
              tv.setTextColor( COLOR_1 );
            }
            tv.setText( Integer.toString( level ) );
            
            tv = (TextView) row.findViewById( R.id.detail );
            String det = network.getDetail();
            tv.setText( det );
            // status( position + " view done. ms: " + (System.currentTimeMillis() - start ) );
        
            return row;
          }
        };
      }
               
      final ListView listView = (ListView) findViewById( R.id.ListView01 );
      listView.setAdapter( listAdapter ); 
    }
    
    private void setupWifi() {
      // warn about turning off network notification
      final String notifOn = Settings.Secure.getString(getContentResolver(), 
          Settings.Secure.WIFI_NETWORKS_AVAILABLE_NOTIFICATION_ON );
      if ( notifOn != null && "1".equals( notifOn ) ) {
        Toast.makeText( this, "For best results, unset \"Network notification\" in"
            + " \"Wireless & networks\"->\"Wi-Fi settings\"", 
            Toast.LENGTH_LONG ).show();
      }
    
      final WifiManager wifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);
      final SharedPreferences prefs = this.getSharedPreferences( SHARED_PREFS, 0 );
      final Editor edit = prefs.edit();
      
      if ( ! wifiManager.isWifiEnabled() ) {
        // save so we can turn it back off when we exit  
        edit.putBoolean( PREF_WIFI_WAS_OFF, true );
        
        // just turn it on, but not in emulator cuz it crashes it
        if ( ! inEmulator ) {
          wifiManager.setWifiEnabled( true );
        }
      }
      else {
        edit.putBoolean( PREF_WIFI_WAS_OFF, false );
      }
      edit.commit();
      
      // wifi scan listener
      // this receiver is the main workhorse of the entire app
      wifiReceiver = new BroadcastReceiver(){
          public void onReceive( final Context context, final Intent intent ){
            final long start = System.currentTimeMillis();

            final List<ScanResult> results = wifiManager.getScanResults(); // return can be null!
            
            long nonstopScanRequestTime = Long.MIN_VALUE;
            final long period = prefs.getLong( PREF_SCAN_PERIOD, 1000L );
            if ( period == 0 ) {
              // treat as "continuous", so request scan in here
              wifiManager.startScan();
              nonstopScanRequestTime = System.currentTimeMillis();
            }
            
            final boolean showCurrent = prefs.getBoolean( PREF_SHOW_CURRENT, true );
            if ( showCurrent ) {
              listAdapter.clear();
            }
            
            final int preQueueSize = dbHelper.getQueueSize();
            final boolean fastMode = dbHelper.isFastMode();
            
            final CacheMap<String,Network> networkCache = getNetworkCache();
            boolean somethingAdded = false;
            int resultSize = 0;
            int newForRun = 0;
            // can be null on shutdown
            if ( results != null ) {
              resultSize = results.size();
              for ( ScanResult result : results ) {
                Network network = networkCache.get( result.BSSID );
                if ( network == null ) {
                  network = new Network( result );
                  networkCache.put( network.getBssid(), network );
                }
                else {
                  // cache hit, just set the level
                  network.setLevel( result.level );
                }
                final boolean added = runNetworks.add( result.BSSID );
                if ( added ) {
                    newForRun++;
                }
                somethingAdded |= added;
                
                // if we're showing current, or this was just added, put on the list
                if ( showCurrent || added ) {
                  listAdapter.add( network );
                  // load test
                  // for ( int i = 0; i< 10; i++) {
                  //  listAdapter.add( network );
                  // }
                  
                }
                else {
                  // not showing current, and not a new thing, go find the network and update the level
                  // this is O(n), ohwell, that's why showCurrent is the default config.
                  for ( int index = 0; index < listAdapter.getCount(); index++ ) {
                    final Network testNet = listAdapter.getItem(index);
                    if ( testNet.getBssid().equals( network.getBssid() ) ) {
                      testNet.setLevel( result.level );
                    }
                  }
                }
                
                
                if ( dbHelper != null ) {
                    if ( location != null  ) {
                        // if in fast mode, only add new-for-run stuff to the db queue
                        if ( fastMode && ! added ) {
                            info( "in fast mode, not adding seen-this-run: " + network.getBssid() );
                        }
                        else {
                            // loop for stress-testing
                            // for ( int i = 0; i < 10; i++ ) {
                            dbHelper.addObservation( network, location, added );
                            // }
                        }
                    } else {
                        // no location
                        dbHelper.pendingObservation( network, added );
                    }
                }
              }
            }

            // check if there are more "New" nets
            final long newNetCount = dbHelper.getNewNetworkCount();
            final long newNetDiff = newNetCount - prevNewNetCount;
            prevNewNetCount = newNetCount;
            
            
            if ( ! isMuted() ) {
              final boolean playRun = prefs.getBoolean( PREF_FOUND_SOUND, true );
              final boolean playNew = prefs.getBoolean( PREF_FOUND_NEW_SOUND, true );
              if ( newNetDiff > 0 && playNew ) {
                if ( soundNewPop != null && ! soundNewPop.isPlaying() ) {
                  // play sound on something new
                  soundNewPop.start();
                }
                else {
                  info( "soundNewPop is playing or null" );
                }
              }
              else if ( somethingAdded && playRun ) {
                if ( soundPop != null && ! soundPop.isPlaying() ) {
                  // play sound on something new
                  soundPop.start();
                }
                else {
                  info( "soundPop is playing or null" );
                }
              }
            }
            
            // sort by signal strength
            listAdapter.sort( signalCompare );

            // update stat
            final TextView tv = (TextView) findViewById( R.id.stats );
            final StringBuilder builder = new StringBuilder( 40 );
            builder.append( "Run: " ).append( runNetworks.size() );
            builder.append( " New: " ).append( newNetCount );
            builder.append( " DB: " ).append( dbHelper.getNetworkCount() );
            builder.append( " Locs: " ).append( dbHelper.getLocationCount() );
            savedStats = builder.toString();
            tv.setText( savedStats );
            
            // set the statics for the map
            WigleAndroid.lameStatic.savedStats = savedStats;
            WigleAndroid.lameStatic.runNets = runNetworks.size();
            WigleAndroid.lameStatic.newNets = newNetCount;
            WigleAndroid.lameStatic.currNets = resultSize;
            WigleAndroid.lameStatic.preQueueSize = preQueueSize;
            
            if ( newForRun > 0 ) {
              if ( location == null ) {
                // save for later
                pendingCount += newForRun;
              }
              else {
                final GeoPoint geoPoint = new GeoPoint( location );
                TrailStat trailStat = lameStatic.trail.get( geoPoint );
                if ( trailStat == null ) {
                  trailStat = new TrailStat();
                  lameStatic.trail.put( geoPoint, trailStat );
                }
                trailStat.newForRun += newForRun;
                trailStat.newForDB += newNetDiff;
                
                // add any pendings
                // don't go crazy
                if ( pendingCount > 25 ) {
                  pendingCount = 25;
                }
                trailStat.newForRun += pendingCount;
                pendingCount = 0;
              }
            }
            
            // info( savedStats );
            
            // notify
            listAdapter.notifyDataSetChanged();
            
            final long now = System.currentTimeMillis();
            if ( scanRequestTime <= 0 ) {
              // wasn't set, set to now
              scanRequestTime = now;
            }
            status( resultSize + " scan " + (now - scanRequestTime) + "ms, process " 
                + (now - start) + "ms. DB Q: " + preQueueSize );
            // we've shown it, reset it to the nonstop time above, or min_value if nonstop wasn't set.
            scanRequestTime = nonstopScanRequestTime;
            
            // do distance calcs
            if ( location != null && GPS_PROVIDER.equals( location.getProvider() )
                && location.getAccuracy() <= MIN_DISTANCE_ACCURACY ) {
              if ( prevGpsLocation != null ) {
                float dist = location.distanceTo( prevGpsLocation );
                // info( "dist: " + dist );
                if ( dist > 0f ) {
                  final Editor edit = prefs.edit();
                  edit.putFloat( PREF_DISTANCE_RUN,
                      dist + prefs.getFloat( PREF_DISTANCE_RUN, 0f ) );
                  edit.putFloat( PREF_DISTANCE_TOTAL,
                      dist + prefs.getFloat( PREF_DISTANCE_TOTAL, 0f ) );
                  edit.commit();
                }
              }
              
              // set for next time
              prevGpsLocation = location;
            }
            
            final long speechPeriod = prefs.getLong( PREF_SPEECH_PERIOD, DEFAULT_SPEECH_PERIOD );
            if ( speechPeriod != 0 && now - previousTalkTime > speechPeriod * 1000L ) {
              String gps = "";
              if ( location == null ) {
                gps = ", no gps fix";
              }
              String queue = "";
              if ( preQueueSize > 0 ) {
                queue = ", queue " + preQueueSize;
              }
              float dist = prefs.getFloat( PREF_DISTANCE_RUN, 0f );
              String miles = ". From " + numberFormat1.format( dist / 1609.344f ) + " miles";
              speak("run " + runNetworks.size() + ", new " + newNetCount + gps + queue + miles );
              previousTalkTime = now;
            }
          }
        };
      
      // register
      final IntentFilter intentFilter = new IntentFilter();
      intentFilter.addAction( WifiManager.SCAN_RESULTS_AVAILABLE_ACTION );
      this.registerReceiver( wifiReceiver, intentFilter );
      
      if ( wifiLock == null ) {
        // lock the radio on
        wifiLock = wifiManager.createWifiLock( WifiManager.WIFI_MODE_SCAN_ONLY, WIFI_LOCK_NAME );
        wifiLock.acquire();
      }
      
      // might not be null on a nonconfig retain
      if ( wifiTimer == null ) {
        wifiTimer = new Handler();
        final Runnable mUpdateTimeTask = new Runnable() {
          public void run() {              
              // make sure the app isn't trying to finish
              if ( ! finishing.get() ) {
                // info( "timer start scan" );
                wifiManager.startScan();
                if ( scanRequestTime <= 0 ) {
                  scanRequestTime = System.currentTimeMillis();
                }
                long period = prefs.getLong( PREF_SCAN_PERIOD, 1000L);
                // check if set to "continuous"
                if ( period == 0L ) {
                  // set to default here, as a scan will also be requested on the scan result listener
                  period = 1000L;
                }
                // info("wifitimer: " + period );
                wifiTimer.postDelayed( this, period );
              }
              else {
                info( "finishing timer" );
              }
          }
        };
        wifiTimer.removeCallbacks( mUpdateTimeTask );
        wifiTimer.postDelayed( mUpdateTimeTask, 100 );

        // starts scan, sends event when done
        final boolean scanOK = wifiManager.startScan();
        if ( scanRequestTime <= 0 ) {
          scanRequestTime = System.currentTimeMillis();
        }
        info( "startup finished. wifi scanOK: " + scanOK );
      }
    }
    
    private void speak( final String string ) {
      if ( ! isMuted() && tts != null ) {
        tts.speak( string );
      }
    }
    
    private void setupLocation() {
      // set on UI if we already have one
      updateLocationData( (Location) null );
      
      final LocationManager locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
      
      if ( ! locationManager.isProviderEnabled( GPS_PROVIDER ) ) {
        Toast.makeText( this, "Please turn on GPS", Toast.LENGTH_SHORT ).show();
        final Intent myIntent = new Intent( Settings.ACTION_LOCATION_SOURCE_SETTINGS );
        startActivity(myIntent);
      }
      // emulator crashes if you ask this
      if ( ! inEmulator && ! locationManager.isProviderEnabled( NETWORK_PROVIDER ) ) {
        Toast.makeText( this, "For best results, set \"Use wireless networks\" in \"Location & security\"", 
            Toast.LENGTH_LONG ).show();
      }
      
      gpsStatusListener = new Listener(){
        public void onGpsStatusChanged( final int event ) {
          updateLocationData( (Location) null );
        } 
      };
      locationManager.addGpsStatusListener( gpsStatusListener );
      
      final List<String> providers = locationManager.getAllProviders();
      locationListener = new LocationListener(){
          public void onLocationChanged( final Location newLocation ) {
            updateLocationData( newLocation );
          }
          public void onProviderDisabled( final String provider ) {}
          public void onProviderEnabled( final String provider ) {}
          public void onStatusChanged( final String provider, final int status, final Bundle extras ) {}
        };
        
      for ( String provider : providers ) {
        info( "provider: " + provider );
        locationManager.requestLocationUpdates( provider, 1000L, 0, locationListener );
      }
    }
    
    /** newLocation can be null */
    private void updateLocationData( final Location newLocation ) {
      final LocationManager locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
      // see if we have new data
      gpsStatus = locationManager.getGpsStatus( gpsStatus );
      final int satCount = getSatCount();
      
      boolean newOK = newLocation != null;
      final boolean locOK = locationOK( location, satCount );
      final long now = System.currentTimeMillis();
      
      if ( newOK ) {
        if ( NETWORK_PROVIDER.equals( newLocation.getProvider() ) ) {
          // save for later, in case we lose gps
          networkLocation = newLocation;
          lastNetworkLocationTime = now;
        }
        else {
          lastLocationTime = now;
          // make sure there's enough sats on this new gps location
          newOK = locationOK( newLocation, satCount );
        }
      }
      
      if ( inEmulator && newLocation != null ) {
        newOK = true; 
      }
      
      final boolean netLocOK = locationOK( networkLocation, satCount );
      
      boolean wasProviderChange = false;
      if ( ! locOK ) {
        if ( newOK ) {
          wasProviderChange = true;
          if ( location != null && ! location.getProvider().equals( newLocation.getProvider() ) ) {
            wasProviderChange = false;
          }
          
          location = newLocation;
        }
        else if ( netLocOK ) {
          location = networkLocation;
          wasProviderChange = true;
        }
        else if ( location != null ) {
          // transition to null
          info( "nulling location: " + location );
          location = null;
          wasProviderChange = true;
        }
      }
      else if ( newOK && GPS_PROVIDER.equals( newLocation.getProvider() ) ) {
        if ( NETWORK_PROVIDER.equals( location.getProvider() ) ) {
          // this is an upgrade from network to gps
          wasProviderChange = true;
        }
        location = newLocation;
      }
      else if ( newOK && NETWORK_PROVIDER.equals( newLocation.getProvider() ) ) {
        if ( NETWORK_PROVIDER.equals( location.getProvider() ) ) {
          // just a new network provided location over an old one
          location = newLocation;
        }
      }
      
      // for maps. so lame!
      lameStatic.location = location;
      
      if ( wasProviderChange ) {
        info( "wasProviderChange: run: " + this.runNetworks.size() + " satCount: " + satCount 
          + " newOK: " + newOK + " locOK: " + locOK + " netLocOK: " + netLocOK
          + " wasProviderChange: " + wasProviderChange
          + (newOK ? " newProvider: " + newLocation.getProvider() : "")
          + (locOK ? " locProvider: " + location.getProvider() : "") 
          + " newLocation: " + newLocation );

        final String announce = location == null ? "Lost Location" 
            : "Now have location from \"" + location.getProvider() + "\"";
        Toast.makeText( this, announce, Toast.LENGTH_SHORT ).show();
        final SharedPreferences prefs = this.getSharedPreferences( SHARED_PREFS, 0 );
        final boolean speechGPS = prefs.getBoolean( PREF_SPEECH_GPS, true );
        if ( speechGPS ) {
          // no quotes or the voice pauses
          final String speakAnnounce = location == null ? "Lost Location" 
            : "Now have location from " + location.getProvider() + ".";
          speak( speakAnnounce );
        }

        if ( location == null ) {
            if ( prevGpsLocation != null ) {
              dbHelper.lastLocation( prevGpsLocation );
              info("set last location for lerping");
            }
        } else {
          int count = dbHelper.recoverLocations( location );
          info( "recovered "+count+" location"+(count==1?"":"s")+" with the power of lerp");
        }
      }
      
      // update the UI
      setLocationUI();
    }
    
    private boolean locationOK( final Location location, final int satCount ) {
      boolean retval = false;
      final long now = System.currentTimeMillis();
      
      if ( location == null ) {
        // bad!
      }
      else if ( GPS_PROVIDER.equals( location.getProvider() ) ) {
        if ( satCount < 3 ) {
          if ( satCountLowTime == null ) {
            satCountLowTime = now;
          }
        }
        else {
          // plenty of sats
          satCountLowTime = null;
        }
        boolean gpsLost = satCountLowTime != null && (now - satCountLowTime) > GPS_TIMEOUT;
        gpsLost |= now - lastLocationTime > GPS_TIMEOUT;
        retval = ! gpsLost;
      }
      else if ( NETWORK_PROVIDER.equals( location.getProvider() ) ) {
        boolean gpsLost = now - lastNetworkLocationTime > NET_LOC_TIMEOUT;
        retval = ! gpsLost;
      }
      
      return retval;
    }
    
    private int getSatCount() {
      int satCount = 0;
      if ( gpsStatus != null ) {
        for ( GpsSatellite sat : gpsStatus.getSatellites() ) {
          if ( sat.usedInFix() ) {
            satCount++;
          }
        }
      }
      return satCount;
    }
    
    private void setLocationUI() {
      if ( gpsStatus != null ) {
        final int satCount = getSatCount();
        final TextView tv = (TextView) this.findViewById( R.id.LocationTextView06 );
        tv.setText( "Sats: " + satCount );
      }
      
      TextView tv = (TextView) this.findViewById( R.id.LocationTextView01 );
      tv.setText( "Lat: " + (location == null ? "  (Waiting for GPS sync..)" 
          : numberFormat8.format( location.getLatitude() ) ) );
      
      tv = (TextView) this.findViewById( R.id.LocationTextView02 );
      tv.setText( "Lon: " + (location == null ? "" : numberFormat8.format( location.getLongitude() ) ) );
      
      tv = (TextView) this.findViewById( R.id.LocationTextView03 );
      tv.setText( "Speed: " + (location == null ? "" : numberFormat1.format( location.getSpeed() * 2.23693629f ) + "mph" ) );
      
      tv = (TextView) this.findViewById( R.id.LocationTextView04 );
      tv.setText( location == null ? "" : ("+/- " + numberFormat1.format( location.getAccuracy() ) + "m") );
      
      tv = (TextView) this.findViewById( R.id.LocationTextView05 );
      tv.setText( location == null ? "" : ("Alt: " + numberFormat1.format( location.getAltitude() ) + "m") );
    }
    
    private void setupUploadButton() {
      final Button button = (Button) findViewById( R.id.upload_button );
      button.setOnClickListener( new OnClickListener() {
          public void onClick( final View view ) {
            uploadFile( WigleAndroid.this, dbHelper );
          }
        });
    }
    
    private void setupService() {
      final Intent serviceIntent = new Intent( this, WigleService.class );
      
      // could be set by nonconfig retain
      if ( serviceConnection == null ) {
        final ComponentName compName = startService( serviceIntent );
        if ( compName == null ) {
          WigleAndroid.error( "startService() failed!" );
        }
        else {
          WigleAndroid.info( "service started ok: " + compName );
        }
        
        serviceConnection = new ServiceConnection(){
          public void onServiceConnected( final ComponentName name, final IBinder iBinder ) {
            WigleAndroid.info( name + " service connected" ); 
          }
          public void onServiceDisconnected( final ComponentName name ) {
            WigleAndroid.info( name + " service disconnected" );
          }
        };  
      }
      
      int flags = 0;
      this.bindService( serviceIntent, serviceConnection, flags );
    }
    
    private void setupSound() {
      // could have been retained
      if ( soundPop == null ) {
        soundPop = createMediaPlayer( R.raw.pop );
      }
      if ( soundNewPop == null ) {
        soundNewPop = createMediaPlayer( R.raw.newpop );
      }
      audioManager = (AudioManager) this.getSystemService(Context.AUDIO_SERVICE);
      // make volume change "media"
      this.setVolumeControlStream( AudioManager.STREAM_MUSIC );  
      
      if ( TTS.hasTTS() ) {
        tts = new TTS( this );        
      }
      
      TelephonyManager tele = (TelephonyManager) getSystemService(TELEPHONY_SERVICE);
      if ( tele != null ) {
        tele.listen(new PhoneStateListener() {
          @Override
          public void onCallStateChanged( int state, String incomingNumber ) {
            switch ( state ) {
              case TelephonyManager.CALL_STATE_IDLE:
                isPhoneActive = false;
                info( "setting phone inactive. state: " + state );
                break;
              case TelephonyManager.CALL_STATE_RINGING:
              case TelephonyManager.CALL_STATE_OFFHOOK:
                isPhoneActive = true;
                info( "setting phone active. state: " + state );
                break;
              default:
                info( "unhandled call state: " + state );
            }
          }
        }, PhoneStateListener.LISTEN_CALL_STATE );
      }
      
      setupMuteButton();
    }
     
    private void setupMuteButton() {
      final Button mute = (Button) this.findViewById(R.id.mute);
      final SharedPreferences prefs = this.getSharedPreferences(SHARED_PREFS, 0);
      final boolean muted = prefs.getBoolean(PREF_MUTED, false);
      if ( muted ) {
        mute.setText("Play");
      }
      mute.setOnClickListener(new OnClickListener(){
        public void onClick( final View buttonView ) {
          boolean muted = prefs.getBoolean(PREF_MUTED, false);
          muted = ! muted;
          Editor editor = prefs.edit();
          editor.putBoolean( PREF_MUTED, muted );
          editor.commit();
          
          if ( muted ) {
            mute.setText("Play");
          }
          else {
            mute.setText("Mute");
          }
        }
      });
    }

    /** 
     * create a mediaplayer for a given raw resource id.
     * @param soundId the R.raw. id for a given sound
     * @return the mediaplayer for soundId or null if it could not be created.
     */
    private MediaPlayer createMediaPlayer( final int soundId ) {
      final MediaPlayer sound = createMp( this, soundId );
      if ( sound == null ) {
        info( "sound null from media player" );
        return null;
      }
      // try to figure out why sounds stops after a while
      sound.setOnErrorListener( new OnErrorListener() {
        public boolean onError( final MediaPlayer mp, final int what, final int extra ) {
          String whatString = null;
          switch ( what ) {
            case MediaPlayer.MEDIA_ERROR_UNKNOWN:
              whatString = "error unknown";
              break;
            case MediaPlayer.MEDIA_ERROR_SERVER_DIED:
              whatString = "server died";
              break;
            default:
              whatString = "not defined";
          }
          info( "media player error \"" + whatString + "\" what: " + what
            + " extra: " + extra + " mp: " + mp );
          return false;
        }
      } );

      return sound;
    }

    /** 
     * externalize the file from a given resource id (if it dosen't already exist), write to our dir if there is one.
     * @param context the context to use
     * @param resid the resource id
     * @param name the file name to write out
     * @return the uri of a file containing resid's resource
     */
    private static Uri resToFile( final Context context, final int resid, final String name ) throws IOException {
        // throw it in our bag of fun.
        String openString = name;
        final boolean hasSD = hasSD();
        if ( hasSD ) {
            final String filepath = Environment.getExternalStorageDirectory().getCanonicalPath() + "/wiglewifi/";
            final File path = new File( filepath );
            path.mkdirs();
            openString = filepath + name;
        }

        final File f = new File( openString );
      
        // see if it exists already
        if ( ! f.exists() ) { 
            info("causing "+f.getCanonicalPath()+" to be made");
            // make it happen:
            f.createNewFile();
            
            InputStream is = null;
            FileOutputStream fos = null;
            try {
                is = context.getResources().openRawResource( resid );
                if ( hasSD ) {
                    fos = new FileOutputStream( f );
                } else { 
                    // XXX: should this be using openString instead? baroo?
                    fos = context.openFileOutput( name, Context.MODE_WORLD_READABLE );
                }

                final byte[] buff = new byte[ 1024 ];
                int rv = -1;
                while( ( rv = is.read( buff ) ) > -1 ) {
                    fos.write( buff, 0, rv );
                }
            } finally {
                if ( fos != null ) {
                    fos.close();
                }
                if ( is != null ) {
                    is.close();
                }
            }
        }
        return Uri.fromFile( f );
    }

    /** 
     * create a media player (trying several paths if available)
     * @param context the context to use
     * @param resid the resource to use
     * @return the media player for resid (or null if it wasn't creatable)
     */
    private static MediaPlayer createMp( final Context context, final int resid ) {
        try {
            MediaPlayer mp = MediaPlayer.create( context, resid );
            // this can fail for many reasons, but android 1.6 on archos5 definitely hates creating from resource
            if ( mp == null ) {
                Uri sounduri;
                // XXX: find a better way? baroo.
                if ( resid == R.raw.pop ) {
                    sounduri = resToFile( context, resid, "pop.wav" );
                } else if ( resid == R.raw.newpop ) {
                    sounduri = resToFile( context, resid, "newpop.wav" );
                } else {
                    info( "unknown raw sound id:"+resid );
                    return null;
                }
                mp = MediaPlayer.create( context, sounduri );
                // may still end up null
            }

            return mp;
        } catch (IOException ex) {
            error("ioe create failed:", ex);
            // fall through
        } catch (IllegalArgumentException ex) {
            error("iae create failed:", ex);
           // fall through
        } catch (SecurityException ex) {
            error("se create failed:", ex);
            // fall through
        } catch ( Resources.NotFoundException ex ) {
            error("rnfe create failed("+resid+"):", ex );
        }
        return null;
    }
   
    
    @SuppressWarnings("unused")
    private boolean isRingerOn() {
      boolean retval = false;
      if ( audioManager != null ) {
        retval = audioManager.getRingerMode() == AudioManager.RINGER_MODE_NORMAL;
      }
      return retval;
    }
    
    private boolean isMuted() {
      if ( isPhoneActive ) {
        // always be quiet when the phone is active
        return true;
      }
      boolean retval = this.getSharedPreferences(SHARED_PREFS, 0).getBoolean(PREF_MUTED, false);
      // info( "ismuted: " + retval );
      return retval;
    }
    
    private static void uploadFile( final Context context, final DatabaseHelper dbHelper ){
      info( "upload file" );
      final FileUploaderTask task = new FileUploaderTask( context, dbHelper );
      task.start();
    }
    
    private void status( String status ) {
      // info( status );
      final TextView tv = (TextView) findViewById( R.id.status );
      tv.setText( status );
    }
    
    public static void sleep( final long sleep ) {
      try {
        Thread.sleep( sleep );
      }
      catch ( final InterruptedException ex ) {
        // no worries
      }
    }
    public static void info( final String value ) {
      Log.i( LOG_TAG, Thread.currentThread().getName() + "] " + value );
    }
    public static void error( final String value ) {
      Log.e( LOG_TAG, Thread.currentThread().getName() + "] " + value );
    }

    public static void error( final String value, final Throwable t ) {
        Log.e( LOG_TAG, Thread.currentThread().getName() + "] " + value, t );
    }

    /**
     * get the per-thread network LRU cache
     * @return per-thread network cache
     */
    public static CacheMap<String,Network> getNetworkCache() {
      return networkCache.get();
    }
    
    public static void writeError( final Thread thread, final Throwable throwable ) {
      try {
        final String error = "Thread: " + thread + " throwable: " + throwable;
        error( error );
        if ( hasSD() ) {
          File file = new File( Environment.getExternalStorageDirectory().getCanonicalPath() + "/wiglewifi/" );
          file.mkdirs();
          file = new File(Environment.getExternalStorageDirectory().getCanonicalPath() 
              + "/wiglewifi/errorstack_" + System.currentTimeMillis() + ".txt" );
          if ( ! file.exists() ) {
            file.createNewFile();
          }
          final FileOutputStream fos = new FileOutputStream( file );
          fos.write( error.getBytes( ENCODING ) );
          throwable.printStackTrace( new PrintStream( fos ) );
          fos.close();
        }
      }
      catch ( final Exception ex ) {
        error( "error logging error: " + ex );
        ex.printStackTrace();
      }
    }
    
    public static boolean hasSD() {
      File sdCard = null;
      try {
        sdCard = new File( Environment.getExternalStorageDirectory().getCanonicalPath() + "/" );
      }
      catch ( final IOException ex ) {
        // ohwell
      }
      return sdCard != null && sdCard.exists() && sdCard.isDirectory() && sdCard.canRead() && sdCard.canWrite();
    }
    
    private void setupMaxidDebug() {
      final SharedPreferences prefs = WigleAndroid.this.getSharedPreferences( SHARED_PREFS, 0 );
      final long maxid = prefs.getLong( PREF_DB_MARKER, -1L );
      if ( maxid == -1L ) {
        // load up the local value
        dbHelper.getLocationCountFromDB();
        final long loccount = dbHelper.getLocationCount();
        if ( loccount > 0 ) {
          // there is no preference set, yet there are locations, this is likely
          // a developer testing a new install on an old db, so set the pref.
          info( "setting db marker to: " + loccount );
          final Editor edit = prefs.edit();
          edit.putLong( PREF_DB_MARKER, loccount );
          edit.commit();
        }
      }
    }
}
