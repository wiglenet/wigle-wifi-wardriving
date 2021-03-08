package net.wigle.wigleandroid;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.NumberFormat;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.database.Cursor;
import android.graphics.Color;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Handler;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.core.view.MenuItemCompat;
import androidx.fragment.app.FragmentActivity;

import android.util.Base64;
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
import android.widget.Toast;

import com.goebl.simplify.PointExtractor;
import com.goebl.simplify.Simplify;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.maps.CameraUpdate;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapView;
import com.google.android.gms.maps.MapsInitializer;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.android.gms.maps.model.Tile;
import com.google.android.gms.maps.model.TileOverlay;
import com.google.android.gms.maps.model.TileOverlayOptions;
import com.google.android.gms.maps.model.TileProvider;

import net.wigle.wigleandroid.background.AbstractApiRequest;
import net.wigle.wigleandroid.background.QueryThread;
import net.wigle.wigleandroid.db.DatabaseHelper;
import net.wigle.wigleandroid.model.ConcurrentLinkedHashMap;
import net.wigle.wigleandroid.model.Network;
import net.wigle.wigleandroid.ui.UINumberFormat;
import net.wigle.wigleandroid.ui.WiGLEToast;

import static net.wigle.wigleandroid.listener.GPSListener.MIN_ROUTE_LOCATION_DIFF_METERS;
import static net.wigle.wigleandroid.listener.GPSListener.MIN_ROUTE_LOCATION_DIFF_TIME;
import static net.wigle.wigleandroid.listener.GPSListener.MIN_ROUTE_LOCATION_PRECISION_METERS;

/**
 * show a map!
 */
public final class MappingFragment extends Fragment {

    private final String ROUTE_LINE_TAG = "routePolyline";

    private static class State {
        private boolean locked = true;
        private boolean firstMove = true;
        private LatLng oldCenter = null;
        private final int oldZoom = Integer.MIN_VALUE;
    }

    private final State state = new State();

    private MapView mapView;
    private MapRender mapRender;

    private final Handler timer = new Handler();
    private AtomicBoolean finishing;
    private Location previousLocation;
    private int previousRunNets;
    private TileOverlay tileOverlay;
    private Polyline routePolyline;
    private Location lastLocation;

    private Menu menu;

    private static final String DIALOG_PREFIX = "DialogPrefix";
    public static final String MAP_DIALOG_PREFIX = "";
    public static LocationListener STATIC_LOCATION_LISTENER = null;

    private NumberFormat numberFormat;

    static final int UPDATE_MAP_FILTER = 1;

    private static final int DEFAULT_ZOOM = 17;
    public static final LatLng DEFAULT_POINT = new LatLng(41.95d, -87.65d);
    private static final int MENU_ZOOM_IN = 13;
    private static final int MENU_ZOOM_OUT = 14;
    private static final int MENU_TOGGLE_LOCK = 15;
    private static final int MENU_TOGGLE_NEWDB = 16;
    private static final int MENU_LABEL = 17;
    private static final int MENU_FILTER = 18;
    private static final int MENU_CLUSTER = 19;
    private static final int MENU_TRAFFIC = 20;
    private static final int MENU_MAP_TYPE = 21;
    private static final int MENU_WAKELOCK = 22;

    private static final int SSID_FILTER = 102;

    private static final String MAP_TILE_URL_FORMAT =
            "https://wigle.net/clientTile?zoom=%d&x=%d&y=%d&startTransID=%s&endTransID=%s";

    private static final String HIGH_RES_TILE_TRAILER = "&sizeX=512&sizeY=512";
    private static final String ONLY_MINE_TILE_TRAILER = "&onlymine=1";
    private static final String NOT_MINE_TILE_TRAILER = "&notmine=1";

    // parameters for polyline simplification package: https://github.com/hgoebl/simplify-java
    // assume we need to undertake drastic route line complexity if we exceed this many segments
    private static final int POLYLINE_PERF_THRESHOLD = 2500;
    // when performing drastic polyline simplification (Radial-Distance), this is our "tolerance value"
    private static final float POLYLINE_TOLERANCE_COARSE = 20.0f;
    // when performing minor polyline simplification (Douglas-Peucker), this is our "tolerance value"
    private static final float POLYLINE_TOLERANCE_FINE = 50.0f;

    private static final float ROUTE_WIDTH = 20.0f;
    private static final int OVERLAY_DARK = Color.BLACK;
    private static final int OVERLAY_LIGHT = Color.parseColor("#F4D03F");



    /** Called when the activity is first created. */
    @Override
    public void onCreate(final Bundle savedInstanceState) {
        MainActivity.info("MAP: onCreate");
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
        // set language
        MainActivity.setLocale(getActivity());
        finishing = new AtomicBoolean(false);

        Configuration sysConfig = getResources().getConfiguration();
        Locale locale = null;
        if (null != sysConfig) {
            locale = sysConfig.locale;
        }
        if (null == locale) {
            locale = Locale.US;
        }
        numberFormat = NumberFormat.getNumberInstance(locale);
        numberFormat.setMaximumFractionDigits(1);
        // media volume
        getActivity().setVolumeControlStream(AudioManager.STREAM_MUSIC);

        setupQuery();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        mapView = new MapView(getActivity());
        final int serviceAvailable = GooglePlayServicesUtil.isGooglePlayServicesAvailable(getActivity());
        if (serviceAvailable == ConnectionResult.SUCCESS) {
            try {
                mapView.onCreate(savedInstanceState);
            }
            catch (final SecurityException ex) {
                MainActivity.error("security exception oncreateview map: " + ex, ex);
            }
        } else {
            final FragmentActivity a = getActivity();
            if (null != a && !a.isFinishing()) {
                WiGLEToast.showOverFragment(a, R.string.fatal_pre_message, getString(R.string.map_needs_playservice));
            }
        }
        MapsInitializer.initialize(getActivity());
        final View view = inflater.inflate(R.layout.map, container, false);

        LatLng oldCenter = null;
        int oldZoom = Integer.MIN_VALUE;
        if (state.oldCenter != null) {
            // pry an orientation change, which calls destroy
            oldCenter = state.oldCenter;
            oldZoom = state.oldZoom;
        }

        setupMapView(view, oldCenter, oldZoom);
        return view;
    }

    private void setupMapView(final View view, final LatLng oldCenter, final int oldZoom) {
        // view
        final RelativeLayout rlView = (RelativeLayout) view.findViewById(R.id.map_rl);

        if (mapView != null) {
            ViewGroup.LayoutParams params = new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            mapView.setLayoutParams(params);
        }

        // conditionally replace the tile source
        final SharedPreferences prefs = getActivity().getSharedPreferences(ListFragment.SHARED_PREFS, 0);
        final boolean visualizeRoute = prefs != null && prefs.getBoolean(ListFragment.PREF_VISUALIZE_ROUTE, false);
        rlView.addView(mapView);
        // guard against not having google play services
        mapView.getMapAsync(new OnMapReadyCallback() {
            @Override
            public void onMapReady(final GoogleMap googleMap) {
                if (ActivityCompat.checkSelfPermission(MappingFragment.this.getContext(),
                        android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                        || ActivityCompat.checkSelfPermission(MappingFragment.this.getContext(),
                        android.Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                    googleMap.setMyLocationEnabled(true);
                }

                googleMap.setBuildingsEnabled(true);
                if (null != prefs) {
                    final boolean showTraffic = prefs.getBoolean(ListFragment.PREF_MAP_TRAFFIC, true);
                    googleMap.setTrafficEnabled(showTraffic);
                    final int mapType = prefs.getInt(ListFragment.PREF_MAP_TYPE, GoogleMap.MAP_TYPE_NORMAL);
                    googleMap.setMapType(mapType);
                } else {
                    googleMap.setMapType(GoogleMap.MAP_TYPE_NORMAL);
                }
                mapRender = new MapRender(getActivity(), googleMap, false);

                // Seeing stack overflow crashes on multiple phones in specific locations, based on indoor svcs.
                googleMap.setIndoorEnabled(false);

                googleMap.setOnMyLocationButtonClickListener(new GoogleMap.OnMyLocationButtonClickListener() {
                    @Override
                    public boolean onMyLocationButtonClick() {
                        if (!state.locked) {

                            state.locked = true;
                            if (menu != null) {
                                MenuItem item = menu.findItem(MENU_TOGGLE_LOCK);
                                String name = state.locked ? getString(R.string.menu_turn_off_lockon) : getString(R.string.menu_turn_on_lockon);
                                item.setTitle(name);
                                MainActivity.info("on-my-location received - activating lock");
                            }
                        }
                        return false;
                    }
                });

                googleMap.setOnCameraMoveStartedListener(new GoogleMap.OnCameraMoveStartedListener() {
                    @Override
                    public void onCameraMoveStarted(int reason) {
                        if (reason ==REASON_GESTURE) {
                            if (state.locked) {
                                state.locked = false;
                                if (menu != null) {
                                    MenuItem item = menu.findItem(MENU_TOGGLE_LOCK);
                                    String name = state.locked ? getString(R.string.menu_turn_off_lockon) : getString(R.string.menu_turn_on_lockon);
                                    item.setTitle(name);
                                }
                            }
                        } else if (reason ==REASON_API_ANIMATION) {
                            //DEBUG: MainActivity.info("Camera moved due to user tap");
                            //TODO: should we combine this case with REASON_GESTURE?
                        } else if (reason ==REASON_DEVELOPER_ANIMATION) {
                            //MainActivity.info("Camera moved due to app directive");
                        }
                    }


                });

                // controller
                final LatLng centerPoint = getCenter(getActivity(), oldCenter, previousLocation);
                float zoom = DEFAULT_ZOOM;
                if (oldZoom >= 0) {
                    zoom = oldZoom;
                } else {
                    if (null != prefs) {
                        zoom = prefs.getFloat(ListFragment.PREF_PREV_ZOOM, zoom);
                    }
                }

                final CameraPosition cameraPosition = new CameraPosition.Builder()
                        .target(centerPoint).zoom(zoom).build();
                googleMap.moveCamera(CameraUpdateFactory.newCameraPosition(cameraPosition));


                if (null != prefs && !ListFragment.PREF_MAP_NO_TILE.equals(
                        prefs.getString(ListFragment.PREF_SHOW_DISCOVERED,
                                ListFragment.PREF_MAP_NO_TILE))) {
                    final int providerTileRes = MainActivity.isHighDefinition()?512:256;

                    //TODO: DRY up token composition vs AbstractApiRequest?
                    String ifAuthToken = null;
                    try {
                        final String authname = prefs.getString(ListFragment.PREF_AUTHNAME, null);
                        final String token = TokenAccess.getApiToken(prefs);
                        if ((null != authname) && (null != token)) {
                            final String encoded = Base64.encodeToString((authname + ":" + token).getBytes("UTF-8"), Base64.NO_WRAP);
                            ifAuthToken = "Basic " + encoded;
                        }
                    } catch (UnsupportedEncodingException ueex) {
                        MainActivity.error("map tiles: unable to encode credentials for mine/others", ueex);
                    } catch (UnsupportedOperationException uoex) {
                        MainActivity.error("map tiles: unable to access credentials for mine/others", uoex);
                    } catch (Exception ex) {
                        MainActivity.error("map tiles: unable to access credentials for mine/others", ex);
                    }
                    final String authToken = ifAuthToken;

                    final String userAgent = AbstractApiRequest.getUserAgentString();


                    TileProvider tileProvider = new TileProvider() {
                        @Override
                        public Tile getTile(int x, int y, int zoom) {
                            if (!checkTileExists(x, y, zoom)) {
                                return null;
                            }

                            final Long since = prefs.getLong(ListFragment.PREF_SHOW_DISCOVERED_SINCE, 2001);
                            int thisYear = Calendar.getInstance().get(Calendar.YEAR);
                            String tileContents = prefs.getString(ListFragment.PREF_SHOW_DISCOVERED,
                                    ListFragment.PREF_MAP_NO_TILE);

                            String sinceString = String.format("%d0000-00000", since);
                            String toString = String.format("%d0000-00000", thisYear+1);
                            String s = String.format(MAP_TILE_URL_FORMAT,
                                    zoom, x, y, sinceString, toString);

                            if (MainActivity.isHighDefinition()) {
                                    s += HIGH_RES_TILE_TRAILER;
                            }

                            // ALIBI: defaults to "ALL"
                            if (ListFragment.PREF_MAP_ONLYMINE_TILE.equals(tileContents)) {
                                s += ONLY_MINE_TILE_TRAILER;
                            } else if (ListFragment.PREF_MAP_NOTMINE_TILE.equals(tileContents)) {
                                s += NOT_MINE_TILE_TRAILER;
                            }

                            //DEBUG: MainActivity.info("map URL: " + s);

                            try {
                                final byte[] data = downloadData(new URL(s), userAgent, authToken);
                                if (data != null) {
                                    return new Tile(providerTileRes, providerTileRes, data);
                                }
                            } catch (MalformedURLException e) {
                                throw new AssertionError(e);
                            }
                            return null;
                        }

                        /*
                         * depends on supported levels on the server
                         */
                        private boolean checkTileExists(int x, int y, int zoom) {
                            int minZoom = 0;
                            int maxZoom = 24;

                            if ((zoom < minZoom || zoom > maxZoom)) {
                                return false;
                            }

                            return true;
                        }

                        private byte[] downloadData(final URL url, final String userAgent, final String authToken) {
                            ByteArrayOutputStream baos = new ByteArrayOutputStream();
                            InputStream is = null;
                            try {
                                HttpURLConnection conn = (HttpURLConnection)url.openConnection();
                                if (null != authToken) {
                                    conn.setRequestProperty("Authorization", authToken);
                                }
                                conn.setRequestProperty("User-Agent", userAgent);
                                is = conn.getInputStream();
                                byte[] byteChunk = new byte[4096];
                                int n;

                                while ((n = is.read(byteChunk)) > 0) {
                                    baos.write(byteChunk, 0, n);
                                }
                            } catch (IOException e) {
                                MainActivity.error("Failed while reading bytes from " +
                                        url.toExternalForm() + ": "+ e.getMessage());
                                e.printStackTrace();
                            } finally {
                                if (is != null) {
                                    try {
                                        is.close();
                                    } catch (IOException ioex) {
                                        MainActivity.error("Failed while closing InputStream " +
                                                url.toExternalForm() + ": "+ ioex.getMessage());
                                        ioex.printStackTrace();
                                    }
                                }
                            }
                            return baos.toByteArray();
                        }
                    };



                    tileOverlay = googleMap.addTileOverlay(new TileOverlayOptions()
                            .tileProvider(tileProvider).transparency(0.35f));
                }

                //ALIBI: still checking prefs because we pass them to the dbHelper
                if (null != prefs  && visualizeRoute) {

                    PolylineOptions pOptions = new PolylineOptions()
                                    .clickable(false);
                    final int mapMode = prefs.getInt(ListFragment.PREF_MAP_TYPE, GoogleMap.MAP_TYPE_NORMAL);
                    try {
                        Cursor routeCursor = ListFragment.lameStatic.dbHelper.getCurrentVisibleRouteIterator(prefs);
                        if (null == routeCursor) {
                            MainActivity.info("null route cursor; not mapping");
                        } else {
                            long segmentCount = 0;

                            for (routeCursor.moveToFirst(); !routeCursor.isAfterLast(); routeCursor.moveToNext()) {
                                final double lat = routeCursor.getDouble(0);
                                final double lon = routeCursor.getDouble(1);
                                //final long time = routeCursor.getLong(2);
                                pOptions.add(
                                        new LatLng(lat, lon));

                                pOptions.color(getRouteColorForMapType(mapMode));
                                pOptions.width(ROUTE_WIDTH); //DEFAULT: 10.0
                                pOptions.zIndex(10000); //to overlay on traffic data
                                segmentCount++;
                            }
                            MainActivity.info("Loaded route with " + segmentCount + " segments");
                            routePolyline = googleMap.addPolyline(pOptions);

                            routePolyline.setTag(ROUTE_LINE_TAG);
                        }
                    } catch (Exception e) {
                        MainActivity.error("Unable to add route: ",e);
                    }
                }
            }
        });
        MainActivity.info("done setupMapView.");
    }

    public static LatLng getCenter( final Context context, final LatLng priorityCenter,
                                    final Location previousLocation ) {

        LatLng centerPoint = DEFAULT_POINT;
        final Location location = ListFragment.lameStatic.location;
        final SharedPreferences prefs = context.getSharedPreferences( ListFragment.SHARED_PREFS, 0 );

        if ( priorityCenter != null ) {
            centerPoint = priorityCenter;
        }
        else if ( location != null ) {
            centerPoint = new LatLng( location.getLatitude(), location.getLongitude() );
        }
        else if ( previousLocation != null ) {
            centerPoint = new LatLng( previousLocation.getLatitude(), previousLocation.getLongitude() );
        }
        else {
            final Location gpsLocation = safelyGetLast(context, LocationManager.GPS_PROVIDER);
            final Location networkLocation = safelyGetLast(context, LocationManager.NETWORK_PROVIDER);

            if ( gpsLocation != null ) {
                centerPoint = new LatLng( gpsLocation.getLatitude(), gpsLocation.getLongitude()  );
            }
            else if ( networkLocation != null ) {
                centerPoint = new LatLng( networkLocation.getLatitude(), networkLocation.getLongitude()  );
            }
            else {
                // ok, try the saved prefs
                float lat = prefs.getFloat( ListFragment.PREF_PREV_LAT, Float.MIN_VALUE );
                float lon = prefs.getFloat( ListFragment.PREF_PREV_LON, Float.MIN_VALUE );
                if ( lat != Float.MIN_VALUE && lon != Float.MIN_VALUE ) {
                    centerPoint = new LatLng( lat, lon );
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
        catch ( final IllegalArgumentException | SecurityException ex ) {
            MainActivity.info("exception getting last known location: " + ex);
        }
        return retval;
    }

    final Runnable mUpdateTimeTask = new MapRunnable();
    private class MapRunnable implements Runnable {
        @Override
        public void run() {
            final View view = getView();
            final SharedPreferences prefs = getActivity() != null?getActivity().getSharedPreferences(ListFragment.SHARED_PREFS, 0):null;
            // make sure the app isn't trying to finish
            if ( ! finishing.get() ) {
                final Location location = ListFragment.lameStatic.location;
                if ( location != null ) {
                    if ( state.locked ) {
                        mapView.getMapAsync(new OnMapReadyCallback() {
                            @Override
                            public void onMapReady(final GoogleMap googleMap) {
                                // MainActivity.info( "mapping center location: " + location );
                                final LatLng locLatLng = new LatLng(location.getLatitude(), location.getLongitude());
                                final CameraUpdate centerUpdate = CameraUpdateFactory.newLatLng(locLatLng);
                                if (state.firstMove) {
                                    googleMap.moveCamera(centerUpdate);
                                    state.firstMove = false;
                                } else {
                                    googleMap.animateCamera(centerUpdate);
                                }
                            }
                        });
                    }
                    else if ( previousLocation == null || previousLocation.getLatitude() != location.getLatitude()
                            || previousLocation.getLongitude() != location.getLongitude()
                            || previousRunNets != ListFragment.lameStatic.runNets) {
                        // location or nets have changed, update the view
                        if (mapView != null) {
                            mapView.postInvalidate();
                        }
                    }

                    try {
                        final boolean showRoute = prefs != null && prefs.getBoolean(ListFragment.PREF_VISUALIZE_ROUTE, false);
                        //DEBUG: MainActivity.info("mUpdateTimeTask with non-null location. show: "+showRoute);
                        if (showRoute) {
                            double accuracy = location.getAccuracy();
                            if (location.getTime() != 0 &&
                                    accuracy < MIN_ROUTE_LOCATION_PRECISION_METERS
                                    && accuracy > 0.0d &&
                                    (lastLocation == null ||
                                            ((location.getTime() - lastLocation.getTime()) > MIN_ROUTE_LOCATION_DIFF_TIME) &&
                                                    lastLocation.distanceTo(location)> MIN_ROUTE_LOCATION_DIFF_METERS)) {
                                if (routePolyline != null) {
                                    final List<LatLng> routePoints = routePolyline.getPoints();
                                    routePoints.add(new LatLng(location.getLatitude(), location.getLongitude()));
                                    final int mapMode = prefs.getInt(ListFragment.PREF_MAP_TYPE, GoogleMap.MAP_TYPE_NORMAL);
                                    routePolyline.setColor(getRouteColorForMapType(mapMode));

                                    if (routePoints.size() > POLYLINE_PERF_THRESHOLD) {
                                        Simplify<LatLng> simplify = new Simplify<LatLng>(new LatLng[0], latLngPointExtractor);
                                        LatLng[] simplified = simplify.simplify(routePoints.toArray(new LatLng[0]), POLYLINE_TOLERANCE_COARSE, false);
                                        routePolyline.setPoints(Arrays.asList(simplified));
                                        MainActivity.error("major route simplification: "+routePoints.size()+"->"+simplified.length);
                                    } else if (routePoints.size() > 1) {
                                        Simplify<LatLng> simplify = new Simplify<LatLng>(new LatLng[0], latLngPointExtractor);
                                        LatLng[] simplified = simplify.simplify(routePoints.toArray(new LatLng[0]), POLYLINE_TOLERANCE_FINE, true);
                                        routePolyline.setPoints(Arrays.asList(simplified));
                                        MainActivity.error("minor route simplification: "+routePoints.size()+"->"+simplified.length);
                                    } else {
                                        //DEBUG: MainActivity.error("route points: " + routePoints.size());
                                        routePolyline.setPoints(routePoints);
                                    }
                                } else {
                                    MainActivity.error("route polyline null - this shouldn't happen");
                                }
                                lastLocation = location;
                            } else {
                                //DEBUG:    MainActivity.warn("time/accuracy route update DQ");
                            }
                        }
                    } catch (Exception ex) {
                        MainActivity.error("Route point update failed: ",ex);
                    }

                    // set if location isn't null
                    previousLocation = location;
                }


                previousRunNets = ListFragment.lameStatic.runNets;

                if (view != null) {
                    TextView tv = view.findViewById(R.id.stats_run);
                    tv.setText(getString(R.string.run) + ": " + UINumberFormat.counterFormat(
                            ListFragment.lameStatic.runNets+ListFragment.lameStatic.runBt));
                    tv = view.findViewById(R.id.stats_wifi);
                    tv.setText( UINumberFormat.counterFormat(ListFragment.lameStatic.newWifi) );
                    tv = view.findViewById( R.id.stats_cell );
                    tv.setText( ""+UINumberFormat.counterFormat(ListFragment.lameStatic.newCells)  );
                    tv = view.findViewById( R.id.stats_bt );
                    tv.setText( ""+UINumberFormat.counterFormat(ListFragment.lameStatic.newBt)  );

                    tv = view.findViewById( R.id.stats_dbnets );
                    tv.setText(UINumberFormat.counterFormat(ListFragment.lameStatic.dbNets));
                    if (prefs != null) {
                        float dist = prefs.getFloat(ListFragment.PREF_DISTANCE_RUN, 0f);
                        final String distString = DashboardFragment.metersToString(prefs,
                                numberFormat, getActivity(), dist, true);
                        tv = view.findViewById(R.id.rundistance);
                        tv.setText(distString);
                    }
                }

                final long period = 1000L;
                // info("wifitimer: " + period );
                timer.postDelayed( this, period );
            }
            else {
                MainActivity.info( "finishing mapping timer" );
            }
        }
    }

    private void setupTimer() {
        timer.removeCallbacks( mUpdateTimeTask );
        timer.postDelayed( mUpdateTimeTask, 250 );
    }

    @Override
    public void onDetach() {
        MainActivity.info( "MAP: onDetach.");
        super.onDetach();
    }

    @Override
    public void onDestroy() {
        MainActivity.info( "MAP: destroy mapping." );
        finishing.set(true);

        mapView.getMapAsync(new OnMapReadyCallback() {

        @Override
        public void onMapReady(final GoogleMap googleMap) {
            // save zoom
            final SharedPreferences prefs = getActivity().getSharedPreferences(ListFragment.SHARED_PREFS, 0);
            if (null != prefs) {
                final Editor edit = prefs.edit();
                edit.putFloat(ListFragment.PREF_PREV_ZOOM, googleMap.getCameraPosition().zoom);
                edit.apply();
            } else {
                MainActivity.warn("failed saving map state - unable to get preferences.");
            }
            // save center
            state.oldCenter = googleMap.getCameraPosition().target;
            }
        });

        try {
            mapView.onDestroy();
        } catch (NullPointerException ex) {
            // seen in the wild
            MainActivity.info("exception in mapView.onDestroy: " + ex, ex);
        }

        super.onDestroy();
    }

    @Override
    public void onPause() {
        MainActivity.info("MAP: onPause");
        super.onPause();
        try {
            mapView.onPause();
        }
        catch (final NullPointerException ex) {
            MainActivity.error("npe on mapview pause: " + ex, ex);
        }
        if (mapRender != null) {
            // save memory
            mapRender.clear();
        }
    }

    @Override
    public void onResume() {
        MainActivity.info( "MAP: onResume" );
        if (mapRender != null) {
            mapRender.onResume();
        }
        if (null != mapView) {
            //refresh tiles on resume
            mapView.postInvalidate();
        }

        if (null != tileOverlay) {
            //DEBUG: MainActivity.info("clearing tile overlay cache");
            tileOverlay.clearTileCache();
        }
        super.onResume();

        setupTimer();
        getActivity().setTitle(R.string.mapping_app_name);
        mapView.onResume();
    }

    @Override
    public void onSaveInstanceState(final Bundle outState) {
        MainActivity.info( "MAP: onSaveInstanceState" );
        super.onSaveInstanceState(outState);
        mapView.onSaveInstanceState(outState);
    }

    @Override
    public void onLowMemory() {
        MainActivity.info( "MAP: onLowMemory" );
        super.onLowMemory();
        mapView.onLowMemory();
    }

    public void addNetwork(final Network network) {
        if (mapRender != null && mapRender.okForMapTab(network)) {
            mapRender.addItem(network);
        }
    }

    public void updateNetwork(final Network network) {
        if (mapRender != null) {
            mapRender.updateNetwork(network);
        }
    }

    public void reCluster() {
        if (mapRender != null) {
            mapRender.reCluster();
        }
    }

    /* Creates the menu items */
    @Override
    public void onCreateOptionsMenu (final Menu menu, final MenuInflater inflater) {
        MainActivity.info( "MAP: onCreateOptionsMenu" );
        MenuItem item;
        final SharedPreferences prefs = getActivity().getSharedPreferences( ListFragment.SHARED_PREFS, 0 );
        final boolean showNewDBOnly = prefs.getBoolean( ListFragment.PREF_MAP_ONLY_NEWDB, false );
        final boolean showLabel = prefs.getBoolean( ListFragment.PREF_MAP_LABEL, true );
        final boolean showCluster = prefs.getBoolean( ListFragment.PREF_MAP_CLUSTER, true );
        final boolean showTraffic = prefs.getBoolean( ListFragment.PREF_MAP_TRAFFIC, true );

        String nameLabel = showLabel ? getString(R.string.menu_labels_off) : getString(R.string.menu_labels_on);
        item = menu.add(0, MENU_LABEL, 0, nameLabel);
        item.setIcon( android.R.drawable.ic_dialog_info );

        String nameCluster = showCluster ? getString(R.string.menu_cluster_off) : getString(R.string.menu_cluster_on);
        item = menu.add(0, MENU_CLUSTER, 0, nameCluster);
        item.setIcon( android.R.drawable.ic_menu_add );

        String nameTraffic = showTraffic ? getString(R.string.menu_traffic_off) : getString(R.string.menu_traffic_on);
        item = menu.add(0, MENU_TRAFFIC, 0, nameTraffic);
        item.setIcon( android.R.drawable.ic_menu_directions );

        item = menu.add(0, MENU_MAP_TYPE, 0, getString(R.string.menu_map_type));
        item.setIcon( android.R.drawable.ic_menu_mapmode );
        MenuItemCompat.setShowAsAction(item, MenuItemCompat.SHOW_AS_ACTION_IF_ROOM);

        item = menu.add(0, MENU_FILTER, 0, getString(R.string.settings_map_head));
        item.setIcon( android.R.drawable.ic_menu_search );
        MenuItemCompat.setShowAsAction(item, MenuItemCompat.SHOW_AS_ACTION_IF_ROOM);

        String name = state.locked ? getString(R.string.menu_turn_off_lockon) : getString(R.string.menu_turn_on_lockon);
        item = menu.add(0, MENU_TOGGLE_LOCK, 0, name);
        item.setIcon( android.R.drawable.ic_lock_lock );

        String nameDB = showNewDBOnly ? getString(R.string.menu_show_old) : getString(R.string.menu_show_new);
        item = menu.add(0, MENU_TOGGLE_NEWDB, 0, nameDB);
        item.setIcon( android.R.drawable.ic_menu_edit );

        final String wake = MainActivity.isScreenLocked( this ) ?
                getString(R.string.menu_screen_sleep) : getString(R.string.menu_screen_wake);
        item = menu.add(0, MENU_WAKELOCK, 0, wake);
        item.setIcon( android.R.drawable.ic_menu_gallery );

        // item = menu.add(0, MENU_ZOOM_IN, 0, getString(R.string.menu_zoom_in));
        // item.setIcon( android.R.drawable.ic_menu_add );

        // item = menu.add(0, MENU_ZOOM_OUT, 0, getString(R.string.menu_zoom_out));
        // item.setIcon( android.R.drawable.ic_menu_revert );

        // item = menu.add(0, MENU_SETTINGS, 0, getString(R.string.menu_settings));
        // item.setIcon( android.R.drawable.ic_menu_preferences );

        // item = menu.add(0, MENU_EXIT, 0, getString(R.string.menu_exit));
        // item.setIcon( android.R.drawable.ic_menu_close_clear_cancel );

        super.onCreateOptionsMenu(menu, inflater);
        this.menu = menu;
    }

    /* Handles item selections */
    @Override
    public boolean onOptionsItemSelected( final MenuItem item ) {
        final SharedPreferences prefs = getActivity().getSharedPreferences( ListFragment.SHARED_PREFS, 0 );
        switch ( item.getItemId() ) {
            case MENU_ZOOM_IN: {
                mapView.getMapAsync(new OnMapReadyCallback() {
                    @Override
                    public void onMapReady(final GoogleMap googleMap) {
                        float zoom = googleMap.getCameraPosition().zoom;
                        zoom++;
                        final CameraUpdate zoomUpdate = CameraUpdateFactory.zoomTo(zoom);
                        googleMap.animateCamera(zoomUpdate);
                    }
                });
                return true;
            }
            case MENU_ZOOM_OUT: {
                mapView.getMapAsync(new OnMapReadyCallback() {
                    @Override
                    public void onMapReady(final GoogleMap googleMap) {
                        float zoom = googleMap.getCameraPosition().zoom;
                        zoom--;
                        final CameraUpdate zoomUpdate = CameraUpdateFactory.zoomTo(zoom);
                        googleMap.animateCamera(zoomUpdate);
                    }
                });
                return true;
            }
            case MENU_TOGGLE_LOCK: {
                state.locked = ! state.locked;
                String name = state.locked ? getString(R.string.menu_turn_off_lockon) : getString(R.string.menu_turn_on_lockon);
                item.setTitle( name );
                return true;
            }
            case MENU_TOGGLE_NEWDB: {
                final boolean showNewDBOnly = ! prefs.getBoolean( ListFragment.PREF_MAP_ONLY_NEWDB, false );
                Editor edit = prefs.edit();
                edit.putBoolean( ListFragment.PREF_MAP_ONLY_NEWDB, showNewDBOnly );
                edit.apply();

                String name = showNewDBOnly ? getString(R.string.menu_show_old) : getString(R.string.menu_show_new);
                item.setTitle( name );
                if (mapRender != null) {
                    mapRender.reCluster();
                }
                return true;
            }
            case MENU_LABEL: {
                final boolean showLabel = ! prefs.getBoolean( ListFragment.PREF_MAP_LABEL, true );
                Editor edit = prefs.edit();
                edit.putBoolean( ListFragment.PREF_MAP_LABEL, showLabel );
                edit.apply();

                String name = showLabel ? getString(R.string.menu_labels_off) : getString(R.string.menu_labels_on);
                item.setTitle( name );

                if (mapRender != null) {
                    mapRender.reCluster();
                }
                return true;
            }
            case MENU_CLUSTER: {
                final boolean showCluster = ! prefs.getBoolean( ListFragment.PREF_MAP_CLUSTER, true );
                Editor edit = prefs.edit();
                edit.putBoolean( ListFragment.PREF_MAP_CLUSTER, showCluster );
                edit.apply();

                String name = showCluster ? getString(R.string.menu_cluster_off) : getString(R.string.menu_cluster_on);
                item.setTitle( name );

                if (mapRender != null) {
                    mapRender.reCluster();
                }
                return true;
            }
            case MENU_TRAFFIC: {
                final boolean showTraffic = ! prefs.getBoolean( ListFragment.PREF_MAP_TRAFFIC, true );
                Editor edit = prefs.edit();
                edit.putBoolean( ListFragment.PREF_MAP_TRAFFIC, showTraffic );
                edit.apply();

                String name = showTraffic ? getString(R.string.menu_traffic_off) : getString(R.string.menu_traffic_on);
                item.setTitle( name );
                mapView.getMapAsync(new OnMapReadyCallback() {
                    @Override
                    public void onMapReady(final GoogleMap googleMap) {
                        googleMap.setTrafficEnabled(showTraffic);
                    }
                });
                return true;
            }
            case MENU_FILTER: {
                final Intent intent = new Intent(getActivity(), MapFilterActivity.class);
                getActivity().startActivityForResult(intent, UPDATE_MAP_FILTER);
                return true;
            }
            case MENU_MAP_TYPE: {
                mapView.getMapAsync(new OnMapReadyCallback() {
                    @Override
                    public void onMapReady(final GoogleMap googleMap) {
                        int newMapType = prefs.getInt(ListFragment.PREF_MAP_TYPE, GoogleMap.MAP_TYPE_NORMAL);
                        final Activity a = getActivity();
                        switch (newMapType) {
                            case GoogleMap.MAP_TYPE_NORMAL:
                                newMapType = GoogleMap.MAP_TYPE_SATELLITE;
                                if (null != a && !a.isFinishing()) {
                                    WiGLEToast.showOverActivity(a, R.string.tab_map, getString(R.string.map_toast_satellite), Toast.LENGTH_SHORT);
                                }
                                break;
                            case GoogleMap.MAP_TYPE_SATELLITE:
                                newMapType = GoogleMap.MAP_TYPE_HYBRID;
                                if (null != a && !a.isFinishing()) {
                                    WiGLEToast.showOverActivity(a, R.string.tab_map, getString(R.string.map_toast_hybrid), Toast.LENGTH_SHORT);
                                }
                                break;
                            case GoogleMap.MAP_TYPE_HYBRID:
                                newMapType = GoogleMap.MAP_TYPE_TERRAIN;
                                if (null != a && !a.isFinishing()) {
                                    WiGLEToast.showOverActivity(a, R.string.tab_map, getString(R.string.map_toast_terrain), Toast.LENGTH_SHORT);
                                }
                                break;
                            case GoogleMap.MAP_TYPE_TERRAIN:
                                newMapType = GoogleMap.MAP_TYPE_NORMAL;
                                if (null != a && !a.isFinishing()) {
                                    WiGLEToast.showOverActivity(a, R.string.tab_map, getString(R.string.map_toast_normal), Toast.LENGTH_SHORT);
                                }
                                break;
                            default:
                                MainActivity.error("unhandled mapType: " + newMapType);
                        }
                        Editor edit = prefs.edit();
                        edit.putInt(ListFragment.PREF_MAP_TYPE, newMapType);
                        edit.apply();
                        googleMap.setMapType(newMapType);
                    }
                });
            }
            case MENU_WAKELOCK: {
                boolean screenLocked = ! MainActivity.isScreenLocked( this );
                MainActivity.setLockScreen( this, screenLocked );
                final String wake = screenLocked ? getString(R.string.menu_screen_sleep) : getString(R.string.menu_screen_wake);
                item.setTitle( wake );
                return true;
            }
        }
        return false;
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

            MainActivity.info("make new dialog. prefix: " + prefix);
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
                        editor.apply();
                        MainActivity.reclusterMap();

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
        final int cacheSize = MainActivity.getNetworkCache().size();
        if (cacheSize > (ListFragment.lameStatic.networkCache.maxSize() / 4)) {
            // don't load, there's already networks to show
            MainActivity.info("cacheSize: " + cacheSize + ", skipping previous networks");
            return;
        }

        final String sql = "SELECT bssid FROM "
                + DatabaseHelper.LOCATION_TABLE + " ORDER BY _id DESC LIMIT "
                + (ListFragment.lameStatic.networkCache.maxSize() * 2);

        final QueryThread.Request request = new QueryThread.Request( sql, new QueryThread.ResultHandler() {
            @Override
            public boolean handleRow( final Cursor cursor ) {
                final String bssid = cursor.getString(0);
                final ConcurrentLinkedHashMap<String,Network> networkCache = MainActivity.getNetworkCache();

                Network network = networkCache.get( bssid );
                // MainActivity.info("RAW bssid: " + bssid);
                if ( network == null ) {
                    network = ListFragment.lameStatic.dbHelper.getNetwork( bssid );
                    if ( network != null ) {
                        networkCache.put( network.getBssid(), network );

                        if (networkCache.isFull()) {
                            MainActivity.info("Cache is full, breaking out of query result handling");
                            return false;
                        }
                    }
                }
                return true;
            }

            @Override
            public void complete() {
                if ( mapView != null ) {
                    // force a redraw
                    mapView.postInvalidate();
                }
            }
        });
        if (ListFragment.lameStatic.dbHelper != null) {
            ListFragment.lameStatic.dbHelper.addToQueue( request );
        }
    }
    private static PointExtractor<LatLng> latLngPointExtractor = new PointExtractor<LatLng>() {
        @Override
        public double getX(LatLng point) {
            return point.latitude * 1000000;
        }

        @Override
        public double getY(LatLng point) {
            return point.longitude * 1000000;
        }
    };

    private int getRouteColorForMapType(final int mapType) {
        if (mapType != GoogleMap.MAP_TYPE_NORMAL && mapType != GoogleMap.MAP_TYPE_TERRAIN
                && mapType != GoogleMap.MAP_TYPE_NONE) {
            return OVERLAY_LIGHT;
        }
        return OVERLAY_DARK;
    }
}
