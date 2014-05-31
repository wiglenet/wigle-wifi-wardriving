package net.wigle.wigleandroid;

import static android.location.LocationManager.GPS_PROVIDER;
import static android.location.LocationManager.NETWORK_PROVIDER;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.lang.Thread.UncaughtExceptionHandler;
import java.text.DateFormat;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

import net.wigle.wigleandroid.background.FileUploaderTask;
import net.wigle.wigleandroid.listener.BatteryLevelReceiver;
import net.wigle.wigleandroid.listener.GPSListener;
import net.wigle.wigleandroid.listener.PhoneState;
import net.wigle.wigleandroid.listener.PhoneStateFactory;
import net.wigle.wigleandroid.listener.WifiReceiver;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.content.res.Resources;
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
import android.os.PowerManager;
import android.provider.Settings;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v7.app.ActionBar;
import android.support.v7.app.ActionBar.Tab;
import android.support.v7.app.ActionBar.TabListener;
import android.support.v7.app.ActionBarActivity;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.WindowManager;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.Toast;

public final class MainActivity extends ActionBarActivity implements TabListener {
//*** state that is retained ***
  public static class State {
    DatabaseHelper dbHelper;
    ServiceConnection serviceConnection;
    AtomicBoolean finishing;
    AtomicBoolean transferring;
    MediaPlayer soundPop;
    MediaPlayer soundNewPop;
    WifiLock wifiLock;
    GPSListener gpsListener;
    WifiReceiver wifiReceiver;
    NumberFormat numberFormat0;
    NumberFormat numberFormat1;
    NumberFormat numberFormat8;
    TTS tts;
    boolean inEmulator;
    PhoneState phoneState;
    FileUploaderTask fileUploaderTask;
    NetworkListAdapter listAdapter;
    String previousStatus;
    int currentTab;
  }
  private State state;
  // *** end of state that is retained ***

  static final Locale ORIG_LOCALE = Locale.getDefault();
  public static final String FILE_POST_URL = "https://wigle.net/gps/gps/main/confirmfile/";
  public static final String OBSERVED_URL = "https://wigle.net/gps/gps/main/myobserved/";
  private static final String LOG_TAG = "wigle";
  public static final String ENCODING = "ISO-8859-1";

  static final String ERROR_STACK_FILENAME = "errorstack";
  static final String ERROR_REPORT_DO_EMAIL = "doEmail";
  static final String ERROR_REPORT_DIALOG = "doDialog";

  public static final long DEFAULT_SPEECH_PERIOD = 60L;
  public static final long DEFAULT_RESET_WIFI_PERIOD = 90000L;
  public static final long LOCATION_UPDATE_INTERVAL = 1000L;
  public static final long SCAN_STILL_DEFAULT = 3000L;
  public static final long SCAN_DEFAULT = 2000L;
  public static final long SCAN_FAST_DEFAULT = 1000L;
  public static final long DEFAULT_BATTERY_KILL_PERCENT = 2L;

  private static MainActivity mainActivity;
  private static ListActivity listActivity;
  private boolean screenLocked = false;
  private PowerManager.WakeLock wakeLock;
  private final List<Fragment> fragList = new ArrayList<Fragment>();
  private BatteryLevelReceiver batteryLevelReceiver;

  private static final String STATE_FRAGMENT_TAG = "StateFragmentTag";
  private static final String LIST_FRAGMENT_TAG = "ListFragmentTag";
  private static final String MAP_FRAGMENT_TAG = "MapFragmentTag";
  private static final String DASH_FRAGMENT_TAG = "DashFragmentTag";
  private static final String DATA_FRAGMENT_TAG = "DataFragmentTag";

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    info("MAIN onCreate. state:  " + state);
    // set language
    setLocale( this );
    setContentView(R.layout.main);

    PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
    wakeLock = pm.newWakeLock(PowerManager.FULL_WAKE_LOCK, "DoNotDimScreen");
    if ( wakeLock.isHeld() ) {
      wakeLock.release();
    }

    mainActivity = this;

    // set language
    setLocale( this );

    if ( ListActivity.DEBUG ) {
      Debug.startMethodTracing("wigle");
    }

    // do some of our own error handling, write a file with the stack
    final UncaughtExceptionHandler origHandler = Thread.getDefaultUncaughtExceptionHandler();
    if ( ! (origHandler instanceof WigleUncaughtExceptionHandler) ) {
      Thread.setDefaultUncaughtExceptionHandler(
          new WigleUncaughtExceptionHandler( getApplicationContext(), origHandler ) );
    }

    // test the error reporting
    // if( true ){ throw new RuntimeException( "weee" ); }

    final FragmentManager fm = getSupportFragmentManager();
    // force the retained fragments to live
    fm.executePendingTransactions();
    StateFragment stateFragment = (StateFragment) fm.findFragmentByTag(STATE_FRAGMENT_TAG);

    if (stateFragment != null && stateFragment.getState() != null) {
      info("MAIN: using retained stateFragment state");
      // pry an orientation change, which calls destroy, but we get this from retained fragment
      state = stateFragment.getState();

      // tell those that need it that we have a new context
      state.gpsListener.setMainActivity( this );
      state.wifiReceiver.setMainActivity( this );
      if ( state.fileUploaderTask != null ) {
        state.fileUploaderTask.setContext( this );
      }


    }
    else {
      info("MAIN: creating new state");
      state = new State();
      state.finishing = new AtomicBoolean( false );
      state.transferring = new AtomicBoolean( false );

      // set it up for retain
      stateFragment = new StateFragment();
      stateFragment.setState(state);
      fm.beginTransaction().add(stateFragment, STATE_FRAGMENT_TAG).commit();
      // new run, reset
      final SharedPreferences prefs = getSharedPreferences( ListActivity.SHARED_PREFS, 0 );
      final float prevRun = prefs.getFloat( ListActivity.PREF_DISTANCE_RUN, 0f );
      Editor edit = prefs.edit();
      edit.putFloat( ListActivity.PREF_DISTANCE_RUN, 0f );
      edit.putFloat( ListActivity.PREF_DISTANCE_PREV_RUN, prevRun );
      edit.commit();
    }

    final String id = Settings.Secure.getString( getContentResolver(), Settings.Secure.ANDROID_ID );

    // DO NOT turn these into |=, they will cause older dalvik verifiers to freak out
    state.inEmulator = id == null;
    state.inEmulator =  state.inEmulator || "sdk".equals( android.os.Build.PRODUCT );
    state.inEmulator = state.inEmulator || "google_sdk".equals( android.os.Build.PRODUCT );

    info( "id: '" + id + "' inEmulator: " + state.inEmulator + " product: " + android.os.Build.PRODUCT );
    info( "android release: '" + Build.VERSION.RELEASE + "' debug: " + ListActivity.DEBUG );

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
    info( "setupBattery" );
    setupBattery();
    info( "setupSound" );
    setupSound();
    info( "setupWifi" );
    setupWifi();
    info( "setupLocation" ); // must be after setupWifi
    setupLocation();
    info( "setup tabs" );
    setupTabs(state.currentTab);
    info( "onCreate setup complete" );
  }

  private void setupTabs(int defaultTab) {
    final ActionBar bar = getSupportActionBar();
    bar.setNavigationMode(ActionBar.NAVIGATION_MODE_TABS);

    final String[] labels = new String[]{
        "List", "Map", "Dash", "Data"
    };

    info("Creating ListActivity");
    listActivity = new ListActivity();
    Bundle bundle = new Bundle();
    listActivity.setArguments(bundle);
    fragList.add(listActivity);

    info("Creating MappingActivity");
    final MappingActivity map = new MappingActivity();
    bundle = new Bundle();
    map.setArguments(bundle);
    fragList.add(map);

    info("Creating DashboardActivity");
    DashboardActivity dash = new DashboardActivity();
    bundle = new Bundle();
    dash.setArguments(bundle);
    fragList.add(dash);

    info("Creating DataActivity");
    DataActivity data = new DataActivity();
    bundle = new Bundle();
    data.setArguments(bundle);
    fragList.add(data);

    for (int i = 0; i <= 3; i++) {
      Tab tab = bar.newTab();
      tab.setText(labels[i]);
      tab.setTabListener(this);
      bar.addTab(tab);
    }

    info("setting tab: " + defaultTab);
    bar.setSelectedNavigationItem(defaultTab);
  }

  // be careful with this
  public static MainActivity getMainActivity() {
    return mainActivity;
  }

  /**
   * switch to the specified tab using the activity's parent, if possible
   * @param activity
   * @param tab
   */
  static void switchTab( Activity activity, String tab ) {
    final Activity parent = activity.getParent();
    // XXX: fix
//    if ( parent != null && parent instanceof TabActivity ) {
//      ((TabActivity) parent).getTabHost().setCurrentTabByTag( tab );
//    }
  }

  static void setLockScreen( Activity activity, boolean lockScreen ) {
    final Activity parent = activity.getParent();
    if ( parent != null && parent instanceof MainActivity ) {
      MainActivity main = (MainActivity) parent;
      main.setLockScreen( lockScreen );
    }
  }

  static boolean isScreenLocked( Activity activity ) {
    final Activity parent = activity.getParent();
    if ( parent != null && parent instanceof MainActivity ) {
      MainActivity main = (MainActivity) parent;
      return main.screenLocked;
    }
    return false;
  }

  @SuppressLint("Wakelock")
  private void setLockScreen( boolean lockScreen ) {
    this.screenLocked = lockScreen;
    if ( lockScreen ) {
      if ( ! wakeLock.isHeld() ) {
        MainActivity.info("acquire wake lock");
        wakeLock.acquire();
      }
    }
    else if ( wakeLock.isHeld() ) {
      MainActivity.info("release wake lock");
      wakeLock.release();
    }
  }

  public static interface Doer {
    public void execute();
  }

  static void createConfirmation( final Activity activity, final String message, final Doer doer ) {
    final AlertDialog.Builder builder = new AlertDialog.Builder( activity );
    builder.setCancelable( true );
    builder.setTitle( "Confirmation" );
    builder.setMessage( message );
    AlertDialog ad = builder.create();
    // ok
    ad.setButton( DialogInterface.BUTTON_POSITIVE, activity.getString(R.string.ok), new DialogInterface.OnClickListener() {
      @Override
      public void onClick( final DialogInterface dialog, final int which ) {
        try {
          dialog.dismiss();
          doer.execute();
        }
        catch ( Exception ex ) {
          // guess it wasn't there anyways
          MainActivity.info( "exception dismissing alert dialog: " + ex );
        }
        return;
      } });

    // cancel
    ad.setButton( DialogInterface.BUTTON_NEGATIVE, activity.getString(R.string.cancel), new DialogInterface.OnClickListener() {
      @Override
      public void onClick( final DialogInterface dialog, final int which ) {
        try {
          dialog.dismiss();
        }
        catch ( Exception ex ) {
          // guess it wasn't there anyways
          MainActivity.info( "exception dismissing alert dialog: " + ex );
        }
        return;
      } });

    try {
      ad.show();
    }
    catch ( WindowManager.BadTokenException ex ) {
      MainActivity.info( "exception showing dialog, view probably changed: " + ex, ex );
    }
  }

  private void setupDatabase() {
    // could be set by nonconfig retain
    if ( state.dbHelper == null ) {
      state.dbHelper = new DatabaseHelper( getApplicationContext() );
      //state.dbHelper.checkDB();
      state.dbHelper.start();
      ListActivity.lameStatic.dbHelper = state.dbHelper;
    }
  }

  public static CheckBox prefSetCheckBox( final Context context, final Dialog dialog, final int id,
      final String pref, final boolean def ) {

    final SharedPreferences prefs = context.getSharedPreferences( ListActivity.SHARED_PREFS, 0);
    final CheckBox checkbox = (CheckBox) dialog.findViewById( id );
    checkbox.setChecked( prefs.getBoolean( pref, def ) );
    return checkbox;
  }

  public static CheckBox prefSetCheckBox( final Activity activity, final int id, final String pref, final boolean def ) {
    final SharedPreferences prefs = activity.getSharedPreferences( ListActivity.SHARED_PREFS, 0);
    final CheckBox checkbox = (CheckBox) activity.findViewById( id );
    checkbox.setChecked( prefs.getBoolean( pref, def ) );
    return checkbox;
  }

  public static CheckBox prefBackedCheckBox( final Activity activity, final int id, final String pref, final boolean def ) {
    final SharedPreferences prefs = activity.getSharedPreferences( ListActivity.SHARED_PREFS, 0);
    final Editor editor = prefs.edit();
    final CheckBox checkbox = prefSetCheckBox( activity, id, pref, def );
    checkbox.setOnCheckedChangeListener( new OnCheckedChangeListener() {
      @Override
      public void onCheckedChanged( final CompoundButton buttonView, final boolean isChecked ) {
            editor.putBoolean( pref, isChecked );
            editor.commit();
        }
    });

    return checkbox;
  }

  public static State getState( Fragment fragment ) {
    return getMainActivity().getState();
  }

  public State getState() {
    return state;
  }

  static MainActivity getMainActivity( Fragment fragment ) {
    final Activity activity = fragment.getActivity();
  	if (activity instanceof MainActivity) {
        return (MainActivity) activity;
  	}
  	else {
  		info("not main activity: " + activity);
  	}
    return null;
  }

  /** safely get the canonical path, as this call throws exceptions on some devices */
  public static String safeFilePath( final File file ) {
    String retval = null;
    try {
      retval = file.getCanonicalPath();
    }
    catch ( Exception ex ) {
      // ignore
    }

    if ( retval == null ) {
      retval = file.getAbsolutePath();
    }
    return retval;
  }

  @Override
  public void onDestroy() {
    MainActivity.info( "MAIN: destroy." );
    super.onDestroy();

    try {
      info("unregister batteryLevelReceiver");
      unregisterReceiver( batteryLevelReceiver );
    }
    catch ( final IllegalArgumentException ex ) {
      info( "batteryLevelReceiver not registered: " + ex );
    }

    try {
      info("unregister wifiReceiver");
      unregisterReceiver( state.wifiReceiver );
    }
    catch ( final IllegalArgumentException ex ) {
      info( "wifiReceiver not registered: " + ex );
    }
  }

  @Override
  public void onPause() {
    MainActivity.info( "MAIN: pause." );
    super.onPause();

    // deal with wake lock
    if ( wakeLock.isHeld() ) {
      MainActivity.info("release wake lock");
      wakeLock.release();
    }
  }

  @Override
  public void onResume() {
    MainActivity.info( "MAIN: resume." );
    super.onResume();

    // deal with wake lock
    if ( ! wakeLock.isHeld() && screenLocked ) {
      MainActivity.info("acquire wake lock");
      wakeLock.acquire();
    }
  }

  @Override
  public void onConfigurationChanged( final Configuration newConfig ) {
    MainActivity.info( "MAIN: config changed" );
    setLocale( this, newConfig );
    super.onConfigurationChanged( newConfig );
  }

  @Override
  public void onStart() {
    MainActivity.info( "MAIN: start." );
    super.onStart();
  }

  @Override
  public void onStop() {
    MainActivity.info( "MAIN: stop." );
    super.onStop();
  }

  @Override
  public void onRestart() {
    MainActivity.info( "MAIN: restart." );
    super.onRestart();
  }

  public static Throwable getBaseThrowable(final Throwable throwable) {
    Throwable retval = throwable;
    while ( retval.getCause() != null ) {
      retval = retval.getCause();
    }
    return retval;
  }

  public static String getBaseErrorMessage(Throwable throwable, final boolean withNewLine) {
    throwable = MainActivity.getBaseThrowable( throwable );
    final String newline = withNewLine ? "\n" : " ";
    return throwable.getClass().getSimpleName() + ":" + newline + throwable.getMessage();
  }

  public static void setLocale( final Activity activity ) {
  final Context context = activity.getBaseContext();
    final Configuration config = context.getResources().getConfiguration();
    setLocale( context, config );
  }

  public static void setLocale( final Context context, final Configuration config ) {
    final SharedPreferences prefs = context.getSharedPreferences( ListActivity.SHARED_PREFS, 0 );
    final String lang = prefs.getString( ListActivity.PREF_LANGUAGE, "" );
    final String current = config.locale.getLanguage();
    MainActivity.info("current lang: " + current + " new lang: " + lang);
    Locale newLocale = null;
    if (! "".equals(lang) && ! current.equals(lang) && lang != null) {
      newLocale = new Locale(lang);
    }
    else if ("".equals(lang) && ORIG_LOCALE != null && ! current.equals(ORIG_LOCALE.getLanguage()) ) {
      newLocale = ORIG_LOCALE;
    }

    if ( newLocale != null ) {
      Locale.setDefault(newLocale);
      config.locale = newLocale;
      MainActivity.info("setting locale: " + newLocale);
      context.getResources().updateConfiguration(config, context.getResources().getDisplayMetrics());
    }
  }

  @Override
  public void onTabReselected(Tab tab, FragmentTransaction ft) {
    MainActivity.info("onTabReselected: " + tab.getPosition());
    Fragment f = fragList.get(tab.getPosition());
    ft.replace(android.R.id.content, f);
    state.currentTab = tab.getPosition();
  }

  @Override
  public void onTabSelected(Tab tab, FragmentTransaction ft) {
    MainActivity.info("onTabSelected: " + tab.getPosition());
    Fragment f = fragList.get(tab.getPosition());
    ft.replace(android.R.id.content, f);
    state.currentTab = tab.getPosition();
  }

  @Override
  public void onTabUnselected(Tab tab, FragmentTransaction ft) {
    MainActivity.info("onTabUnselected: " + tab.getPosition());
    ft.remove(fragList.get(tab.getPosition()));
  }


  /**
   * create a mediaplayer for a given raw resource id.
   * @param soundId the R.raw. id for a given sound
   * @return the mediaplayer for soundId or null if it could not be created.
   */
  private MediaPlayer createMediaPlayer( final int soundId ) {
    final MediaPlayer sound = createMp( getApplicationContext(), soundId );
    if ( sound == null ) {
      info( "sound null from media player" );
      return null;
    }
    // try to figure out why sounds stops after a while
    sound.setOnErrorListener( new OnErrorListener() {
      @Override
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
    boolean retval = getSharedPreferences(ListActivity.SHARED_PREFS, 0).getBoolean(ListActivity.PREF_MUTED, false);
    // info( "ismuted: " + retval );
    return retval;
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
    return ListActivity.networkCache.get();
  }

  public static void writeError( final Thread thread, final Throwable throwable, final Context context ) {
    writeError(thread, throwable, context, null);
  }
  public static void writeError( final Thread thread, final Throwable throwable, final Context context, final String detail ) {
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
          final DateFormat format = SimpleDateFormat.getDateTimeInstance();
          builder.append( format.format( new Date() ) ).append( "\n" );
          final PackageManager pm = context.getPackageManager();
          final PackageInfo pi = pm.getPackageInfo(context.getPackageName(), 0);
          builder.append( "versionName: " ).append( pi.versionName ).append( "\n" );
          builder.append( "baseError: " ).append( baseErrorMessage ).append( "\n\n" );
          if (detail != null) {
            builder.append( "detail: " ).append( detail ).append( "\n" );
          }
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

  private void setupSound() {
    // could have been retained
    if ( state.soundPop == null ) {
      state.soundPop = createMediaPlayer( R.raw.pop );
    }
    if ( state.soundNewPop == null ) {
      state.soundNewPop = createMediaPlayer( R.raw.newpop );
    }

    // make volume change "media"
    setVolumeControlStream( AudioManager.STREAM_MUSIC );

    try {
      if ( TTS.hasTTS() ) {
        // don't reuse an old one, has to be on *this* context
        if ( state.tts != null ) {
          state.tts.shutdown();
        }
        // this has to have the parent activity, for whatever wacky reasons
        state.tts = new TTS( this );
      }
    }
    catch ( Exception ex ) {
      error( "exception setting TTS: " + ex, ex);
    }

    TelephonyManager tele = (TelephonyManager) getSystemService( Context.TELEPHONY_SERVICE );
    if ( tele != null && state.phoneState == null ) {
      state.phoneState = PhoneStateFactory.createPhoneState();
      final int signal_strengths = 256;
      try {
        tele.listen( state.phoneState, PhoneStateListener.LISTEN_SERVICE_STATE
          | PhoneStateListener.LISTEN_CALL_STATE | PhoneStateListener.LISTEN_SIGNAL_STRENGTH | signal_strengths );
      }
      catch (SecurityException ex) {
        info("cannot get call state, will play audio over any telephone calls: " + ex);
      }
    }
  }

  public boolean inEmulator() {
    return state.inEmulator;
  }

  public DatabaseHelper getDBHelper() {
    return state.dbHelper;
  }

  public BatteryLevelReceiver getBatteryLevelReceiver() {
    return batteryLevelReceiver;
  }

  public GPSListener getGPSListener() {
    return state.gpsListener;
  }

  public PhoneState getPhoneState() {
    return state.phoneState;
  }

  @Override
  public boolean isFinishing() {
    return state.finishing.get();
  }

  public boolean isTransferring() {
    return state.transferring.get();
  }

  public boolean isScanning() {
    return isScanning(this);
  }

  public static boolean isScanning(final Context context) {
    final SharedPreferences prefs = context.getSharedPreferences( ListActivity.SHARED_PREFS, 0 );
    return prefs.getBoolean( ListActivity.PREF_SCAN_RUNNING, true );
  }

  public void playNewNetSound() {
    try {
      if ( state.soundNewPop != null && ! state.soundNewPop.isPlaying() ) {
        // play sound on something new
        state.soundNewPop.start();
      }
      else {
        MainActivity.info( "soundNewPop is playing or null" );
      }
    }
    catch ( IllegalStateException ex ) {
      // ohwell, likely already playing
      MainActivity.info( "exception trying to play sound: " + ex );
    }
  }

  public void playRunNetSound() {
    try {
      if ( state.soundPop != null && ! state.soundPop.isPlaying() ) {
        // play sound on something new
        state.soundPop.start();
      }
      else {
        MainActivity.info( "soundPop is playing or null" );
      }
    }
    catch ( IllegalStateException ex ) {
      // ohwell, likely already playing
      MainActivity.info( "exception trying to play sound: " + ex );
    }
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
    final SharedPreferences prefs = getSharedPreferences( ListActivity.SHARED_PREFS, 0 );
    final Editor edit = prefs.edit();

    // keep track of for later
    boolean turnedWifiOn = false;
    if ( ! wifiManager.isWifiEnabled() ) {
      // tell user, cuz this takes a little while
      Toast.makeText( this, getString(R.string.turn_on_wifi), Toast.LENGTH_LONG ).show();

      // save so we can turn it back off when we exit
      edit.putBoolean( ListActivity.PREF_WIFI_WAS_OFF, true );

      // just turn it on, but not in emulator cuz it crashes it
      if ( ! state.inEmulator ) {
        MainActivity.info( "turning on wifi");
        wifiManager.setWifiEnabled( true );
        MainActivity.info( "wifi on");
        turnedWifiOn = true;
      }
    }
    else {
      edit.putBoolean( ListActivity.PREF_WIFI_WAS_OFF, false );
    }
    edit.commit();

    if ( state.wifiReceiver == null ) {
      MainActivity.info( "new wifiReceiver");
      // wifi scan listener
      // this receiver is the main workhorse of the entire app
      state.wifiReceiver = new WifiReceiver( this, state.dbHelper );
      state.wifiReceiver.setupWifiTimer( turnedWifiOn );
    }

    // register wifi receiver
    setupWifiReceiverIntent();

    if ( state.wifiLock == null ) {
      MainActivity.info( "lock wifi radio on");
      // lock the radio on
      state.wifiLock = wifiManager.createWifiLock( WifiManager.WIFI_MODE_SCAN_ONLY, ListActivity.WIFI_LOCK_NAME );
      state.wifiLock.acquire();
    }
  }

  private void setupWifiReceiverIntent() {
    // register
    MainActivity.info( "register BroadcastReceiver");
    final IntentFilter intentFilter = new IntentFilter();
    intentFilter.addAction( WifiManager.SCAN_RESULTS_AVAILABLE_ACTION );
    registerReceiver( state.wifiReceiver, intentFilter );
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

  public void setTransferring() {
    state.transferring.set( true );
  }

  public void scheduleScan() {
    state.wifiReceiver.scheduleScan();
  }

  public void speak( final String string ) {
    if ( ! MainActivity.getMainActivity().isMuted() && state.tts != null ) {
      state.tts.speak( string );
    }
  }

  public void interruptSpeak() {
    if ( state.tts != null ) {
      state.tts.stop();
    }
  }

  private void setupService() {
    // could be set by nonconfig retain
    if ( state.serviceConnection == null ) {
      final Intent serviceIntent = new Intent( getApplicationContext(), WigleService.class );
      final ComponentName compName = startService( serviceIntent );
      if ( compName == null ) {
        MainActivity.error( "startService() failed!" );
      }
      else {
        MainActivity.info( "service started ok: " + compName );
      }

      state.serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected( final ComponentName name, final IBinder iBinder ) {
          MainActivity.info( name + " service connected" );
        }
        @Override
        public void onServiceDisconnected( final ComponentName name ) {
          MainActivity.info( name + " service disconnected" );
        }
      };

      int flags = 0;
      // have to use the app context to bind to the service, cuz we're in tabs
      // http://code.google.com/p/android/issues/detail?id=2483#c2
      final boolean bound = getApplicationContext().bindService( serviceIntent, state.serviceConnection, flags );
      MainActivity.info( "service bound: " + bound );
    }
  }

  private void setupLocation() {
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
        MainActivity.error("exception trying to start location activity: " + ex, ex);
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

  private void handleScanChange() {
    final boolean isScanning = isScanning();
    MainActivity.info("handleScanChange: isScanning now: " + isScanning );
    if ( isScanning ) {
      // turn on location updates
      this.setLocationUpdates(MainActivity.LOCATION_UPDATE_INTERVAL, 0f);

      if ( ! state.wifiLock.isHeld() ){
        state.wifiLock.acquire();
      }
    }
    else {
      // turn off location updates
      this.setLocationUpdates(0L, 0f);
      state.gpsListener.handleScanStop();
      if ( state.wifiLock.isHeld() ){
        try {
          state.wifiLock.release();
        }
        catch (SecurityException ex) {
          // a case where we have a leftover lock from another run?
          MainActivity.info("exception releasing wifilock: " + ex);
        }
      }
    }

    // XXX: tell fragment about scan change
    // setStatusUI( view, "Scanning Turned On" );
    // setStatusUI( getView(), "Scanning Turned Off" );
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
    state.gpsListener.setMapListener(MappingActivity.STATIC_LOCATION_LISTENER);
    locationManager.addGpsStatusListener( state.gpsListener );

    final SharedPreferences prefs = getSharedPreferences( ListActivity.SHARED_PREFS, 0 );
    final boolean useNetworkLoc = prefs.getBoolean(ListActivity.PREF_USE_NETWORK_LOC, false);

    final List<String> providers = locationManager.getAllProviders();
    if (providers != null) {
      for ( String provider : providers ) {
        MainActivity.info( "available provider: " + provider + " updateIntervalMillis: " + updateIntervalMillis );
        if ( ! useNetworkLoc && LocationManager.NETWORK_PROVIDER.equals(provider)) {
          // skip!
          continue;
        }
        if ( ! "passive".equals( provider ) && updateIntervalMillis > 0 ) {
          MainActivity.info("using provider: " + provider);
          locationManager.requestLocationUpdates( provider, updateIntervalMillis, updateMeters, state.gpsListener );
        }
      }
    }
  }

  public long getLocationSetPeriod() {
    final SharedPreferences prefs = getSharedPreferences( ListActivity.SHARED_PREFS, 0 );
    final long prefPeriod = prefs.getLong(ListActivity.GPS_SCAN_PERIOD, MainActivity.LOCATION_UPDATE_INTERVAL);
    long setPeriod = prefPeriod;
    if (setPeriod == 0 ){
      setPeriod = Math.max(state.wifiReceiver.getScanPeriod(), MainActivity.LOCATION_UPDATE_INTERVAL);
    }
    return setPeriod;
  }

  public void setLocationUpdates() {
    final long setPeriod = getLocationSetPeriod();
    setLocationUpdates(setPeriod, 0f);
  }

  /**
   * FileUploaderListener interface
   */
  public void transferComplete() {
    state.transferring.set( false );
    MainActivity.info( "transfer complete" );
    // start a scan to get the ball rolling again if this is non-stop mode
    scheduleScan();
    state.fileUploaderTask = null;
  }

  public void setLocationUI() {
    // tell list about new location
    listActivity.setLocationUI( this );
  }

  public void setNetCountUI() {
    // tell list
    listActivity.setNetCountUI( getState() );
  }

  public void setStatusUI( String status ) {
	if ( status == null ) {
      status = state.previousStatus;
    }
    if ( status != null ) {
      // keep around a previous, for orientation changes
      state.previousStatus = status;
      // tell list
      listActivity.setStatusUI( status );
    }
  }

  @Override
  public boolean onCreateOptionsMenu( final Menu menu ) {
    info("MAIN: onCreateOptionsMenu.");
    return true;
  }

  @Override
  public boolean onOptionsItemSelected( final MenuItem item ) {
    return false;
  }

  //@Override
  @Override
  public void finish() {
    info( "MAIN: finish. networks: " + state.wifiReceiver.getRunNetworkCount() );

    final boolean wasFinishing = state.finishing.getAndSet( true );
    if ( wasFinishing ) {
      info( "MAIN: finish called twice!" );
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

    // stop the service, so when we die it's both stopped and unbound and will die
    final Intent serviceIntent = new Intent( this, WigleService.class );
    stopService( serviceIntent );
    try {
      // have to use the app context to bind to the service, cuz we're in tabs
      getApplicationContext().unbindService( state.serviceConnection );
    }
    catch ( final IllegalArgumentException ex ) {
      MainActivity.info( "serviceConnection not registered: " + ex, ex );
    }

    // release the lock before turning wifi off
    if ( state.wifiLock != null && state.wifiLock.isHeld() ) {
      try {
        state.wifiLock.release();
      }
      catch ( Exception ex ) {
        MainActivity.error( "exception releasing wifi lock: " + ex, ex );
      }
    }

    final SharedPreferences prefs = this.getSharedPreferences( ListActivity.SHARED_PREFS, 0 );
    final boolean wifiWasOff = prefs.getBoolean( ListActivity.PREF_WIFI_WAS_OFF, false );
    // don't call on emulator, it crashes it
    if ( wifiWasOff && ! state.inEmulator ) {
      // tell user, cuz this takes a little while
      Toast.makeText( this, getString(R.string.turning_wifi_off), Toast.LENGTH_SHORT ).show();

      // well turn it of now that we're done
      final WifiManager wifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);
      MainActivity.info( "turning back off wifi" );
      try {
        wifiManager.setWifiEnabled( false );
      }
      catch ( Exception ex ) {
        MainActivity.error("exception turning wifi back off: " + ex, ex);
      }
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

    if ( ListActivity.DEBUG ) {
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
}
