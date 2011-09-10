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
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import net.wigle.wigleandroid.MainActivity.Doer;
import net.wigle.wigleandroid.listener.BatteryLevelReceiver;
import net.wigle.wigleandroid.listener.GPSListener;
import net.wigle.wigleandroid.listener.PhoneState;
import net.wigle.wigleandroid.listener.PhoneStateFactory;
import net.wigle.wigleandroid.listener.WifiReceiver;

import org.osmdroid.util.GeoPoint;

import android.app.Activity;
import android.app.Dialog;
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
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.AdapterView.OnItemSelectedListener;

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
      private NumberFormat numberFormat0;
      private NumberFormat numberFormat1;
      private NumberFormat numberFormat8;
      private TTS tts;
      private boolean inEmulator;
      private BatteryLevelReceiver batteryLevelReceiver;
      private PhoneState phoneState;
      private FileUploaderTask fileUploaderTask;
    }
    private State state;
    // *** end of state that is retained ***
    
    private NetworkListAdapter listAdapter;
    private String previousStatus;
    
    public static final String FILE_POST_URL = "https://wigle.net/gps/gps/main/confirmfile/";
    private static final String LOG_TAG = "wigle";
    private static final int MENU_SETTINGS = 10;
    private static final int MENU_EXIT = 11;
    private static final int MENU_WAKELOCK = 12;
    private static final int MENU_SORT = 13;
    private static final int MENU_SCAN = 14;
    private static final int MENU_FILTER = 15;
    
    private static final int SORT_DIALOG = 100;
    private static final int SSID_FILTER = 102;
    
    public static final String ENCODING = "ISO-8859-1";
    public static final float MIN_DISTANCE_ACCURACY = 32f;
    static final String ERROR_STACK_FILENAME = "errorstack";
    static final String ERROR_REPORT_DO_EMAIL = "doEmail";
    static final String ERROR_REPORT_DIALOG = "doDialog";
    
    // preferences
    public static final String SHARED_PREFS = "WiglePrefs";
    public static final String PREF_USERNAME = "username";
    public static final String PREF_PASSWORD = "password";
    public static final String PREF_SHOW_CURRENT = "showCurrent";
    public static final String PREF_BE_ANONYMOUS = "beAnonymous";
    public static final String PREF_DONATE = "donate";
    public static final String PREF_DB_MARKER = "dbMarker";
    public static final String PREF_MAX_DB = "maxDbMarker";
    public static final String PREF_NETS_UPLOADED = "netsUploaded";
    public static final String PREF_SCAN_PERIOD_STILL = "scanPeriodStill";
    public static final String PREF_SCAN_PERIOD = "scanPeriod";
    public static final String PREF_SCAN_PERIOD_FAST = "scanPeriodFast";
    public static final String GPS_SCAN_PERIOD = "gpsPeriod";
    public static final String PREF_FOUND_SOUND = "foundSound";
    public static final String PREF_FOUND_NEW_SOUND = "foundNewSound";
    public static final String PREF_SPEECH_PERIOD = "speechPeriod";
    public static final String PREF_RESET_WIFI_PERIOD = "resetWifiPeriod";
    public static final String PREF_BATTERY_KILL_PERCENT = "batteryKillPercent";    
    public static final String PREF_SPEECH_GPS = "speechGPS";
    public static final String PREF_MUTED = "muted";
    public static final String PREF_WIFI_WAS_OFF = "wifiWasOff";
    public static final String PREF_DISTANCE_RUN = "distRun";
    public static final String PREF_DISTANCE_TOTAL = "distTotal";
    public static final String PREF_DISTANCE_PREV_RUN = "distPrevRun";
    public static final String PREF_MAP_ONLY_NEWDB = "mapOnlyNewDB";
    public static final String PREF_PREV_LAT = "prevLat";
    public static final String PREF_PREV_LON = "prevLon";
    public static final String PREF_PREV_ZOOM = "prevZoom";
    public static final String PREF_LIST_SORT = "listSort";
    public static final String PREF_SCAN_RUNNING = "scanRunning";
    public static final String PREF_METRIC = "metric";
    public static final String PREF_MAP_LABEL = "mapLabel";
    public static final String PREF_CIRCLE_SIZE_MAP = "circleSizeMap";
    
    // what to speak on announcements
    public static final String PREF_SPEAK_RUN = "speakRun";
    public static final String PREF_SPEAK_NEW_WIFI = "speakNew";
    public static final String PREF_SPEAK_NEW_CELL = "speakNewCell";
    public static final String PREF_SPEAK_QUEUE = "speakQueue";
    public static final String PREF_SPEAK_MILES = "speakMiles";
    public static final String PREF_SPEAK_TIME = "speakTime";
    public static final String PREF_SPEAK_BATTERY = "speakBattery";
    public static final String PREF_SPEAK_SSID = "speakSsid";
    
    // map ssid filter
    public static final String PREF_MAPF_REGEX = "mapfRegex";
    public static final String PREF_MAPF_INVERT = "mapfInvert";
    public static final String PREF_MAPF_OPEN = "mapfOpen";
    public static final String PREF_MAPF_WEP = "mapfWep";
    public static final String PREF_MAPF_WPA = "mapfWpa";
    public static final String PREF_MAPF_CELL = "mapfCell";    
    public static final String PREF_MAPF_ENABLED = "mapfEnabled";
    public static final String FILTER_PREF_PREFIX = "LA";
    
    public static final String NETWORK_EXTRA_BSSID = "extraBssid";
    
    public static final long DEFAULT_SPEECH_PERIOD = 60L;
    public static final long DEFAULT_RESET_WIFI_PERIOD = 90000L;    
    public static final long LOCATION_UPDATE_INTERVAL = 1000L;
    public static final long SCAN_STILL_DEFAULT = 3000L;
    public static final long SCAN_DEFAULT = 2000L;
    public static final long SCAN_FAST_DEFAULT = 1000L;
    public static final long DEFAULT_BATTERY_KILL_PERCENT = 2L;    
    
    static final String ANONYMOUS = "anonymous";
    private static final String WIFI_LOCK_NAME = "wigleWifiLock";
    //static final String THREAD_DEATH_MESSAGE = "threadDeathMessage";
    static final boolean DEBUG = false;
    
    /** cross-activity communication */
    public static class TrailStat {
      public int newWifiForRun = 0;
      public int newWifiForDB = 0;
      public int newCellForRun = 0;
      public int newCellForDB = 0;
    }
    public static class LameStatic {
      public Location location; 
      public ConcurrentLinkedHashMap<GeoPoint,TrailStat> trail = 
        new ConcurrentLinkedHashMap<GeoPoint,TrailStat>( 512 );
      public int runNets;
      public long newNets;
      public long newWifi;
      public long newCells;
      public int currNets;
      public int preQueueSize;
      public long dbNets;
      public long dbLocs;
      public DatabaseHelper dbHelper;
      public Set<String> runNetworks;
      public QueryArgs queryArgs;
    }
    public static final LameStatic lameStatic = new LameStatic();
    
    // cache
    private static final ThreadLocal<ConcurrentLinkedHashMap<String,Network>> networkCache = 
      new ThreadLocal<ConcurrentLinkedHashMap<String,Network>>() {
        protected ConcurrentLinkedHashMap<String,Network> initialValue() {
            return new ConcurrentLinkedHashMap<String,Network>( 128 );
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
        
        // set ourselves for later finishing by other activities
        MainActivity main = MainActivity.getMainActivity( this );
        if ( main != null ) {
          main.setListActivity( this );
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
          if ( state.fileUploaderTask != null ) {
            state.fileUploaderTask.setContext( this );
          }
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
        
        if ( state.numberFormat0 == null ) {
          state.numberFormat0 = NumberFormat.getNumberInstance( Locale.US );
          if ( state.numberFormat0 instanceof DecimalFormat ) {
            ((DecimalFormat) state.numberFormat0).setMaximumFractionDigits( 0 );
          }
        }
        
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
        info( "setNetCountUI" );
        setNetCountUI();
        info( "setStatusUI" );
        setStatusUI( (String) null );
        info( "setup complete" );
    }
    
    public void setNetCountUI() {
      TextView tv = (TextView) findViewById( R.id.stats_run );
      tv.setText( getString(R.string.run) + ": " + state.wifiReceiver.getRunNetworkCount() );
      tv = (TextView) findViewById( R.id.stats_new );
      tv.setText( getString(R.string.new_word) + ": " + state.dbHelper.getNewNetworkCount() );
      tv = (TextView) findViewById( R.id.stats_dbnets );
      tv.setText( getString(R.string.db) + ": " + state.dbHelper.getNetworkCount() );
    }
    
    public void setStatusUI( String status ) {
      if ( status == null ) {
        status = previousStatus;
      }
      if ( status != null ) {
        // keep around a previous, for orientation changes
        previousStatus = status;
        final TextView tv = (TextView) findViewById( R.id.status );
        tv.setText( status );
      }
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
    
    public PhoneState getPhoneState() {
      return state.phoneState;
    }
    
    public boolean isFinishing() {
      return state.finishing.get();
    }
    
    public boolean isUploading() {
      return state.uploading.get();
    }
    
    public boolean isScanning() {
      return isScanning(this);
    }
    
    public static boolean isScanning(final Context context) {
      final SharedPreferences prefs = context.getSharedPreferences( SHARED_PREFS, 0 );
      return prefs.getBoolean( PREF_SCAN_RUNNING, true );
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
        // have to use the app context to bind to the service, cuz we're in tabs
        getApplicationContext().unbindService( state.serviceConnection );
      }
      catch ( final IllegalArgumentException ex ) {
        info( "serviceConnection not registered: " + ex, ex );
      }    
      
      // release the lock before turning wifi off
      if ( state.wifiLock != null && state.wifiLock.isHeld() ) {
        state.wifiLock.release();
      }
      
      final SharedPreferences prefs = this.getSharedPreferences( SHARED_PREFS, 0 );
      final boolean wifiWasOff = prefs.getBoolean( PREF_WIFI_WAS_OFF, false );
      // don't call on emulator, it crashes it
      if ( wifiWasOff && ! state.inEmulator ) {
        // tell user, cuz this takes a little while
        Toast.makeText( this, getString(R.string.turning_wifi_off), Toast.LENGTH_SHORT ).show();
        
        // well turn it of now that we're done
        final WifiManager wifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);
        info( "turning back off wifi" );
        wifiManager.setWifiEnabled( false );
      }
      
      TelephonyManager tele = (TelephonyManager) getSystemService( TELEPHONY_SERVICE );
      if ( tele != null && state.phoneState != null ) {
        tele.listen( state.phoneState, PhoneStateListener.LISTEN_NONE );
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
      MenuItem item = menu.add(0, MENU_SORT, 0, "Sort Options");
      item.setIcon( android.R.drawable.ic_menu_sort_alphabetically );
      
      final String scan = isScanning() ? "Off" : "On";
      item = menu.add(0, MENU_SCAN, 0, "Scan " + scan);
      item.setIcon( isScanning() ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play );      
      
      final String wake = MainActivity.isScreenLocked( this ) ? "Let Screen Sleep" : "Keep Screen On";
      item = menu.add(0, MENU_WAKELOCK, 0, wake);
      item.setIcon( android.R.drawable.ic_menu_gallery );
      
      item = menu.add(0, MENU_EXIT, 0, "Exit");
      item.setIcon( android.R.drawable.ic_menu_close_clear_cancel );
      
      item = menu.add(0, MENU_FILTER, 0, "SSID Label Filter");
      item.setIcon( android.R.drawable.ic_menu_search );
              
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
            final Intent settingsIntent = new Intent( this, SettingsActivity.class );
            startActivity( settingsIntent );
            break;
          }
          case MENU_WAKELOCK: {
            boolean screenLocked = ! MainActivity.isScreenLocked( this );
            MainActivity.setLockScreen( this, screenLocked );
            final String wake = screenLocked ? "Let Screen Sleep" : "Keep Screen On";
            item.setTitle( wake );
            return true;
          }
          case MENU_SORT: {
            info("sort dialog");
            showDialog( SORT_DIALOG );
            return true;
          }
          case MENU_SCAN: {
            boolean scanning = ! isScanning();
            final Editor edit = this.getSharedPreferences( SHARED_PREFS, 0 ).edit();
            edit.putBoolean(PREF_SCAN_RUNNING, scanning);
            edit.commit();
            String name = scanning ? "Scan Off" : "Scan On";
            item.setTitle( name );
            item.setIcon( isScanning() ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play );
            handleScanChange();
            return true;
          }
          case MENU_EXIT:
            // call over to finish
            finish();
            return true;
          case MENU_FILTER:
            showDialog( SSID_FILTER );
            return true;          
        }        
        return false;
    }
    
    private void handleScanChange() {
      final boolean isScanning = isScanning();
      info("handleScanChange: isScanning now: " + isScanning );
      if ( isScanning ) {
        // turn on location updates
        this.setLocationUpdates(LOCATION_UPDATE_INTERVAL, 0f);
        setStatusUI( "Scanning Turned On" );
        if ( ! state.wifiLock.isHeld() ){
          state.wifiLock.acquire();
        }
      }
      else {
        // turn off location updates
        this.setLocationUpdates(0L, 0f);
        setStatusUI( "Scanning Turned Off" );
        state.gpsListener.handleScanStop();
        if ( state.wifiLock.isHeld() ){
          state.wifiLock.release();
        }
      }
    }
    
    @Override
    public Dialog onCreateDialog( int which ) {
      switch ( which ) {
        case SSID_FILTER:
          return MappingActivity.createSsidFilterDialog(this, FILTER_PREF_PREFIX);
        case SORT_DIALOG:
          final Dialog dialog = new Dialog( this );
  
          dialog.setContentView( R.layout.listdialog );
          dialog.setTitle( getString(R.string.sort_title) );
  
          TextView text = (TextView) dialog.findViewById( R.id.text );
          text.setText( getString(R.string.sort_spin_label) );
          
          final SharedPreferences prefs = getSharedPreferences( SHARED_PREFS, 0 );
          final Editor editor = prefs.edit();
          
          Spinner spinner = (Spinner) dialog.findViewById( R.id.sort_spinner );
          ArrayAdapter<String> adapter = new ArrayAdapter<String>(
              this, android.R.layout.simple_spinner_item);
          final int[] listSorts = new int[]{ WifiReceiver.CHANNEL_COMPARE, WifiReceiver.CRYPTO_COMPARE,
              WifiReceiver.FIND_TIME_COMPARE, WifiReceiver.SIGNAL_COMPARE, WifiReceiver.SSID_COMPARE };
          final String[] listSortName = new String[]{ getString(R.string.channel),getString(R.string.crypto),
              getString(R.string.found_time),getString(R.string.signal),getString(R.string.ssid) };
          int listSort = prefs.getInt( PREF_LIST_SORT, WifiReceiver.SIGNAL_COMPARE );
          int periodIndex = 0;
          for ( int i = 0; i < listSorts.length; i++ ) {
            adapter.add( listSortName[i] );
            if ( listSort == listSorts[i] ) {
              periodIndex = i;
            }
          }
          adapter.setDropDownViewResource( android.R.layout.simple_spinner_dropdown_item );
          spinner.setAdapter( adapter );
          spinner.setSelection( periodIndex );
          spinner.setOnItemSelectedListener( new OnItemSelectedListener() {
            public void onItemSelected( final AdapterView<?> parent, final View v, final int position, final long id ) {
              // set pref
              final int listSort = listSorts[position];
              ListActivity.info( PREF_LIST_SORT + " setting list sort: " + listSort );
              editor.putInt( PREF_LIST_SORT, listSort );
              editor.commit();
            }
            public void onNothingSelected( final AdapterView<?> arg0 ) {}
            });
          
          Button ok = (Button) dialog.findViewById( R.id.listdialog_button );
          ok.setOnClickListener( new OnClickListener() {
              public void onClick( final View buttonView ) {  
                try {
                  dialog.dismiss();
                }
                catch ( Exception ex ) {
                  // guess it wasn't there anyways
                  info( "exception dismissing sort dialog: " + ex );
                }
              }
            } );
          
          return dialog;
        default:
          error( "unhandled dialog: " + which );
      }
      return null;
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
      state.wifiReceiver.setListAdapter( listAdapter );
      setNetCountUI();
      setLocationUI();
      setStatusUI( previousStatus );
    }
    
    private void setupDatabase() {
      // could be set by nonconfig retain
      if ( state.dbHelper == null ) {
        state.dbHelper = new DatabaseHelper( this.getApplicationContext() );
        //state.dbHelper.checkDB();
        state.dbHelper.start();
        lameStatic.dbHelper = state.dbHelper;
      }      
    }
    
    private void setupList() {
      // not set by nonconfig retain
      listAdapter = new NetworkListAdapter( this.getApplicationContext(), R.layout.row );
               
      final ListView listView = (ListView) findViewById( R.id.ListView01 );
      listView.setAdapter( listAdapter ); 
      listView.setOnItemClickListener( new AdapterView.OnItemClickListener() {
        public void onItemClick( AdapterView<?> parent, View view, final int position, final long id ) {
          final Network network = (Network) parent.getItemAtPosition( position );
          final Intent intent = new Intent( ListActivity.this, NetworkActivity.class );
          intent.putExtra( NETWORK_EXTRA_BSSID, network.getBssid() );
          ListActivity.this.startActivity( intent );
        }
      });
    }
    
    private void setupWifi() {
      // warn about turning off network notification
      final String notifOn = Settings.Secure.getString(getContentResolver(), 
          Settings.Secure.WIFI_NETWORKS_AVAILABLE_NOTIFICATION_ON );
      if ( notifOn != null && "1".equals( notifOn ) && state.wifiReceiver == null ) {
        Toast.makeText( this, getString(R.string.best_results), 
            Toast.LENGTH_LONG ).show();
      }
    
      final WifiManager wifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);
      final SharedPreferences prefs = this.getSharedPreferences( SHARED_PREFS, 0 );
      final Editor edit = prefs.edit();
      
      // keep track of for later
      boolean turnedWifiOn = false;
      if ( ! wifiManager.isWifiEnabled() ) {
        // tell user, cuz this takes a little while
        Toast.makeText( this, getString(R.string.turn_on_wifi), Toast.LENGTH_LONG ).show();
        
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
      scheduleScan();
      state.fileUploaderTask = null;
    }
    
    public void scheduleScan() {
      state.wifiReceiver.scheduleScan();
    }
    
    public void speak( final String string ) {
      if ( ! isMuted() && state.tts != null ) {
        state.tts.speak( string );
      }
    }
    
    public void interruptSpeak() {
      if ( state.tts != null ) {
        state.tts.stop();
      }
    }
    
    private void setupLocation() {
      // set on UI if we already have one
      setLocationUI();
      
      final LocationManager locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
      
      // check if there is a gps
      final LocationProvider locProvider = locationManager.getProvider( GPS_PROVIDER );
      if ( locProvider == null ) {
        Toast.makeText( this, getString(R.string.no_gps_device), Toast.LENGTH_LONG ).show();
      }
      else if ( ! locationManager.isProviderEnabled( GPS_PROVIDER ) ) {
        // gps exists, but isn't on
        Toast.makeText( this, getString(R.string.turn_on_gps), Toast.LENGTH_SHORT ).show();
        final Intent myIntent = new Intent( Settings.ACTION_LOCATION_SOURCE_SETTINGS );
        try {
          startActivity(myIntent);
        }
        catch (Exception ex) {
          error("exception trying to start location activity: " + ex, ex);
        }
      }
      // emulator crashes if you ask this
      if ( ! state.inEmulator && ! locationManager.isProviderEnabled( NETWORK_PROVIDER ) && state.gpsListener == null ) {
        //Toast.makeText( this, "For best results, set \"Use wireless networks\" in \"Location & security\"", 
        //    Toast.LENGTH_LONG ).show();
      }

      if ( state.gpsListener == null ) {
        // force a listener to be created
        handleScanChange();
      }
    }
    
    public long getLocationSetPeriod() {
      final SharedPreferences prefs = getSharedPreferences( ListActivity.SHARED_PREFS, 0 );
      final long prefPeriod = prefs.getLong(ListActivity.GPS_SCAN_PERIOD, ListActivity.LOCATION_UPDATE_INTERVAL);
      long setPeriod = prefPeriod;
      if (setPeriod == 0 ){
        setPeriod = Math.max(state.wifiReceiver.getScanPeriod(), ListActivity.LOCATION_UPDATE_INTERVAL); 
      }
      return setPeriod;
    }
    
    public void setLocationUpdates() {
      final long setPeriod = getLocationSetPeriod();    
      setLocationUpdates(setPeriod, 0f);
    }    
    
    /**
     * resets the gps listener to the requested update time and distance.
     * an updateIntervalMillis of <= 0 will not register for updates. 
     */
    public void setLocationUpdates(final long updateIntervalMillis, final float updateMeters) {
      final LocationManager locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
      
      if ( state.gpsListener != null ) {
        // remove any old requests
        locationManager.removeUpdates( state.gpsListener );
        locationManager.removeGpsStatusListener( state.gpsListener );
      }
      
      // create a new listener to try and get around the gps stopping bug
      state.gpsListener = new GPSListener( this );
      locationManager.addGpsStatusListener( state.gpsListener );      

      final List<String> providers = locationManager.getAllProviders();
      for ( String provider : providers ) {
        info( "available provider: " + provider + " updateIntervalMillis: " + updateIntervalMillis );
        if ( ! "passive".equals( provider ) && updateIntervalMillis > 0 ) {
          locationManager.requestLocationUpdates( provider, updateIntervalMillis, updateMeters, state.gpsListener );
        }
      }
    }
    
    public void setLocationUI() {
      if ( state.gpsListener == null ) {
        return;
      }
      
      try {       
        TextView tv = (TextView) this.findViewById( R.id.LocationTextView06 );
        tv.setText( getString(R.string.list_short_sats) + " " + state.gpsListener.getSatCount() );
        
        final Location location = state.gpsListener.getLocation();
        
        tv = (TextView) this.findViewById( R.id.LocationTextView01 );
        String latText = "";
        if ( location == null ) {
          if ( isScanning() ) {
            latText = getString(R.string.list_waiting_gps);
          }
          else {
            latText = getString(R.string.list_scanning_off);
          }
        }
        else {
          latText = state.numberFormat8.format( location.getLatitude() );
        }
        tv.setText( getString(R.string.list_short_lat) + " " + latText );
        
        tv = (TextView) this.findViewById( R.id.LocationTextView02 );
        tv.setText( getString(R.string.list_short_lon) + " " + (location == null ? "" : state.numberFormat8.format( location.getLongitude() ) ) );
        
        tv = (TextView) this.findViewById( R.id.LocationTextView03 );
        tv.setText( getString(R.string.list_speed) + " " + (location == null ? "" : metersPerSecondToSpeedString(state.numberFormat1, this, location.getSpeed()) ) );
        
        TextView tv4 = (TextView) this.findViewById( R.id.LocationTextView04 );
        TextView tv5 = (TextView) this.findViewById( R.id.LocationTextView05 );
        if ( location == null ) {
          tv4.setText( "" );
          tv5.setText( "" );
        }
        else {
          String distString = DashboardActivity.metersToString( 
              state.numberFormat0, this, location.getAccuracy(), true );
          tv4.setText( "+/- " + distString );
          distString = DashboardActivity.metersToString( 
              state.numberFormat0, this, (float) location.getAltitude(), true );
          tv5.setText( getString(R.string.list_short_alt) + " " + distString );
        }
      }
      catch ( IncompatibleClassChangeError ex ) {
        // yeah, saw this in the wild, who knows.
        error( "wierd ex: " + ex, ex);
      }
    }
    
    public static String metersPerSecondToSpeedString( final NumberFormat numberFormat, final Context context,
        final float metersPerSecond ) {
      
      final SharedPreferences prefs = context.getSharedPreferences( ListActivity.SHARED_PREFS, 0 );
      final boolean metric = prefs.getBoolean( ListActivity.PREF_METRIC, false );
      
      String retval = null;
      if ( metric ) {
        retval = numberFormat.format( metersPerSecond * 3.6 ) + " " + context.getString(R.string.kmph);
      }
      else {
        retval = numberFormat.format( metersPerSecond * 2.23693629f ) + " " + context.getString(R.string.mph);
      }
      return retval;
    }
    
    private void setupUploadButton() {
      final Button button = (Button) findViewById( R.id.upload_button );
      button.setOnClickListener( new OnClickListener() { 
          public void onClick( final View view ) {
            MainActivity.createConfirmation( ListActivity.this, getString(R.string.list_upload), new Doer() {
              @Override
              public void execute() {                
                state.uploading.set( true );
                uploadFile( state.dbHelper );
              }
            } );
          }
        });
    }
    
    private void setupService() {
      // could be set by nonconfig retain
      if ( state.serviceConnection == null ) {        
        final Intent serviceIntent = new Intent( this.getApplicationContext(), WigleService.class );
        final ComponentName compName = startService( serviceIntent );
        if ( compName == null ) {
          error( "startService() failed!" );
        }
        else {
          info( "service started ok: " + compName );
        }
        
        state.serviceConnection = new ServiceConnection() {
          public void onServiceConnected( final ComponentName name, final IBinder iBinder ) {
            info( name + " service connected" ); 
          }
          public void onServiceDisconnected( final ComponentName name ) {
            info( name + " service disconnected" );
          }
        };  
      
        int flags = 0;
        // have to use the app context to bind to the service, cuz we're in tabs
        // http://code.google.com/p/android/issues/detail?id=2483#c2
        final boolean bound = getApplicationContext().bindService( serviceIntent, state.serviceConnection, flags );
        info( "service bound: " + bound );        
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
        state.phoneState = PhoneStateFactory.createPhoneState();
        final int signal_strengths = 256;
        tele.listen( state.phoneState, PhoneStateListener.LISTEN_SERVICE_STATE
            | PhoneStateListener.LISTEN_CALL_STATE | PhoneStateListener.LISTEN_SIGNAL_STRENGTH | signal_strengths );
      }
      
      setupMuteButton();
    }
     
    private void setupMuteButton() {
      final Button mute = (Button) this.findViewById(R.id.mute);
      final SharedPreferences prefs = this.getSharedPreferences(SHARED_PREFS, 0);
      final boolean muted = prefs.getBoolean(PREF_MUTED, false);
      if ( muted ) {
        mute.setText(getString(R.string.mute));
      }
      mute.setOnClickListener(new OnClickListener(){
        public void onClick( final View buttonView ) {
          boolean muted = prefs.getBoolean(PREF_MUTED, false);
          muted = ! muted;
          Editor editor = prefs.edit();
          editor.putBoolean( PREF_MUTED, muted );
          editor.commit();
          
          if ( muted ) {
            mute.setText(getString(R.string.play));
            interruptSpeak();
          }
          else {
            mute.setText(getString(R.string.mute));
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
            final String filepath = MainActivity.safeFilePath( Environment.getExternalStorageDirectory() ) + "/wiglewifi/";
            final File path = new File( filepath );
            path.mkdirs();
            openString = filepath + name;
        }

        final File f = new File( openString );
      
        // see if it exists already
        if ( ! f.exists() ) { 
            info( "causing " + MainActivity.safeFilePath( f ) + " to be made" );
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
      state.fileUploaderTask = new FileUploaderTask( this, dbHelper, this );
      state.fileUploaderTask.start();
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
    public static void warn( final String value ) {
      Log.w( LOG_TAG, Thread.currentThread().getName() + "] " + value );
    }
    public static void error( final String value ) {
      Log.e( LOG_TAG, Thread.currentThread().getName() + "] " + value );
    }

    public static void info( final String value, final Throwable t ) {
      Log.i( LOG_TAG, Thread.currentThread().getName() + "] " + value, t );
    }
    public static void warn( final String value, final Throwable t ) {
      Log.w( LOG_TAG, Thread.currentThread().getName() + "] " + value, t );
    }
    public static void error( final String value, final Throwable t ) {
      Log.e( LOG_TAG, Thread.currentThread().getName() + "] " + value, t );
    }

    /**
     * get the per-thread network LRU cache
     * @return per-thread network cache
     */
    public static ConcurrentLinkedHashMap<String,Network> getNetworkCache() {
      return networkCache.get();
    }
    
    public static void writeError( final Thread thread, final Throwable throwable, final Context context ) {
      try {
        final String error = "Thread: " + thread + " throwable: " + throwable;
        error( error, throwable );
        if ( hasSD() ) {
          File file = new File( MainActivity.safeFilePath( Environment.getExternalStorageDirectory() ) + "/wiglewifi/" );
          file.mkdirs();
          file = new File(MainActivity.safeFilePath( Environment.getExternalStorageDirectory() ) 
              + "/wiglewifi/" + ERROR_STACK_FILENAME + "_" + System.currentTimeMillis() + ".txt" );
          error( "Writing stackfile to: " + MainActivity.safeFilePath( file ) + "/" + file.getName() );
          if ( ! file.exists() ) {
            file.createNewFile();
          }
          final FileOutputStream fos = new FileOutputStream( file );
          
          try {
            final String baseErrorMessage = MainActivity.getBaseErrorMessage( throwable, false );
            StringBuilder builder = new StringBuilder( "WigleWifi error log - " );
            SimpleDateFormat format = new SimpleDateFormat();
            builder.append( format.format( new Date() ) ).append( "\n" );
            final PackageManager pm = context.getPackageManager();
            final PackageInfo pi = pm.getPackageInfo(context.getPackageName(), 0);
            builder.append( "versionName: " ).append( pi.versionName ).append( "\n" );
            builder.append( "baseError: " ).append( baseErrorMessage ).append( "\n\n" );
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
          
          fos.write( (error + "\n\n").getBytes( ENCODING ) );
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
      File sdCard = new File( MainActivity.safeFilePath( Environment.getExternalStorageDirectory() ) + "/" );
      return sdCard != null && sdCard.exists() && sdCard.isDirectory() && sdCard.canRead() && sdCard.canWrite();
    }        
}
