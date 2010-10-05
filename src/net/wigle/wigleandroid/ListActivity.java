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
import java.lang.Thread.UncaughtExceptionHandler;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

import net.wigle.wigleandroid.listener.BatteryLevelReceiver;
import net.wigle.wigleandroid.listener.GPSListener;
import net.wigle.wigleandroid.listener.PhoneState;
import net.wigle.wigleandroid.listener.WifiReceiver;

import org.andnav.osm.util.GeoPoint;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.location.Location;
import android.location.LocationManager;
import android.location.LocationProvider;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnErrorListener;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiManager.WifiLock;
import android.os.Build;
import android.os.Bundle;
import android.os.Debug;
import android.os.Environment;
import android.os.IBinder;
import android.provider.Settings;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

public final class ListActivity extends Activity implements FileUploaderListener {
    // *** state that is retained ***
    private static class State {
      private DatabaseHelper dbHelper;
      private ServiceConnection serviceConnection;
      private AtomicBoolean finishing;
      private AtomicBoolean uploading;
      private MediaPlayer soundPop;
      private MediaPlayer soundNewPop;
      private WifiLock wifiLock;
      private GPSListener gpsListener;
      private WifiReceiver wifiReceiver;
      private NumberFormat numberFormat1;
      private NumberFormat numberFormat8;
      private TTS tts;
      private boolean inEmulator;
      private BatteryLevelReceiver batteryLevelReceiver;
      private PhoneState phoneState;
    }
    private State state;
    // *** end of state that is retained ***
    
    private NetworkListAdapter listAdapter;
    
    public static final String FILE_POST_URL = "https://wigle.net/gps/gps/main/confirmfile/";
    private static final String LOG_TAG = "wigle";
    private static final int MENU_SETTINGS = 10;
    private static final int MENU_EXIT = 11;
    private static final int MENU_MAP = 12;
    private static final int MENU_DASH = 13;
    public static final String ENCODING = "ISO-8859-1";
    public static final float MIN_DISTANCE_ACCURACY = 32f;
    static final String ERROR_STACK_FILENAME = "errorstack";
    static final String ERROR_REPORT_DO_EMAIL = "doEmail";
    
    // preferences
    public static final String SHARED_PREFS = "WiglePrefs";
    public static final String PREF_USERNAME = "username";
    public static final String PREF_PASSWORD = "password";
    public static final String PREF_SHOW_CURRENT = "showCurrent";
    public static final String PREF_BE_ANONYMOUS = "beAnonymous";
    public static final String PREF_DB_MARKER = "dbMarker";
    public static final String PREF_MAX_DB = "maxDbMarker";
    public static final String PREF_SCAN_PERIOD = "scanPeriod";
    public static final String PREF_SCAN_PERIOD_FAST = "scanPeriodFast";
    public static final String PREF_FOUND_SOUND = "foundSound";
    public static final String PREF_FOUND_NEW_SOUND = "foundNewSound";
    public static final String PREF_SPEECH_PERIOD = "speechPeriod";
    public static final String PREF_SPEECH_GPS = "speechGPS";
    public static final String PREF_MUTED = "muted";
    public static final String PREF_WIFI_WAS_OFF = "wifiWasOff";
    public static final String PREF_DISTANCE_RUN = "distRun";
    public static final String PREF_DISTANCE_TOTAL = "distTotal";
    public static final String PREF_DISTANCE_PREV_RUN = "distPrevRun";
    public static final String PREF_MAP_ONLY_NEWDB = "mapOnlyNewDB";
    public static final String PREF_PREV_LAT = "prevLat";
    public static final String PREF_PREV_LON = "prevLon";
    // what to speak on announcements
    public static final String PREF_SPEAK_RUN = "speakRun";
    public static final String PREF_SPEAK_NEW = "speakNew";
    public static final String PREF_SPEAK_QUEUE = "speakQueue";
    public static final String PREF_SPEAK_MILES = "speakMiles";
    public static final String PREF_SPEAK_TIME = "speakTime";
    public static final String PREF_SPEAK_BATTERY = "speakBattery";
    
    public static final long DEFAULT_SPEECH_PERIOD = 60L;
    public static final long LOCATION_UPDATE_INTERVAL = 1000L;
    public static final long SCAN_DEFAULT = 2000L;
    
    static final String ANONYMOUS = "anonymous";
    private static final String WIFI_LOCK_NAME = "wigleWifiLock";
    //static final String THREAD_DEATH_MESSAGE = "threadDeathMessage";
    static final boolean DEBUG = false;
    
    /** cross-activity communication */
    public static class TrailStat {
      public int newForRun = 0;
      public int newForDB = 0;
    }
    public static class LameStatic {
      public Location location; 
      public ConcurrentLinkedHashMap<GeoPoint,TrailStat> trail = 
        new ConcurrentLinkedHashMap<GeoPoint,TrailStat>( 512 );
      public int runNets;
      public long newNets;
      public int currNets;
      public int preQueueSize;
      public long dbNets;
      public long dbLocs;
      public DatabaseHelper dbHelper;
    }
    public static final LameStatic lameStatic = new LameStatic();
    
    // cache
    private static final ThreadLocal<CacheMap<String,Network>> networkCache = new ThreadLocal<CacheMap<String,Network>>() {
      protected CacheMap<String,Network> initialValue() {
          return new CacheMap<String,Network>( 16, 64 );
      }
    };
    
    /** Called when the activity is first created. */
    @Override
    public void onCreate( final Bundle savedInstanceState ) {
        super.onCreate( savedInstanceState );
        setContentView( R.layout.list );
        
        if ( DEBUG ) {
          Debug.startMethodTracing("wigle");
        }
        
        // do some of our own error handling, write a file with the stack
        final UncaughtExceptionHandler origHandler = Thread.getDefaultUncaughtExceptionHandler();
        if ( ! (origHandler instanceof WigleUncaughtExceptionHandler) ) {
          Thread.setDefaultUncaughtExceptionHandler( 
              new WigleUncaughtExceptionHandler( this.getApplicationContext(), origHandler ) ); 
        }
        
        // test the error reporting
        // if( true ){ throw new RuntimeException( "weee" ); }
        
        final Object stored = getLastNonConfigurationInstance();
        if ( stored != null && stored instanceof State ) {
          // pry an orientation change, which calls destroy, but we set this in onRetainNonConfigurationInstance
          state = (State) stored;
          
          // tell those that need it that we have a new context
          state.gpsListener.setListActivity( this );
          state.wifiReceiver.setListActivity( this );
          setNetCountUI( this, state.wifiReceiver.getRunNetworkCount(), state.dbHelper.getNewNetworkCount(), 
              ListActivity.lameStatic.dbNets );
        }
        else {
          state = new State();
          state.finishing = new AtomicBoolean( false );
          state.uploading = new AtomicBoolean( false );
          
          // new run, reset
          final SharedPreferences prefs = this.getSharedPreferences( SHARED_PREFS, 0 );
          final float prevRun = prefs.getFloat( PREF_DISTANCE_RUN, 0f );
          Editor edit = prefs.edit();
          edit.putFloat( PREF_DISTANCE_RUN, 0f );
          edit.putFloat( PREF_DISTANCE_PREV_RUN, prevRun );
          edit.commit();
        }
        
        final String id = Settings.Secure.getString( getContentResolver(), Settings.Secure.ANDROID_ID );

        // DO NOT turn these into |=, they will cause older dalvik verifiers to freak out
        state.inEmulator = id == null;
        state.inEmulator =  state.inEmulator || "sdk".equals( android.os.Build.PRODUCT );
        state.inEmulator = state.inEmulator || "google_sdk".equals( android.os.Build.PRODUCT );

        info( "id: '" + id + "' inEmulator: " + state.inEmulator + " product: " + android.os.Build.PRODUCT );
        info( "android release: '" + Build.VERSION.RELEASE + "' debug: " + DEBUG );
        
        if ( state.numberFormat1 == null ) {
          state.numberFormat1 = NumberFormat.getNumberInstance( Locale.US );
          if ( state.numberFormat1 instanceof DecimalFormat ) {
            ((DecimalFormat) state.numberFormat1).setMaximumFractionDigits( 1 );
          }
        }
        
        if ( state.numberFormat8 == null ) {
          state.numberFormat8 = NumberFormat.getNumberInstance( Locale.US );
          if ( state.numberFormat8 instanceof DecimalFormat ) {
            ((DecimalFormat) state.numberFormat8).setMaximumFractionDigits( 8 );
          }
        }
        
        info( "setupService" );
        setupService();
        info( "setupDatabase" );
        setupDatabase();
        info( "setupMaxidDebug" );
        setupMaxidDebug();
        info( "setupUploadButton" );
        setupUploadButton();
        info( "setupList" );
        setupList();
        info( "setupSound" );
        setupSound();
        info( "setupWifi" );
        setupWifi();
        info( "setupLocation" );
        setupLocation();
        info( "setupBattery" );
        setupBattery();
        info( "setup complete" );
    }
    
    public static void setNetCountUI( final Activity activity, final int runNetworks, 
        final long prevNewNetCount, final long dbNets ) {
      
      TextView tv = (TextView) activity.findViewById( R.id.stats_run );
      tv.setText( "Run: " + runNetworks );
      tv = (TextView) activity.findViewById( R.id.stats_new );
      tv.setText( "New: " + prevNewNetCount );
      tv = (TextView) activity.findViewById( R.id.stats_dbnets );
      tv.setText( "DB: " + dbNets );
    }
    
    public boolean inEmulator() {
      return state.inEmulator;
    }
    
    public DatabaseHelper getDBHelper() {
      return state.dbHelper;
    }
    
    public BatteryLevelReceiver getBatteryLevelReceiver() {
      return state.batteryLevelReceiver;
    }
    
    public GPSListener getGPSListener() {
      return state.gpsListener;
    }
    
    public boolean isFinishing() {
      return state.finishing.get();
    }
    
    public boolean isUploading() {
      return state.uploading.get();
    }
    
    public void playNewNetSound() {
      try {
        if ( state.soundNewPop != null && ! state.soundNewPop.isPlaying() ) {
          // play sound on something new
          state.soundNewPop.start();
        }
        else {
          ListActivity.info( "soundNewPop is playing or null" );
        }
      }
      catch ( IllegalStateException ex ) {
        // ohwell, likely already playing
        info( "exception trying to play sound: " + ex );
      }
    }
    
    public void playRunNetSound() {
      try {
        if ( state.soundPop != null && ! state.soundPop.isPlaying() ) {
          // play sound on something new
          state.soundPop.start();
        }  
        else {
          ListActivity.info( "soundPop is playing or null" );
        }
      }
      catch ( IllegalStateException ex ) {
        // ohwell, likely already playing
        info( "exception trying to play sound: " + ex );
      }
    }
    
    @Override
    public Object onRetainNonConfigurationInstance() {
      info( "onRetainNonConfigurationInstance" );
      // return the whole state class to copy data from
      return state;
    }
    
    @Override
    public void onPause() {
      info( "LIST: paused. networks: " + state.wifiReceiver.getRunNetworkCount() );
      super.onPause();
    }
    
    @Override
    public void onResume() {
      info( "LIST: resumed. networks: " + state.wifiReceiver.getRunNetworkCount() );
      super.onResume();
    }
    
    @Override
    public void onStart() {
      info( "LIST: start. networks: " + state.wifiReceiver.getRunNetworkCount() );
      super.onStart();
    }
    
    @Override
    public void onStop() {
      info( "LIST: stop. networks: " + state.wifiReceiver.getRunNetworkCount() );
      super.onStop();
    }

    @Override
    public void onRestart() {
      info( "LIST: restart. networks: " + state.wifiReceiver.getRunNetworkCount() );
      super.onRestart();
    }
    
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
      if (keyCode == KeyEvent.KEYCODE_BACK) {
        info( "onKeyDown: treating back like home, not quitting app" );
        moveTaskToBack(true);
        if ( getParent() != null ) {
          getParent().moveTaskToBack( true );
        }
        return true;
      }
      return super.onKeyDown(keyCode, event);
    }
    
    @Override
    public void onDestroy() {
      info( "LIST: destroy. networks: " + state.wifiReceiver.getRunNetworkCount() );
      super.onDestroy();
    }
    
    @Override
    public void finish() {
      info( "LIST: finish. networks: " + state.wifiReceiver.getRunNetworkCount() );
      
      final boolean wasFinishing = state.finishing.getAndSet( true );
      if ( wasFinishing ) {
        info( "LIST: finish called twice!" );
      }

      final SharedPreferences prefs = this.getSharedPreferences( SHARED_PREFS, 0 );
      if ( prefs.getLong( PREF_SPEECH_PERIOD, 0 ) > 0 ) {
        speak( "done." );
      }
      
      // save our location for later runs
      state.gpsListener.saveLocation();
      
      // close the db. not in destroy, because it'll still write after that.
      state.dbHelper.close();
      
      final LocationManager locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
      if ( state.gpsListener != null ) {
        locationManager.removeGpsStatusListener( state.gpsListener );
        locationManager.removeUpdates( state.gpsListener );
      }
      
      try {
        this.unregisterReceiver( state.wifiReceiver );
      }
      catch ( final IllegalArgumentException ex ) {
        info( "wifiReceiver not registered: " + ex );
      }

      // stop the service, so when we die it's both stopped and unbound and will die
      final Intent serviceIntent = new Intent( this, WigleService.class );
      this.stopService( serviceIntent );
      try {
        this.unbindService( state.serviceConnection );
      }
      catch ( final IllegalArgumentException ex ) {
        info( "serviceConnection not registered: " + ex, ex );
      }    
      
      // release the lock before turning wifi off
      if ( state.wifiLock != null && state.wifiLock.isHeld() ) {
        state.wifiLock.release();
      }
      
      final boolean wifiWasOff = prefs.getBoolean( PREF_WIFI_WAS_OFF, false );
      // don't call on emulator, it crashes it
      if ( wifiWasOff && ! state.inEmulator ) {
        // tell user, cuz this takes a little while
        Toast.makeText( this, "Turning WiFi back off", Toast.LENGTH_SHORT ).show();
        
        // well turn it of now that we're done
        final WifiManager wifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);
        info( "turning back off wifi" );
        wifiManager.setWifiEnabled( false );
      }
      
      if ( state.tts != null ) {
        if ( ! isMuted() ) {
          // give time for the above "done" to be said
          sleep( 250 );
        }
        state.tts.shutdown();
      }
      
      
      if ( DEBUG ) {
        Debug.stopMethodTracing();
      }

      // clean up.
      if ( state.soundPop != null ) {
        state.soundPop.release();
      }
      if ( state.soundNewPop != null ) {
        state.soundNewPop.release();
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
            MainActivity.switchTab( this, MainActivity.TAB_SETTINGS );
            return true;
          }
          case MENU_MAP: {
            info("start map activity");
            MainActivity.switchTab( this, MainActivity.TAB_MAP );
            return true;
          }
          case MENU_DASH: {
            info("start dashboard activity");
            MainActivity.switchTab( this, MainActivity.TAB_MAP );
            return true;
          }
          case MENU_EXIT:
            // call over to finish
            finish();
            return true;
        }
        return false;
    }

    // why is this even here? this is retarded. via:
    // http://stackoverflow.com/questions/456211/activity-restart-on-rotation-android
    @Override
    public void onConfigurationChanged( final Configuration newConfig ) {
      super.onConfigurationChanged( newConfig );
      setContentView( R.layout.list );
      info( "on config change" );
      
      // have to redo linkages/listeners
      setupUploadButton();
      setupMuteButton();
      setupList();
    }
    
    private void setupDatabase() {
      // could be set by nonconfig retain
      if ( state.dbHelper == null ) {
        state.dbHelper = new DatabaseHelper( this.getApplicationContext() );
        state.dbHelper.checkDB();
        state.dbHelper.start();
        lameStatic.dbHelper = state.dbHelper;
      }
      
      state.dbHelper.checkDB();
    }
    
    private void setupList() {
      // not set by nonconfig retain
      listAdapter = new NetworkListAdapter( this.getApplicationContext(), R.layout.row );
               
      final ListView listView = (ListView) findViewById( R.id.ListView01 );
      listView.setAdapter( listAdapter ); 
    }
    
    private void setupWifi() {
      // warn about turning off network notification
      final String notifOn = Settings.Secure.getString(getContentResolver(), 
          Settings.Secure.WIFI_NETWORKS_AVAILABLE_NOTIFICATION_ON );
      if ( notifOn != null && "1".equals( notifOn ) && state.wifiReceiver == null ) {
        Toast.makeText( this, "For best results, unset \"Network notification\" in"
            + " \"Wireless & networks\"->\"Wi-Fi settings\"", 
            Toast.LENGTH_LONG ).show();
      }
    
      final WifiManager wifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);
      final SharedPreferences prefs = this.getSharedPreferences( SHARED_PREFS, 0 );
      final Editor edit = prefs.edit();
      
      // keep track of for later
      boolean turnedWifiOn = false;
      if ( ! wifiManager.isWifiEnabled() ) {
        // tell user, cuz this takes a little while
        Toast.makeText( this, "Turning on WiFi", Toast.LENGTH_LONG ).show();
        
        // save so we can turn it back off when we exit  
        edit.putBoolean( PREF_WIFI_WAS_OFF, true );
        
        // just turn it on, but not in emulator cuz it crashes it
        if ( ! state.inEmulator ) {
          info( "turning on wifi");
          wifiManager.setWifiEnabled( true );
          info( "wifi on");
          turnedWifiOn = true;
        }
      }
      else {
        edit.putBoolean( PREF_WIFI_WAS_OFF, false );
      }
      edit.commit();
      
      if ( state.wifiReceiver == null ) {
        info( "new wifiReceiver");
        // wifi scan listener
        // this receiver is the main workhorse of the entire app
        state.wifiReceiver = new WifiReceiver( this, state.dbHelper, listAdapter );
        state.wifiReceiver.setupWifiTimer( turnedWifiOn );
        
        // register
        info( "register BroadcastReceiver");
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction( WifiManager.SCAN_RESULTS_AVAILABLE_ACTION );
        this.registerReceiver( state.wifiReceiver, intentFilter );
      }
      // always set our current list adapter
      state.wifiReceiver.setListAdapter( listAdapter );
      
      if ( state.wifiLock == null ) {
        info( "lock wifi radio on");
        // lock the radio on
        state.wifiLock = wifiManager.createWifiLock( WifiManager.WIFI_MODE_SCAN_ONLY, WIFI_LOCK_NAME );
        state.wifiLock.acquire();
      }
    }
    
    /**
     * Computes the battery level by registering a receiver to the intent triggered 
     * by a battery status/level change.
     */
    private void setupBattery() {
      if ( state.batteryLevelReceiver == null ) {
        state.batteryLevelReceiver = new BatteryLevelReceiver();
        IntentFilter batteryLevelFilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        registerReceiver(state.batteryLevelReceiver, batteryLevelFilter);
      }
    }
    
    /**
     * FileUploaderListener interface
     */
    public void uploadComplete() {
      state.uploading.set( false );
      info( "uploading complete" );
      // start a scan to get the ball rolling again if this is non-stop mode
      state.wifiReceiver.doWifiScan();
    }
    
    public void speak( final String string ) {
      if ( ! isMuted() && state.tts != null ) {
        state.tts.speak( string );
      }
    }
    
    private void setupLocation() {
      if ( state.gpsListener != null ) {
        // set on UI if we already have one
        setLocationUI( state.gpsListener.getSatCount() );
      }
      
      final LocationManager locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
      
      // check if there is a gps
      final LocationProvider locProvider = locationManager.getProvider( GPS_PROVIDER );
      if ( locProvider == null ) {
        Toast.makeText( this, "No GPS detected in device!", Toast.LENGTH_LONG ).show();
      }
      else if ( ! locationManager.isProviderEnabled( GPS_PROVIDER ) ) {
        // gps exists, but isn't on
        Toast.makeText( this, "Please turn on GPS", Toast.LENGTH_SHORT ).show();
        final Intent myIntent = new Intent( Settings.ACTION_LOCATION_SOURCE_SETTINGS );
        startActivity(myIntent);
      }
      // emulator crashes if you ask this
      if ( ! state.inEmulator && ! locationManager.isProviderEnabled( NETWORK_PROVIDER ) && state.gpsListener == null ) {
        Toast.makeText( this, "For best results, set \"Use wireless networks\" in \"Location & security\"", 
            Toast.LENGTH_LONG ).show();
      }

      if ( state.gpsListener == null ) {
        state.gpsListener = new GPSListener( this );
        locationManager.addGpsStatusListener( state.gpsListener );
      
        final List<String> providers = locationManager.getAllProviders();
        for ( String provider : providers ) {
          info( "available provider: " + provider );
          if ( ! "passive".equals( provider ) ) {
            locationManager.requestLocationUpdates( provider, LOCATION_UPDATE_INTERVAL, 0, state.gpsListener );
          }
        }
      }
    }
    
    public void setLocationUI( final int satCount ) {
      TextView tv = (TextView) this.findViewById( R.id.LocationTextView06 );
      tv.setText( "Sats: " + satCount );
      
      final Location location = state.gpsListener.getLocation();
      
      tv = (TextView) this.findViewById( R.id.LocationTextView01 );
      tv.setText( "Lat: " + (location == null ? "  (Waiting for GPS sync..)" 
          : state.numberFormat8.format( location.getLatitude() ) ) );
      
      tv = (TextView) this.findViewById( R.id.LocationTextView02 );
      tv.setText( "Lon: " + (location == null ? "" : state.numberFormat8.format( location.getLongitude() ) ) );
      
      tv = (TextView) this.findViewById( R.id.LocationTextView03 );
      tv.setText( "Speed: " + (location == null ? "" : state.numberFormat1.format( location.getSpeed() * 2.23693629f ) + "mph" ) );
      
      tv = (TextView) this.findViewById( R.id.LocationTextView04 );
      tv.setText( location == null ? "" : ("+/- " + state.numberFormat1.format( location.getAccuracy() ) + "m") );
      
      tv = (TextView) this.findViewById( R.id.LocationTextView05 );
      tv.setText( location == null ? "" : ("Alt: " + state.numberFormat1.format( location.getAltitude() ) + "m") );
    }
    
    private void setupUploadButton() {
      final Button button = (Button) findViewById( R.id.upload_button );
      button.setOnClickListener( new OnClickListener() {
          public void onClick( final View view ) {
            state.uploading.set( true );
            uploadFile( state.dbHelper );
          }
        });
    }
    
    private void setupService() {
      // could be set by nonconfig retain
      if ( state.serviceConnection == null ) {
        final Intent serviceIntent = new Intent( this.getApplicationContext(), WigleService.class );
        final ComponentName compName = startService( serviceIntent );
        if ( compName == null ) {
          ListActivity.error( "startService() failed!" );
        }
        else {
          ListActivity.info( "service started ok: " + compName );
        }
        
        state.serviceConnection = new ServiceConnection() {
          public void onServiceConnected( final ComponentName name, final IBinder iBinder ) {
            ListActivity.info( name + " service connected" ); 
          }
          public void onServiceDisconnected( final ComponentName name ) {
            ListActivity.info( name + " service disconnected" );
          }
        };  
      
        int flags = 0;
        this.bindService( serviceIntent, state.serviceConnection, flags );
      }
    }
    
    private void setupSound() {
      // could have been retained
      if ( state.soundPop == null ) {
        state.soundPop = createMediaPlayer( R.raw.pop );
      }
      if ( state.soundNewPop == null ) {
        state.soundNewPop = createMediaPlayer( R.raw.newpop );
      }

      // make volume change "media"
      this.setVolumeControlStream( AudioManager.STREAM_MUSIC );  
      
      if ( TTS.hasTTS() ) {
        // don't reuse an old one, has to be on *this* context
        if ( state.tts != null ) {
          state.tts.shutdown();
        }
        // this has to have the parent activity, for whatever wacky reasons
        Activity context = this.getParent();
        if ( context == null ) {
          context = this;
        }
        state.tts = new TTS( context );        
      }
      
      TelephonyManager tele = (TelephonyManager) getSystemService( TELEPHONY_SERVICE );
      if ( tele != null && state.phoneState == null ) {
        state.phoneState = new PhoneState();
        tele.listen( state.phoneState, PhoneStateListener.LISTEN_CALL_STATE );
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
      final MediaPlayer sound = createMp( this.getApplicationContext(), soundId );
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
            error("ioe create failed: " + ex, ex);
            // fall through
        } catch (IllegalArgumentException ex) {
            error("iae create failed: " + ex, ex);
           // fall through
        } catch (SecurityException ex) {
            error("se create failed: " + ex, ex);
            // fall through
        } catch ( Resources.NotFoundException ex ) {
            error("rnfe create failed("+resid+"): " + ex, ex );
        }
        return null;
    }
   
    public boolean isMuted() {
      if ( state.phoneState != null && state.phoneState.isPhoneActive() ) {
        // always be quiet when the phone is active
        return true;
      }
      boolean retval = this.getSharedPreferences(SHARED_PREFS, 0).getBoolean(PREF_MUTED, false);
      // info( "ismuted: " + retval );
      return retval;
    }
    
    private void uploadFile( final DatabaseHelper dbHelper ){
      info( "upload file" );
      // actually need this Activity context, for dialogs
      final FileUploaderTask task = new FileUploaderTask( this, dbHelper, this );
      task.start();
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

    public static void info( final String value, final Throwable t ) {
      Log.i( LOG_TAG, Thread.currentThread().getName() + "] " + value, t );
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
    
    public static void writeError( final Thread thread, final Throwable throwable, final Context context ) {
      try {
        final String error = "Thread: " + thread + " throwable: " + throwable;
        error( error, throwable );
        if ( hasSD() ) {
          File file = new File( Environment.getExternalStorageDirectory().getCanonicalPath() + "/wiglewifi/" );
          file.mkdirs();
          file = new File(Environment.getExternalStorageDirectory().getCanonicalPath() 
              + "/wiglewifi/" + ERROR_STACK_FILENAME + "_" + System.currentTimeMillis() + ".txt" );
          error( "Writing stackfile to: " + file.getCanonicalPath() + "/" + file.getName() );
          if ( ! file.exists() ) {
            file.createNewFile();
          }
          final FileOutputStream fos = new FileOutputStream( file );
          
          try {
            StringBuilder builder = new StringBuilder( "WigleWifi error log - " );
            SimpleDateFormat format = new SimpleDateFormat();
            builder.append( format.format( new Date() ) ).append( "\n" );
            final PackageManager pm = context.getPackageManager();
            final PackageInfo pi = pm.getPackageInfo(context.getPackageName(), 0);
            builder.append( "versionName: " ).append( pi.versionName ).append( "\n" );
            builder.append( "packageName: " ).append( pi.packageName ).append( "\n" );
            builder.append( "MODEL: " ).append( android.os.Build.MODEL ).append( "\n" );
            builder.append( "RELEASE: " ).append( android.os.Build.VERSION.RELEASE ).append( "\n" );
          
            builder.append( "BOARD: " ).append( android.os.Build.BOARD ).append( "\n" );
            builder.append( "BRAND: " ).append( android.os.Build.BRAND ).append( "\n" );
            // android 1.6 android.os.Build.CPU_ABI;
            builder.append( "DEVICE: " ).append( android.os.Build.DEVICE ).append( "\n" );
            builder.append( "DISPLAY: " ).append( android.os.Build.DISPLAY ).append( "\n" );
            builder.append( "FINGERPRINT: " ).append( android.os.Build.FINGERPRINT ).append( "\n" );
            builder.append( "HOST: " ).append( android.os.Build.HOST ).append( "\n" );
            builder.append( "ID: " ).append( android.os.Build.ID ).append( "\n" );
            // android 1.6: android.os.Build.MANUFACTURER;
            builder.append( "PRODUCT: " ).append( android.os.Build.PRODUCT ).append( "\n" );
            builder.append( "TAGS: " ).append( android.os.Build.TAGS ).append( "\n" );
            builder.append( "TIME: " ).append( android.os.Build.TIME ).append( "\n" );
            builder.append( "TYPE: " ).append( android.os.Build.TYPE ).append( "\n" );
            builder.append( "USER: " ).append( android.os.Build.USER ).append( "\n" );
            
            // write to file
            fos.write( builder.toString().getBytes( ENCODING ) );
          }
          catch ( Throwable er ) {
            // ohwell
            error( "error getting data for error: " + er, er );
          }
          
          fos.write( error.getBytes( ENCODING ) );
          throwable.printStackTrace( new PrintStream( fos ) );
          fos.close();
        }
      }
      catch ( final Exception ex ) {
        error( "error logging error: " + ex, ex );
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
        ListActivity.info( "no sd card apparently: " + ex, ex );
      }
      return sdCard != null && sdCard.exists() && sdCard.isDirectory() && sdCard.canRead() && sdCard.canWrite();
    }
    
    private void setupMaxidDebug() {
      final SharedPreferences prefs = ListActivity.this.getSharedPreferences( SHARED_PREFS, 0 );
      final long maxid = prefs.getLong( PREF_DB_MARKER, -1L );
      // load up the local value
      state.dbHelper.getLocationCountFromDB();
      final long loccount = state.dbHelper.getLocationCount();
      
      final Editor edit = prefs.edit();
      edit.putLong( PREF_MAX_DB, loccount );
      
      if ( maxid == -1L ) {    
        if ( loccount > 0 ) {
          // there is no preference set, yet there are locations, this is likely
          // a developer testing a new install on an old db, so set the pref.
          info( "setting db marker to: " + loccount );
          edit.putLong( PREF_DB_MARKER, loccount );
        }
      }
      edit.commit();
    }
}
