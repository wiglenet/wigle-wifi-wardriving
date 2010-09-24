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
import net.wigle.wigleandroid.listener.WifiReceiver;

import org.andnav.osm.util.GeoPoint;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.PendingIntent;
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
import android.os.Handler;
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
    // *** state. anything added here should be added to the retain copy-construction ***
    private NetworkListAdapter listAdapter;
    private Handler wifiTimer;
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
    private boolean isPhoneActive;
    private BatteryLevelReceiver batteryLevelReceiver;
    // *** end of state that must be added to the retain copy-constructor ***
    
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
        
        final String id = Settings.Secure.getString( getContentResolver(), Settings.Secure.ANDROID_ID );
        inEmulator = id == null;
        inEmulator |= "sdk".equals( android.os.Build.PRODUCT );
        inEmulator |= "google_sdk".equals( android.os.Build.PRODUCT );
        info( "id: '" + id + "' inEmulator: " + inEmulator + " product: " + android.os.Build.PRODUCT );
        info( "android release: '" + Build.VERSION.RELEASE + "' debug: " + DEBUG );
        
        // set up pending email intent to email stacktrace logs if needed
        final Intent errorReportIntent = new Intent( this, ErrorReportActivity.class );
        errorReportIntent.putExtra( ERROR_REPORT_DO_EMAIL, true );
        final PendingIntent pendingIntent = PendingIntent.getActivity(getBaseContext(), 0,
            errorReportIntent, errorReportIntent.getFlags() );
      
        // do some of our own error handling, write a file with the stack
        final UncaughtExceptionHandler origHandler = Thread.getDefaultUncaughtExceptionHandler();
        
        Thread.setDefaultUncaughtExceptionHandler( new Thread.UncaughtExceptionHandler(){
          public void uncaughtException( Thread thread, Throwable throwable ) {
            String error = "Thread: " + thread + " throwable: " + throwable;
            ListActivity.error( error );
            throwable.printStackTrace();
            
            ListActivity.writeError( thread, throwable, ListActivity.this );
            // set the email intent to go off in a few seconds
            AlarmManager mgr = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
            mgr.set( AlarmManager.RTC, System.currentTimeMillis() + 2000, pendingIntent );

            // give it to the regular handler
            origHandler.uncaughtException( thread, throwable );
          }
        });
        
        // test the error reporting
        // if( true ){ throw new RuntimeException( "weee" ); }
        
        final Object stored = getLastNonConfigurationInstance();
        if ( stored != null && stored instanceof ListActivity ) {
          // pry an orientation change, which calls destroy, but we set this in onRetainNonConfigurationInstance
          final ListActivity retained = (ListActivity) stored;
          this.listAdapter = retained.listAdapter;
          this.wifiTimer = retained.wifiTimer;
          this.dbHelper = retained.dbHelper;
          this.serviceConnection = retained.serviceConnection;
          this.finishing = retained.finishing;
          this.uploading = retained.uploading;
          this.soundPop = retained.soundPop;
          this.soundNewPop = retained.soundNewPop;
          this.wifiLock = retained.wifiLock;
          this.gpsListener = retained.gpsListener;
          this.wifiReceiver = retained.wifiReceiver;
          this.numberFormat1 = retained.numberFormat1;
          this.numberFormat8 = retained.numberFormat8;
          this.tts = retained.tts;
          this.inEmulator = retained.inEmulator;
          this.isPhoneActive = retained.isPhoneActive;
          this.batteryLevelReceiver = retained.batteryLevelReceiver;
          
          // tell those that need it that we have a new context
          gpsListener.setListActivity( this );
          wifiReceiver.setListActivity( this );
          setNetCountUI( this, wifiReceiver.getRunNetworkCount(), dbHelper.getNewNetworkCount(), 
              ListActivity.lameStatic.dbNets );
        }
        else {
          finishing = new AtomicBoolean( false );
          uploading = new AtomicBoolean( false );
          
          // new run, reset
          final SharedPreferences prefs = this.getSharedPreferences( SHARED_PREFS, 0 );
          final float prevRun = prefs.getFloat( PREF_DISTANCE_RUN, 0f );
          Editor edit = prefs.edit();
          edit.putFloat( PREF_DISTANCE_RUN, 0f );
          edit.putFloat( PREF_DISTANCE_PREV_RUN, prevRun );
          edit.commit();
        }
        
        if ( numberFormat1 == null ) {
          numberFormat1 = NumberFormat.getNumberInstance( Locale.US );
          if ( numberFormat1 instanceof DecimalFormat ) {
            ((DecimalFormat) numberFormat1).setMaximumFractionDigits( 1 );
          }
        }
        
        if ( numberFormat8 == null ) {
          numberFormat8 = NumberFormat.getNumberInstance( Locale.US );
          if ( numberFormat8 instanceof DecimalFormat ) {
            ((DecimalFormat) numberFormat8).setMaximumFractionDigits( 8 );
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
      return inEmulator;
    }
    
    public DatabaseHelper getDBHelper() {
      return dbHelper;
    }
    
    public BatteryLevelReceiver getBatteryLevelReceiver() {
      return batteryLevelReceiver;
    }
    
    public GPSListener getGPSListener() {
      return gpsListener;
    }
    
    public boolean isFinishing() {
      return finishing.get();
    }
    
    public boolean isUploading() {
      return uploading.get();
    }
    
    public void playNewNetSound() {
      if ( soundNewPop != null && ! soundNewPop.isPlaying() ) {
        try {
          // play sound on something new
          soundNewPop.start();
        }
        catch ( IllegalStateException ex ) {
          // ohwell, likely already playing
          info( "exception trying to play sound: " + ex );
        }
      }
      else {
        ListActivity.info( "soundNewPop is playing or null" );
      }
    }
    
    public void playRunNetSound() {
      if ( soundPop != null && ! soundPop.isPlaying() ) {
        try {
          // play sound on something new
          soundPop.start();
        }
        catch ( IllegalStateException ex ) {
          // ohwell, likely already playing
          info( "exception trying to play sound: " + ex );
        }
      }
      else {
        ListActivity.info( "soundPop is playing or null" );
      }
    }
    
    @Override
    public Object onRetainNonConfigurationInstance() {
      info( "onRetainNonConfigurationInstance" );
      // return this whole class to copy data from
      return this;
    }
    
    @Override
    public void onPause() {
      info( "LIST: paused. networks: " + wifiReceiver.getRunNetworkCount() );
      super.onPause();
    }
    
    @Override
    public void onResume() {
      info( "LIST: resumed. networks: " + wifiReceiver.getRunNetworkCount() );
      super.onResume();
    }
    
    @Override
    public void onStart() {
      info( "LIST: start. networks: " + wifiReceiver.getRunNetworkCount() );
      super.onStart();
    }
    
    @Override
    public void onStop() {
      info( "LIST: stop. networks: " + wifiReceiver.getRunNetworkCount() );
      super.onStop();
    }

    @Override
    public void onRestart() {
      info( "LIST: restart. networks: " + wifiReceiver.getRunNetworkCount() );
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
      info( "LIST: destroy. networks: " + wifiReceiver.getRunNetworkCount() );
      super.onDestroy();
    }
    
    @Override
    public void finish() {
      info( "LIST: finish. networks: " + wifiReceiver.getRunNetworkCount() );
      
      final boolean wasFinishing = finishing.getAndSet( true );
      if ( wasFinishing ) {
        info( "LIST: finish called twice!" );
      }

      final SharedPreferences prefs = this.getSharedPreferences( SHARED_PREFS, 0 );
      if ( prefs.getLong( PREF_SPEECH_PERIOD, 0 ) > 0 ) {
        speak( "done." );
      }
      
      // save our location for later runs
      gpsListener.saveLocation();
      
      // close the db. not in destroy, because it'll still write after that.
      dbHelper.close();
      
      final LocationManager locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
      if ( gpsListener != null ) {
        locationManager.removeGpsStatusListener( gpsListener );
        locationManager.removeUpdates( gpsListener );
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
        info( "serviceConnection not registered: " + ex, ex );
      }    
      
      // release the lock before turning wifi off
      if ( wifiLock != null && wifiLock.isHeld() ) {
        wifiLock.release();
      }
      
      final boolean wifiWasOff = prefs.getBoolean( PREF_WIFI_WAS_OFF, false );
      // don't call on emulator, it crashes it
      if ( wifiWasOff && ! inEmulator ) {
        // tell user, cuz this takes a little while
        Toast.makeText( this, "Turning WiFi back off", Toast.LENGTH_SHORT ).show();
        
        // well turn it of now that we're done
        final WifiManager wifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);
        info( "turning back off wifi" );
        wifiManager.setWifiEnabled( false );
      }
      
      if ( tts != null ) {
        if ( ! isMuted() ) {
          // give time for the above "done" to be said
          sleep( 250 );
        }
        tts.shutdown();
      }
      
      
      if ( DEBUG ) {
        Debug.stopMethodTracing();
      }

      // clean up.
      if ( soundPop != null ) {
        soundPop.release();
      }
      if ( soundNewPop != null ) {
        soundNewPop.release();
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
      final ListView listView = (ListView) findViewById( R.id.ListView01 );
      listView.setAdapter( listAdapter ); 
    }
    
    private void setupDatabase() {
      // could be set by nonconfig retain
      if ( dbHelper == null ) {
        dbHelper = new DatabaseHelper( this );
        dbHelper.checkDB();
        dbHelper.start();
      }
      
      dbHelper.checkDB();
    }
    
    private void setupList() {
      // may have been set by nonconfig retain
      if ( listAdapter == null ) {
        listAdapter = new NetworkListAdapter( this, R.layout.row );
      }
               
      final ListView listView = (ListView) findViewById( R.id.ListView01 );
      listView.setAdapter( listAdapter ); 
    }
    
    private void setupWifi() {
      // warn about turning off network notification
      final String notifOn = Settings.Secure.getString(getContentResolver(), 
          Settings.Secure.WIFI_NETWORKS_AVAILABLE_NOTIFICATION_ON );
      if ( notifOn != null && "1".equals( notifOn ) && wifiReceiver == null ) {
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
        if ( ! inEmulator ) {
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
      
      if ( wifiReceiver == null ) {
        info( "new wifiReceiver");
        // wifi scan listener
        // this receiver is the main workhorse of the entire app
        wifiReceiver = new WifiReceiver( this, dbHelper, listAdapter );
        wifiReceiver.setupWifiTimer( turnedWifiOn );
        
        // register
        info( "register BroadcastReceiver");
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction( WifiManager.SCAN_RESULTS_AVAILABLE_ACTION );
        this.registerReceiver( wifiReceiver, intentFilter );
      }
      
      if ( wifiLock == null ) {
        info( "lock wifi radio on");
        // lock the radio on
        wifiLock = wifiManager.createWifiLock( WifiManager.WIFI_MODE_SCAN_ONLY, WIFI_LOCK_NAME );
        wifiLock.acquire();
      }
    }
    
    /**
     * Computes the battery level by registering a receiver to the intent triggered 
     * by a battery status/level change.
     */
    private void setupBattery() {
      if ( batteryLevelReceiver == null ) {
        batteryLevelReceiver = new BatteryLevelReceiver();
        IntentFilter batteryLevelFilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        registerReceiver(batteryLevelReceiver, batteryLevelFilter);
      }
    }
    
    /**
     * FileUploaderListener interface
     */
    public void uploadComplete() {
      uploading.set( false );
      info( "uploading complete" );
      // start a scan to get the ball rolling again if this is non-stop mode
      wifiReceiver.doWifiScan();
    }
    
    public void speak( final String string ) {
      if ( ! isMuted() && tts != null ) {
        tts.speak( string );
      }
    }
    
    private void setupLocation() {
      if ( gpsListener != null ) {
        // set on UI if we already have one
        setLocationUI( gpsListener.getSatCount() );
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
      if ( ! inEmulator && ! locationManager.isProviderEnabled( NETWORK_PROVIDER ) && gpsListener == null ) {
        Toast.makeText( this, "For best results, set \"Use wireless networks\" in \"Location & security\"", 
            Toast.LENGTH_LONG ).show();
      }

      if ( gpsListener == null ) {
        gpsListener = new GPSListener( this );
        locationManager.addGpsStatusListener( gpsListener );
      
        final List<String> providers = locationManager.getAllProviders();
        for ( String provider : providers ) {
          info( "available provider: " + provider );
          if ( ! "passive".equals( provider ) ) {
            locationManager.requestLocationUpdates( provider, LOCATION_UPDATE_INTERVAL, 0, gpsListener );
          }
        }
      }
    }
    
    public void setLocationUI( final int satCount ) {
      TextView tv = (TextView) this.findViewById( R.id.LocationTextView06 );
      tv.setText( "Sats: " + satCount );
      
      final Location location = gpsListener.getLocation();
      
      tv = (TextView) this.findViewById( R.id.LocationTextView01 );
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
            uploading.set( true );
            uploadFile( dbHelper );
          }
        });
    }
    
    private void setupService() {
      final Intent serviceIntent = new Intent( this, WigleService.class );
      
      // could be set by nonconfig retain
      if ( serviceConnection == null ) {
        final ComponentName compName = startService( serviceIntent );
        if ( compName == null ) {
          ListActivity.error( "startService() failed!" );
        }
        else {
          ListActivity.info( "service started ok: " + compName );
        }
        
        serviceConnection = new ServiceConnection(){
          public void onServiceConnected( final ComponentName name, final IBinder iBinder ) {
            ListActivity.info( name + " service connected" ); 
          }
          public void onServiceDisconnected( final ComponentName name ) {
            ListActivity.info( name + " service disconnected" );
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

      // make volume change "media"
      this.setVolumeControlStream( AudioManager.STREAM_MUSIC );  
      
      if ( TTS.hasTTS() ) {
        // don't reuse an old one, has to be on *this* context
        if ( tts != null ) {
          tts.shutdown();
        }
        // this has to have the parent activity, for whatever wacky reasons
        Activity context = this.getParent();
        if ( context == null ) {
          context = this;
        }
        tts = new TTS( context );        
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
      if ( isPhoneActive ) {
        // always be quiet when the phone is active
        return true;
      }
      boolean retval = this.getSharedPreferences(SHARED_PREFS, 0).getBoolean(PREF_MUTED, false);
      // info( "ismuted: " + retval );
      return retval;
    }
    
    private void uploadFile( final DatabaseHelper dbHelper ){
      info( "upload file" );
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
      dbHelper.getLocationCountFromDB();
      final long loccount = dbHelper.getLocationCount();
      
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
