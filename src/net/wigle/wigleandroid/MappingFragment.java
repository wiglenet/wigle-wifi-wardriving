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
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.database.Cursor;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.view.MenuItemCompat;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.RelativeLayout;
import android.widget.TextView;

/**
 * show a map!
 */
@SuppressWarnings("deprecation")
public final class MappingFragment extends Fragment {
  private static class State {
    private boolean locked = true;
    private boolean firstMove = true;
    private IGeoPoint oldCenter = null;
    private final int oldZoom = Integer.MIN_VALUE;
  }
  private final State state = new State();

  private IMapController mapControl;
  private IMapView mapView;
  private final Handler timer = new Handler();
  private AtomicBoolean finishing;
  private Location previousLocation;
  private int previousRunNets;
  private MyLocationOverlay myLocationOverlay = null;

  private static final String DIALOG_PREFIX = "DialogPrefix";
  public static LocationListener STATIC_LOCATION_LISTENER = null;

  private static final int DEFAULT_ZOOM = 17;
  public static final GeoPoint DEFAULT_POINT = new GeoPoint( 41950000, -87650000 );
  private static final int MENU_SETTINGS = 10;
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
    setHasOptionsMenu(true);
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

    IGeoPoint oldCenter = null;
    int oldZoom = Integer.MIN_VALUE;
    if ( state.oldCenter != null ) {
      // pry an orientation change, which calls destroy
      oldCenter = state.oldCenter;
      oldZoom = state.oldZoom;
    }

    setupMapView( view, oldCenter, oldZoom );
    return view;
  }

  private void setupMapView( final View view, final IGeoPoint oldCenter, final int oldZoom ) {
    // view
    final RelativeLayout rlView = (RelativeLayout) view.findViewById( R.id.map_rl );

    // tryEvil();

    // possibly choose goog maps here
    mapView = new MapView( getActivity(), 256 );

    if ( mapView instanceof View ) {
      MainActivity.info("is vew!!!!!");
      ViewGroup.LayoutParams params = new ViewGroup.LayoutParams(
          ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
      ((View) mapView).setLayoutParams(params);
    }

    if ( mapView instanceof MapView ) {
      final MapView osmMapView = (MapView) mapView;
      osmMapView.setUseSafeCanvas(true);

      // conditionally replace the tile source
      final SharedPreferences prefs = getActivity().getSharedPreferences( ListFragment.SHARED_PREFS, 0 );
      final boolean wigleTiles = prefs.getBoolean( ListFragment.PREF_USE_WIGLE_TILES, true );
      if ( wigleTiles ) {
          osmMapView.setTileSource( WigleTileSource.WiGLE );
      }

      rlView.addView( osmMapView );
      osmMapView.setBuiltInZoomControls( true );
      osmMapView.setMultiTouchControls( true );

      // my location overlay
      myLocationOverlay = new MyLocationOverlay( getActivity().getApplicationContext(), osmMapView );
      myLocationOverlay.setLocationUpdateMinTime( MainActivity.LOCATION_UPDATE_INTERVAL );
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
      final SharedPreferences prefs = getActivity().getSharedPreferences( ListFragment.SHARED_PREFS, 0 );
      zoom = prefs.getInt( ListFragment.PREF_PREV_ZOOM, zoom );
    }
    mapControl.setCenter( centerPoint );
    mapControl.setZoom( zoom );
    mapControl.setCenter( centerPoint );

    MainActivity.info("done setupMapView. zoom: " + zoom);
  }

  public static IGeoPoint getCenter( final Context context, final IGeoPoint priorityCenter,
      final Location previousLocation ) {

    IGeoPoint centerPoint = DEFAULT_POINT;
    final Location location = ListFragment.lameStatic.location;
    final SharedPreferences prefs = context.getSharedPreferences( ListFragment.SHARED_PREFS, 0 );

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
        float lat = prefs.getFloat( ListFragment.PREF_PREV_LAT, Float.MIN_VALUE );
        float lon = prefs.getFloat( ListFragment.PREF_PREV_LON, Float.MIN_VALUE );
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
      MainActivity.info("exception getting last known location: " + ex);
    }
    return retval;
  }

  final Runnable mUpdateTimeTask = new MapRunnable();
  private class MapRunnable implements Runnable {
    @Override
    public void run() {
        // make sure the app isn't trying to finish
        if ( ! finishing.get() ) {
          final Location location = ListFragment.lameStatic.location;
          if ( location != null ) {
            if ( state.locked ) {
              // MainActivity.info( "mapping center location: " + location );
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
                || previousRunNets != ListFragment.lameStatic.runNets) {
              // location or nets have changed, update the view
              if ( mapView instanceof View ) {
                ((View) mapView).postInvalidate();
              }
            }
            // set if location isn't null
            previousLocation = location;
          }

          previousRunNets = ListFragment.lameStatic.runNets;

          final View view = getView();

          TextView tv = (TextView) view.findViewById( R.id.stats_run );
          tv.setText( getString(R.string.run) + ": " + ListFragment.lameStatic.runNets );
          tv = (TextView) view.findViewById( R.id.stats_new );
          tv.setText( getString(R.string.new_word) + ": " + ListFragment.lameStatic.newNets );
          tv = (TextView) view.findViewById( R.id.stats_dbnets );
          tv.setText( getString(R.string.db) + ": " + ListFragment.lameStatic.dbNets );

          final long period = 1000L;
          // info("wifitimer: " + period );
          timer.postDelayed( this, period );
        }
        else {
          MainActivity.info( "finishing mapping timer" );
        }
    }
  };

  private void setupTimer() {
    timer.removeCallbacks( mUpdateTimeTask );
    timer.postDelayed( mUpdateTimeTask, 250 );
  }

  @Override
  public void onDetach() {
    MainActivity.info( "Map: onDetach.");
    super.onDetach();
    ((MapView)mapView).onDetach();
  }

  @Override
  public void onDestroy() {
    MainActivity.info( "destroy mapping." );
    finishing.set( true );

    // save zoom
    final SharedPreferences prefs = getActivity().getSharedPreferences( ListFragment.SHARED_PREFS, 0 );
    final Editor edit = prefs.edit();
    edit.putInt( ListFragment.PREF_PREV_ZOOM, mapView.getZoomLevel() );
    edit.commit();

    // save center
    state.oldCenter = mapView.getMapCenter();

    super.onDestroy();
  }

  @Override
  public void onPause() {
    MainActivity.info( "pause mapping." );
    myLocationOverlay.disableCompass();
    disableLocation();

    super.onPause();
  }

  @Override
  public void onResume() {
    MainActivity.info( "resume mapping." );
    myLocationOverlay.enableCompass();
    enableLocation();

    super.onResume();

    setupTimer();
    getActivity().setTitle(R.string.mapping_app_name);
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
      MainActivity.getMainActivity(this).getGPSListener().setMapListener(myLocationOverlay);
      myLocationOverlay.enableMyLocation();
    }
    catch (Exception ex) {
      MainActivity.error("Could not enableLocation for maps: " + ex, ex);
    }
  }

  /* Creates the menu items */
  @Override
  public void onCreateOptionsMenu (final Menu menu, final MenuInflater inflater) {
    MenuItem item = null;
    final SharedPreferences prefs = getActivity().getSharedPreferences( ListFragment.SHARED_PREFS, 0 );
    final boolean showNewDBOnly = prefs.getBoolean( ListFragment.PREF_MAP_ONLY_NEWDB, false );
    final boolean showLabel = prefs.getBoolean( ListFragment.PREF_MAP_LABEL, false );

    String nameLabel = showLabel ? getString(R.string.menu_labels_off) : getString(R.string.menu_labels_on);
    item = menu.add(0, MENU_LABEL, 0, nameLabel);
    item.setIcon( android.R.drawable.ic_dialog_info );

    item = menu.add(0, MENU_FILTER, 0, getString(R.string.menu_ssid_filter));
    item.setIcon( android.R.drawable.ic_menu_search );
    MenuItemCompat.setShowAsAction(item, MenuItemCompat.SHOW_AS_ACTION_IF_ROOM);

    String name = state.locked ? getString(R.string.menu_turn_off_lockon) : getString(R.string.menu_turn_on_lockon);
    item = menu.add(0, MENU_TOGGLE_LOCK, 0, name);
    item.setIcon( android.R.drawable.ic_menu_mapmode );

    String nameDB = showNewDBOnly ? getString(R.string.menu_show_old) : getString(R.string.menu_show_new);
    item = menu.add(0, MENU_TOGGLE_NEWDB, 0, nameDB);
    item.setIcon( android.R.drawable.ic_menu_edit );

    item = menu.add(0, MENU_ZOOM_IN, 0, getString(R.string.menu_zoom_in));
    item.setIcon( android.R.drawable.ic_menu_add );

    item = menu.add(0, MENU_ZOOM_OUT, 0, getString(R.string.menu_zoom_out));
    item.setIcon( android.R.drawable.ic_menu_revert );

    item = menu.add(0, MENU_SETTINGS, 0, getString(R.string.menu_settings));
    item.setIcon( android.R.drawable.ic_menu_preferences );

    item = menu.add(0, MENU_EXIT, 0, getString(R.string.menu_exit));
    item.setIcon( android.R.drawable.ic_menu_close_clear_cancel );

    super.onCreateOptionsMenu(menu, inflater);
  }

  /* Handles item selections */
  @Override
  public boolean onOptionsItemSelected( final MenuItem item ) {
      switch ( item.getItemId() ) {
        case MENU_EXIT: {
          final MainActivity main = MainActivity.getMainActivity();
          main.finish();
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
          final SharedPreferences prefs = getActivity().getSharedPreferences( ListFragment.SHARED_PREFS, 0 );
          final boolean showNewDBOnly = ! prefs.getBoolean( ListFragment.PREF_MAP_ONLY_NEWDB, false );
          Editor edit = prefs.edit();
          edit.putBoolean( ListFragment.PREF_MAP_ONLY_NEWDB, showNewDBOnly );
          edit.commit();

          String name = showNewDBOnly ? getString(R.string.menu_show_old) : getString(R.string.menu_show_new);
          item.setTitle( name );
          return true;
        }
        case MENU_LABEL: {
          final SharedPreferences prefs = getActivity().getSharedPreferences( ListFragment.SHARED_PREFS, 0 );
          final boolean showLabel = ! prefs.getBoolean( ListFragment.PREF_MAP_LABEL, true );
          Editor edit = prefs.edit();
          edit.putBoolean( ListFragment.PREF_MAP_LABEL, showLabel );
          edit.commit();

          String name = showLabel ? getString(R.string.menu_labels_off) : getString(R.string.menu_labels_on);
          item.setTitle( name );
          return true;
        }
        case MENU_FILTER: {
          onCreateDialog( SSID_FILTER );
          return true;
        }
        case MENU_SETTINGS: {
          MainActivity.info("start settings activity");
          final Intent settingsIntent = new Intent( this.getActivity(), SettingsActivity.class );
          startActivity( settingsIntent );
          break;
        }
      }
      return false;
  }

  public void onCreateDialog( int which ) {
    DialogFragment dialogFragment = null;
    switch ( which ) {
      case SSID_FILTER:
        dialogFragment = createSsidFilterDialog( "" );
        break;
      default:
        MainActivity.error( "unhandled dialog: " + which );
    }

    if (dialogFragment != null) {
      final FragmentManager fm = getActivity().getSupportFragmentManager();
      dialogFragment.show(fm, MainActivity.LIST_FRAGMENT_TAG);
    }
  }

  public static class MapDialogFragment extends DialogFragment {
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {

      final String prefix = getArguments().getString(DIALOG_PREFIX);

      final Dialog dialog = getDialog();
      final Activity activity = getActivity();
      final View view = inflater.inflate(R.layout.filterdialog, container);
      dialog.setTitle( "SSID Filter" );

      MainActivity.info("make new dialog");
      final SharedPreferences prefs = activity.getSharedPreferences( ListFragment.SHARED_PREFS, 0 );
      final EditText regex = (EditText) view.findViewById( R.id.edit_regex );
      regex.setText( prefs.getString( prefix + ListFragment.PREF_MAPF_REGEX, "") );

      final CheckBox invert = MainActivity.prefSetCheckBox( activity, view, R.id.showinvert,
          prefix + ListFragment.PREF_MAPF_INVERT, false );
      final CheckBox open = MainActivity.prefSetCheckBox( activity, view, R.id.showopen,
          prefix + ListFragment.PREF_MAPF_OPEN, true );
      final CheckBox wep = MainActivity.prefSetCheckBox( activity, view, R.id.showwep,
          prefix + ListFragment.PREF_MAPF_WEP, true );
      final CheckBox wpa = MainActivity.prefSetCheckBox( activity, view, R.id.showwpa,
          prefix + ListFragment.PREF_MAPF_WPA, true );
      final CheckBox cell = MainActivity.prefSetCheckBox( activity, view, R.id.showcell,
          prefix + ListFragment.PREF_MAPF_CELL, true );
      final CheckBox enabled = MainActivity.prefSetCheckBox( activity, view, R.id.enabled,
          prefix + ListFragment.PREF_MAPF_ENABLED, true );

      Button ok = (Button) view.findViewById( R.id.ok_button );
      ok.setOnClickListener( new OnClickListener() {
          @Override
          public void onClick( final View buttonView ) {
            try {
              final Editor editor = prefs.edit();
              editor.putString( prefix + ListFragment.PREF_MAPF_REGEX, regex.getText().toString() );
              editor.putBoolean( prefix + ListFragment.PREF_MAPF_INVERT, invert.isChecked() );
              editor.putBoolean( prefix + ListFragment.PREF_MAPF_OPEN, open.isChecked() );
              editor.putBoolean( prefix + ListFragment.PREF_MAPF_WEP, wep.isChecked() );
              editor.putBoolean( prefix + ListFragment.PREF_MAPF_WPA, wpa.isChecked() );
              editor.putBoolean( prefix + ListFragment.PREF_MAPF_CELL, cell.isChecked() );
              editor.putBoolean( prefix + ListFragment.PREF_MAPF_ENABLED, enabled.isChecked() );
              editor.commit();
              dialog.dismiss();
            }
            catch ( Exception ex ) {
              // guess it wasn't there anyways
              MainActivity.info( "exception dismissing filter dialog: " + ex );
            }
          }
        } );

      Button cancel = (Button) view.findViewById( R.id.cancel_button );
      cancel.setOnClickListener( new OnClickListener() {
          @Override
          public void onClick( final View buttonView ) {
            try {
              regex.setText( prefs.getString( prefix + ListFragment.PREF_MAPF_REGEX, "") );
              MainActivity.prefSetCheckBox( activity, view, R.id.showinvert,
                  prefix + ListFragment.PREF_MAPF_INVERT, false );
              MainActivity.prefSetCheckBox( activity, view, R.id.showopen,
                  prefix + ListFragment.PREF_MAPF_OPEN, true );
              MainActivity.prefSetCheckBox( activity, view, R.id.showwep,
                  prefix + ListFragment.PREF_MAPF_WEP, true );
              MainActivity.prefSetCheckBox( activity, view, R.id.showwpa,
                  prefix + ListFragment.PREF_MAPF_WPA, true );
              MainActivity.prefSetCheckBox( activity, view, R.id.showcell,
                  prefix + ListFragment.PREF_MAPF_CELL, true );
              MainActivity.prefSetCheckBox( activity, view, R.id.enabled,
                  prefix + ListFragment.PREF_MAPF_ENABLED, true );

              dialog.dismiss();
            }
            catch ( Exception ex ) {
              // guess it wasn't there anyways
              MainActivity.info( "exception dismissing filter dialog: " + ex );
            }
          }
        } );
      return view;
    }
  }

  public static DialogFragment createSsidFilterDialog( final String prefix ) {
    final DialogFragment dialog = new MapDialogFragment();
    final Bundle bundle = new Bundle();
    bundle.putString(DIALOG_PREFIX, prefix);
    dialog.setArguments(bundle);
    return dialog;
  }

  private void setupQuery() {
    if (MainActivity.getNetworkCache().size() > 25) {
      // don't load, there's already networks to show
      return;
    }

    final String sql = "SELECT bssid FROM "
      + DatabaseHelper.LOCATION_TABLE + " ORDER BY _id DESC LIMIT 200";

    final QueryThread.Request request = new QueryThread.Request( sql, new QueryThread.ResultHandler() {
      @Override
      public void handleRow( final Cursor cursor ) {
        final String bssid = cursor.getString(0);
        final ConcurrentLinkedHashMap<String,Network> networkCache = MainActivity.getNetworkCache();

        Network network = networkCache.get( bssid );
        if ( network == null ) {
          network = ListFragment.lameStatic.dbHelper.getNetwork( bssid );
          if ( network != null ) {
            networkCache.put( network.getBssid(), network );
            // MainActivity.info("bssid: " + network.getBssid() + " ssid: " + network.getSsid());

            final GeoPoint geoPoint = network.getGeoPoint();
            final int newWifiForRun = 1;
            final long newWifiForDB = 0;
            WifiReceiver.updateTrailStat(geoPoint, newWifiForRun, newWifiForDB);
          }
        }
      }

      @Override
      public void complete() {
        if ( mapView != null ) {
          // force a redraw
          ((View) mapView).postInvalidate();
        }
      }
    });
    ListFragment.lameStatic.dbHelper.addToQueue( request );
  }

//  private void tryEvil() {
//    final String apiKey = "hiuhhkjhkjhkjh";
//    //Object foo = new com.google.android.maps.MapView( this, apiKey );
//    try {
//      File file = new File("/sdcard/com.google.android.maps.jar");
//      MainActivity.info("file exists: " + file.exists() + " " + file.canRead());
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
//      MainActivity.info("mapActivity: " + mapActivity.getClass().getName());
//      Method create = mapActivity.getClass().getMethod("onCreate", Bundle.class);
//      create.invoke(mapActivity, new Bundle());
//
////      final InvocationHandler handler = new InvocationHandler() {
////        public Object invoke( Object object, Method method, Object[] args ) {
////          MainActivity.info("invoke: " + method.getName() );
////          return null;
////        }
////      };
////      Object mapActivity = Proxy.newProxyInstance( mapActivityClass.getClassLoader(),
////                                         new Class[]{ mapActivityClass }, handler );
//
//      Class<?> foo = cl.loadClass("com.google.android.maps.MapView");
//      constructor = foo.getConstructor(Context.class, String.class);
//      Object googMap = constructor.newInstance( mapActivity, apiKey );
//      MainActivity.info("googMap: " + googMap);
//
//    }
//    catch ( Exception ex)  {
//      MainActivity.error("ex: " + ex, ex);
//    }
//
//  }

}
