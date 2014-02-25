package net.wigle.wigleandroid;

import java.util.concurrent.atomic.AtomicBoolean;

import net.wigle.wigleandroid.listener.WifiReceiver;

import org.osmdroid.LocationListenerProxy;
import org.osmdroid.api.IGeoPoint;
import org.osmdroid.api.IMapController;
import org.osmdroid.api.IMapView;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.MyLocationOverlay;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.database.Cursor;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.RelativeLayout;
import android.widget.TextView;

/**
 * show a map!
 */
@SuppressWarnings("deprecation")
public final class MappingActivity extends Fragment {
  private static class State {
    private boolean locked = true;
    private boolean firstMove = true;
    private IGeoPoint oldCenter = null;
    private int oldZoom = Integer.MIN_VALUE;
  }
  private final State state = new State();
  
  private IMapController mapControl;
  private IMapView mapView;
  private Handler timer;
  private AtomicBoolean finishing;
  private Location previousLocation;
  private int previousRunNets;
  private MyLocationOverlay myLocationOverlay = null;
  
  public static LocationListener STATIC_LOCATION_LISTENER = null;
  
  private static final int DEFAULT_ZOOM = 17;
  public static final GeoPoint DEFAULT_POINT = new GeoPoint( 41950000, -87650000 );
  private static final int MENU_EXIT = 12;
  private static final int MENU_ZOOM_IN = 13;
  private static final int MENU_ZOOM_OUT = 14;
  private static final int MENU_TOGGLE_LOCK = 15;
  private static final int MENU_TOGGLE_NEWDB = 16;
  private static final int MENU_LABEL = 17;
  private static final int MENU_FILTER = 18;
  
  private static final int SSID_FILTER = 102;
  
  /** Called when the activity is first created. */
  @Override
  public void onCreate( final Bundle savedInstanceState ) {
    super.onCreate( savedInstanceState );
    // set language
    MainActivity.setLocale( getActivity() );
    finishing = new AtomicBoolean( false );
    
    // media volume
    getActivity().setVolumeControlStream( AudioManager.STREAM_MUSIC );  
    
    setupQuery();
  }
  
  @Override
  public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
    final View view = inflater.inflate(R.layout.map, container, false);
    
    final Object stored = getActivity().getLastNonConfigurationInstance();
    IGeoPoint oldCenter = null;
    int oldZoom = Integer.MIN_VALUE;
    if ( stored != null && stored instanceof State ) {
      // pry an orientation change, which calls destroy, but we set this in onRetainNonConfigurationInstance
      final State retained = (State) stored;
      state.locked = retained.locked;
      state.firstMove = retained.firstMove;
      oldCenter = retained.oldCenter;
      oldZoom = retained.oldZoom;
    }       
    
    setupMapView( view, oldCenter, oldZoom );
    setupTimer( view );    
    
    return view;
  }
  
//  @Override
//  public Object onRetainNonConfigurationInstance() {
//    ListActivity.info( "MappingActivity: onRetainNonConfigurationInstance" );
//    // save the map info
//    state.oldCenter = mapView.getMapCenter();
//    state.oldZoom = mapView.getZoomLevel();
//    // return state class to copy data from
//    return state;
//  }
  
  private void setupMapView( final View view, final IGeoPoint oldCenter, final int oldZoom ) {
    // view
    final RelativeLayout rlView = (RelativeLayout) view.findViewById( R.id.map_rl );

    // tryEvil();
    
    // possibly choose goog maps here
    mapView = new MapView( getActivity(), 256 );   
    
    if ( mapView instanceof View ) {
      ViewGroup.LayoutParams params = new ViewGroup.LayoutParams(
        LayoutParams.FILL_PARENT, LayoutParams.FILL_PARENT);
      ((View) mapView).setLayoutParams(params);
    }
    
    if ( mapView instanceof MapView ) {
      final MapView osmMapView = (MapView) mapView;

      // conditionally replace the tile source
      final SharedPreferences prefs = getActivity().getSharedPreferences( ListActivity.SHARED_PREFS, 0 );
      final boolean wigleTiles = prefs.getBoolean( ListActivity.PREF_USE_WIGLE_TILES, true );
      if ( wigleTiles ) { 
          osmMapView.setTileSource( WigleTileSource.WiGLE );
      }

      rlView.addView( osmMapView );
      osmMapView.setBuiltInZoomControls( true );
      osmMapView.setMultiTouchControls( true );
      
      // my location overlay
      myLocationOverlay = new MyLocationOverlay( getActivity().getApplicationContext(), osmMapView );
      myLocationOverlay.setLocationUpdateMinTime( ListActivity.LOCATION_UPDATE_INTERVAL );
      myLocationOverlay.setDrawAccuracyEnabled( false );
      osmMapView.getOverlays().add( myLocationOverlay );
      
      final OpenStreetMapViewWrapper overlay = new OpenStreetMapViewWrapper( getActivity() );
      osmMapView.getOverlays().add( overlay );
    }
    
    // controller
    mapControl = mapView.getController();
    final IGeoPoint centerPoint = getCenter( getActivity(), oldCenter, previousLocation );
    int zoom = DEFAULT_ZOOM;
    if ( oldZoom >= 0 ) {
      zoom = oldZoom;
    }
    else {
      final SharedPreferences prefs = getActivity().getSharedPreferences( ListActivity.SHARED_PREFS, 0 );
      zoom = prefs.getInt( ListActivity.PREF_PREV_ZOOM, zoom );
    }    
    mapControl.setCenter( centerPoint );
    mapControl.setZoom( zoom );
    mapControl.setCenter( centerPoint );
    
    ListActivity.info("done setupMapView. zoom: " + zoom);
  }
  
  public static IGeoPoint getCenter( final Context context, final IGeoPoint priorityCenter,
      final Location previousLocation ) {
    
    IGeoPoint centerPoint = DEFAULT_POINT;
    final Location location = ListActivity.lameStatic.location;
    final SharedPreferences prefs = context.getSharedPreferences( ListActivity.SHARED_PREFS, 0 );
    
    if ( priorityCenter != null ) {
      centerPoint = priorityCenter;
    }
    else if ( location != null ) {
      centerPoint = new GeoPoint( location );
    }
    else if ( previousLocation != null ) {
      centerPoint = new GeoPoint( previousLocation );
    }
    else {
      final Location gpsLocation = safelyGetLast(context, LocationManager.GPS_PROVIDER);
      final Location networkLocation = safelyGetLast(context, LocationManager.NETWORK_PROVIDER);
      
      if ( gpsLocation != null ) {
        centerPoint = new GeoPoint( gpsLocation );
      }
      else if ( networkLocation != null ) {
        centerPoint = new GeoPoint( networkLocation );
      }
      else {      
        // ok, try the saved prefs
        float lat = prefs.getFloat( ListActivity.PREF_PREV_LAT, Float.MIN_VALUE );
        float lon = prefs.getFloat( ListActivity.PREF_PREV_LON, Float.MIN_VALUE );
        if ( lat != Float.MIN_VALUE && lon != Float.MIN_VALUE ) {
          centerPoint = new GeoPoint( lat, lon );
        }
      }    
    }
    
    return centerPoint;
  }
  
  private static Location safelyGetLast( final Context context, final String provider ) {
    Location retval = null;
    try {
      final LocationManager locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
      retval = locationManager.getLastKnownLocation( provider );
    }
    catch ( IllegalArgumentException ex ) {
      ListActivity.info("exception getting last known location: " + ex);
    }
    return retval;
  }
  
  private void setupTimer( final View view ) {
    if ( timer == null ) {
      timer = new Handler();
      final Runnable mUpdateTimeTask = new Runnable() {
        public void run() {              
            // make sure the app isn't trying to finish
            if ( ! finishing.get() ) {
              final Location location = ListActivity.lameStatic.location;
              if ( location != null ) {
                if ( state.locked ) {
                  // ListActivity.info( "mapping center location: " + location );
  								final GeoPoint locGeoPoint = new GeoPoint( location );
  								if ( state.firstMove ) {
  								  mapControl.setCenter( locGeoPoint );
  								  state.firstMove = false;
  								}
  								else {
  								  mapControl.animateTo( locGeoPoint );
  								}
                }
                else if ( previousLocation == null || previousLocation.getLatitude() != location.getLatitude() 
                    || previousLocation.getLongitude() != location.getLongitude() 
                    || previousRunNets != ListActivity.lameStatic.runNets) {
                  // location or nets have changed, update the view
                  if ( mapView instanceof View ) {
                    ((View) mapView).postInvalidate();
                  }
                }
                // set if location isn't null
                previousLocation = location;
              }
              
              previousRunNets = ListActivity.lameStatic.runNets;
              
              TextView tv = (TextView) view.findViewById( R.id.stats_run );
              tv.setText( getString(R.string.run) + ": " + ListActivity.lameStatic.runNets );
              tv = (TextView) view.findViewById( R.id.stats_new );
              tv.setText( getString(R.string.new_word) + ": " + ListActivity.lameStatic.newNets );
              tv = (TextView) view.findViewById( R.id.stats_dbnets );
              tv.setText( getString(R.string.db) + ": " + ListActivity.lameStatic.dbNets );
              
              final long period = 1000L;
              // info("wifitimer: " + period );
              timer.postDelayed( this, period );
            }
            else {
              ListActivity.info( "finishing mapping timer" );
            }
        }
      };
      timer.removeCallbacks( mUpdateTimeTask );
      timer.postDelayed( mUpdateTimeTask, 100 );
    }
  }
  
// XXX   
//  @Override
//  public void finish() {
//    ListActivity.info( "finish mapping." );
//    finishing.set( true );
//    
//    super.finish();
//  }
  
  @Override
  public void onDestroy() {
    ListActivity.info( "destroy mapping." );
    finishing.set( true );
    
    // save zoom
    final SharedPreferences prefs = getActivity().getSharedPreferences( ListActivity.SHARED_PREFS, 0 );
    final Editor edit = prefs.edit();
    edit.putInt( ListActivity.PREF_PREV_ZOOM, mapView.getZoomLevel() );
    edit.commit();
    
    super.onDestroy();
  }
  
  @Override
  public void onPause() {
    ListActivity.info( "pause mapping." );
    myLocationOverlay.disableCompass();
    disableLocation();
    
    super.onPause();
  }
  
  @Override
  public void onResume() {
    ListActivity.info( "resume mapping." );
    myLocationOverlay.enableCompass();    
    enableLocation();
    
    super.onResume();
  }
  
  private void disableLocation() {
    myLocationOverlay.mLocationListener = null;

    // Update the screen to see changes take effect
    if ( mapView instanceof View ) {
      ((View) mapView).postInvalidate();
    }
  }
  
  private void enableLocation() {
    try {
      // force it to think it's own location listening is on
      myLocationOverlay.mLocationListener = new LocationListenerProxy(null);
      STATIC_LOCATION_LISTENER = myLocationOverlay;
      MainActivity.getListActivity(getActivity()).getGPSListener().setMapListener(myLocationOverlay);
      myLocationOverlay.enableMyLocation();
    }
    catch (Exception ex) {
      ListActivity.error("Could not enableLocation for maps: " + ex, ex);
    }
  }
  
//  XXX
//  /* Creates the menu items */
//  @Override
//  public boolean onCreateOptionsMenu( final Menu menu ) {
//    MenuItem item = null;
//    final SharedPreferences prefs = this.getSharedPreferences( ListActivity.SHARED_PREFS, 0 );
//    final boolean showNewDBOnly = prefs.getBoolean( ListActivity.PREF_MAP_ONLY_NEWDB, false );
//    final boolean showLabel = prefs.getBoolean( ListActivity.PREF_MAP_LABEL, false );
//    
//    String name = state.locked ? getString(R.string.menu_turn_off_lockon) : getString(R.string.menu_turn_on_lockon);
//    item = menu.add(0, MENU_TOGGLE_LOCK, 0, name);
//    item.setIcon( android.R.drawable.ic_menu_mapmode );
//        
//    String nameDB = showNewDBOnly ? getString(R.string.menu_show_old) : getString(R.string.menu_show_new);
//    item = menu.add(0, MENU_TOGGLE_NEWDB, 0, nameDB);
//    item.setIcon( android.R.drawable.ic_menu_edit );
//    
//    String nameLabel = showLabel ? getString(R.string.menu_labels_off) : getString(R.string.menu_labels_on);
//    item = menu.add(0, MENU_LABEL, 0, nameLabel);
//    item.setIcon( android.R.drawable.ic_dialog_info );
//    
//    item = menu.add(0, MENU_EXIT, 0, getString(R.string.menu_exit));
//    item.setIcon( android.R.drawable.ic_menu_close_clear_cancel );    
//    
//    item = menu.add(0, MENU_FILTER, 0, getString(R.string.menu_ssid_filter));
//    item.setIcon( android.R.drawable.ic_menu_search );
//    
//    item = menu.add(0, MENU_ZOOM_IN, 0, getString(R.string.menu_zoom_in));
//    item.setIcon( android.R.drawable.ic_menu_add );
//    
//    item = menu.add(0, MENU_ZOOM_OUT, 0, getString(R.string.menu_zoom_out));
//    item.setIcon( android.R.drawable.ic_menu_revert );
//    
//    
//    return true;
//  }

  /* Handles item selections */
  @Override
  public boolean onOptionsItemSelected( final MenuItem item ) {
      switch ( item.getItemId() ) {
        case MENU_EXIT: {
          MainActivity.finishListActivity( getActivity() );
//          finish();  XXX
          return true;
        }
        case MENU_ZOOM_IN: {
          int zoom = mapView.getZoomLevel();
          zoom++;
          mapControl.setZoom( zoom );
          return true;
        }
        case MENU_ZOOM_OUT: {
          int zoom = mapView.getZoomLevel();
          zoom--;
          mapControl.setZoom( zoom );
          return true;
        }
        case MENU_TOGGLE_LOCK: {
          state.locked = ! state.locked;
          String name = state.locked ? getString(R.string.menu_turn_off_lockon) : getString(R.string.menu_turn_on_lockon);
          item.setTitle( name );
          return true;
        }
        case MENU_TOGGLE_NEWDB: {
          final SharedPreferences prefs = getActivity().getSharedPreferences( ListActivity.SHARED_PREFS, 0 );
          final boolean showNewDBOnly = ! prefs.getBoolean( ListActivity.PREF_MAP_ONLY_NEWDB, false );
          Editor edit = prefs.edit();
          edit.putBoolean( ListActivity.PREF_MAP_ONLY_NEWDB, showNewDBOnly );
          edit.commit();
          
          String name = showNewDBOnly ? getString(R.string.menu_show_old) : getString(R.string.menu_show_new);
          item.setTitle( name );
          return true;
        }
        case MENU_LABEL: {
          final SharedPreferences prefs = getActivity().getSharedPreferences( ListActivity.SHARED_PREFS, 0 );
          final boolean showLabel = ! prefs.getBoolean( ListActivity.PREF_MAP_LABEL, true );
          Editor edit = prefs.edit();
          edit.putBoolean( ListActivity.PREF_MAP_LABEL, showLabel );
          edit.commit();
          
          String name = showLabel ? getString(R.string.menu_labels_off) : getString(R.string.menu_labels_on);
          item.setTitle( name );
          return true;
        }
        case MENU_FILTER: {
//          showDialog( SSID_FILTER );  XXX
          return true;
        }
      }
      return false;
  }
  
  
//  XXX
//  @Override
//  public boolean onKeyDown(int keyCode, KeyEvent event) {
//    if (keyCode == KeyEvent.KEYCODE_BACK) {
//      ListActivity.info( "onKeyDown: not quitting app on back" );
//      MainActivity.switchTab( this, MainActivity.TAB_LIST );
//      return true;
//    }
//    return super.onKeyDown(keyCode, event);
//  }

  //  XXX
//  @Override
//  public Dialog onCreateDialog( int which ) {
//    switch ( which ) {
//      case SSID_FILTER:
//        return createSsidFilterDialog( this, "" );
//      default:
//        ListActivity.error( "unhandled dialog: " + which );
//    }
//    return null;    
//  }
  
  public static Dialog createSsidFilterDialog( final Activity activity, final String prefix ) {
    final Dialog dialog = new Dialog( activity );

    dialog.setContentView( R.layout.filterdialog );
    dialog.setTitle( "SSID Filter" );
    
    ListActivity.info("make new dialog");
    final SharedPreferences prefs = activity.getSharedPreferences( ListActivity.SHARED_PREFS, 0 );
    final EditText regex = (EditText) dialog.findViewById( R.id.edit_regex );
    regex.setText( prefs.getString( prefix + ListActivity.PREF_MAPF_REGEX, "") );
    
    final CheckBox invert = MainActivity.prefSetCheckBox( activity, dialog, R.id.showinvert, 
        prefix + ListActivity.PREF_MAPF_INVERT, false );
    final CheckBox open = MainActivity.prefSetCheckBox( activity, dialog, R.id.showopen, 
        prefix + ListActivity.PREF_MAPF_OPEN, true );
    final CheckBox wep = MainActivity.prefSetCheckBox( activity, dialog, R.id.showwep, 
        prefix + ListActivity.PREF_MAPF_WEP, true );
    final CheckBox wpa = MainActivity.prefSetCheckBox( activity, dialog, R.id.showwpa, 
        prefix + ListActivity.PREF_MAPF_WPA, true );
    final CheckBox cell = MainActivity.prefSetCheckBox( activity, dialog, R.id.showcell, 
        prefix + ListActivity.PREF_MAPF_CELL, true );
    final CheckBox enabled = MainActivity.prefSetCheckBox( activity, dialog, R.id.enabled, 
        prefix + ListActivity.PREF_MAPF_ENABLED, true );
    
    Button ok = (Button) dialog.findViewById( R.id.ok_button );
    ok.setOnClickListener( new OnClickListener() {
        public void onClick( final View buttonView ) {  
          try {                
            final Editor editor = prefs.edit();
            editor.putString( prefix + ListActivity.PREF_MAPF_REGEX, regex.getText().toString() );
            editor.putBoolean( prefix + ListActivity.PREF_MAPF_INVERT, invert.isChecked() );
            editor.putBoolean( prefix + ListActivity.PREF_MAPF_OPEN, open.isChecked() );
            editor.putBoolean( prefix + ListActivity.PREF_MAPF_WEP, wep.isChecked() );
            editor.putBoolean( prefix + ListActivity.PREF_MAPF_WPA, wpa.isChecked() );
            editor.putBoolean( prefix + ListActivity.PREF_MAPF_CELL, cell.isChecked() );
            editor.putBoolean( prefix + ListActivity.PREF_MAPF_ENABLED, enabled.isChecked() );
            editor.commit();
            dialog.dismiss();
          }
          catch ( Exception ex ) {
            // guess it wasn't there anyways
            ListActivity.info( "exception dismissing filter dialog: " + ex );
          }
        }
      } );
    
    Button cancel = (Button) dialog.findViewById( R.id.cancel_button );
    cancel.setOnClickListener( new OnClickListener() {
        public void onClick( final View buttonView ) {  
          try {
            regex.setText( prefs.getString( prefix + ListActivity.PREF_MAPF_REGEX, "") );
            MainActivity.prefSetCheckBox( activity, dialog, R.id.showinvert, 
                prefix + ListActivity.PREF_MAPF_INVERT, false );
            MainActivity.prefSetCheckBox( activity, dialog, R.id.showopen, 
                prefix + ListActivity.PREF_MAPF_OPEN, true );
            MainActivity.prefSetCheckBox( activity, dialog, R.id.showwep, 
                prefix + ListActivity.PREF_MAPF_WEP, true );
            MainActivity.prefSetCheckBox( activity, dialog, R.id.showwpa, 
                prefix + ListActivity.PREF_MAPF_WPA, true );
            MainActivity.prefSetCheckBox( activity, dialog, R.id.showcell, 
                prefix + ListActivity.PREF_MAPF_CELL, true );
            MainActivity.prefSetCheckBox( activity, dialog, R.id.enabled, 
                prefix + ListActivity.PREF_MAPF_ENABLED, true );
            
            dialog.dismiss();
          }
          catch ( Exception ex ) {
            // guess it wasn't there anyways
            ListActivity.info( "exception dismissing filter dialog: " + ex );
          }
        }
      } );
    
    return dialog;
  }

  private void setupQuery() {
    if (ListActivity.getNetworkCache().size() > 25) {
      // don't load, there's already networks to show
      return;
    }
    
    final String sql = "SELECT bssid FROM " 
      + DatabaseHelper.LOCATION_TABLE + " ORDER BY _id DESC LIMIT 200";
    
    final QueryThread.Request request = new QueryThread.Request( sql, new QueryThread.ResultHandler() {
      public void handleRow( final Cursor cursor ) {
        final String bssid = cursor.getString(0);
        final ConcurrentLinkedHashMap<String,Network> networkCache = ListActivity.getNetworkCache();
        
        Network network = networkCache.get( bssid );        
        if ( network == null ) {
          network = ListActivity.lameStatic.dbHelper.getNetwork( bssid );
          networkCache.put( network.getBssid(), network );      
          // ListActivity.info("bssid: " + network.getBssid() + " ssid: " + network.getSsid());
        
          final GeoPoint geoPoint = network.getGeoPoint();
          final int newWifiForRun = 1;
          final long newWifiForDB = 0;
          WifiReceiver.updateTrailStat(geoPoint, newWifiForRun, newWifiForDB);          
        }
      }
      
      public void complete() {
        if ( mapView != null ) {
          // force a redraw
          ((View) mapView).postInvalidate();
        }
      }
    });
    ListActivity.lameStatic.dbHelper.addToQueue( request );
  }
  
//  private void tryEvil() {
//    final String apiKey = "hiuhhkjhkjhkjh";
//    //Object foo = new com.google.android.maps.MapView( this, apiKey );
//    try {
//      File file = new File("/sdcard/com.google.android.maps.jar");
//      ListActivity.info("file exists: " + file.exists() + " " + file.canRead());
//      //DexFile df = new DexFile(file);
//      
//      DexClassLoader cl = new DexClassLoader("/system/framework/com.google.android.maps.jar:/sdcard/evil.jar",
//          "/sdcard/", null, MappingActivity.class.getClassLoader() );
//      // this is abstract, doesn't seem like we can reflect into it, proxy only works for interfaces :(
////      Class<?> mapActivityClass = cl.loadClass("com.google.android.maps.MapActivity");
//      
//      Class<?> mapActivityClass = cl.loadClass("EvilMap");
//      Constructor<?> constructor = mapActivityClass.getConstructor(Activity.class);
//      Object mapActivity = constructor.newInstance( this );
//      ListActivity.info("mapActivity: " + mapActivity.getClass().getName());
//      Method create = mapActivity.getClass().getMethod("onCreate", Bundle.class);
//      create.invoke(mapActivity, new Bundle());
//      
////      final InvocationHandler handler = new InvocationHandler() {
////        public Object invoke( Object object, Method method, Object[] args ) {
////          ListActivity.info("invoke: " + method.getName() );
////          return null;
////        }
////      };
////      Object mapActivity = Proxy.newProxyInstance( mapActivityClass.getClassLoader(), 
////                                         new Class[]{ mapActivityClass }, handler );
//      
//      Class<?> foo = cl.loadClass("com.google.android.maps.MapView");
//      constructor = foo.getConstructor(Context.class, String.class);
//      Object googMap = constructor.newInstance( mapActivity, apiKey );
//      ListActivity.info("googMap: " + googMap);
//
//    }
//    catch ( Exception ex)  {
//      ListActivity.error("ex: " + ex, ex);
//    }
//        
//  }
  
}
