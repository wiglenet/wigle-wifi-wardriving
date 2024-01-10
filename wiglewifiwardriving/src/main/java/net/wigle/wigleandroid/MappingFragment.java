package net.wigle.wigleandroid;

import static android.view.View.GONE;
import static com.google.android.gms.maps.GoogleMap.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.NumberFormat;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

import android.annotation.SuppressLint;
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
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.core.view.MenuItemCompat;

import android.util.Base64;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
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
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.maps.CameraUpdate;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.MapView;
import com.google.android.gms.maps.MapsInitializer;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.android.gms.maps.model.Tile;
import com.google.android.gms.maps.model.TileOverlay;
import com.google.android.gms.maps.model.TileOverlayOptions;
import com.google.android.gms.maps.model.TileProvider;

import net.wigle.wigleandroid.background.PooledQueryExecutor;
import net.wigle.wigleandroid.db.DatabaseHelper;
import net.wigle.wigleandroid.model.ConcurrentLinkedHashMap;
import net.wigle.wigleandroid.model.Network;
import net.wigle.wigleandroid.net.WiGLEApiManager;
import net.wigle.wigleandroid.ui.PrefsBackedCheckbox;
import net.wigle.wigleandroid.ui.ThemeUtil;
import net.wigle.wigleandroid.ui.UINumberFormat;
import net.wigle.wigleandroid.ui.WiGLEToast;
import net.wigle.wigleandroid.util.HeadingManager;
import net.wigle.wigleandroid.util.Logging;
import net.wigle.wigleandroid.util.PreferenceKeys;
import net.wigle.wigleandroid.util.StatsUtil;

import static net.wigle.wigleandroid.listener.GNSSListener.MIN_ROUTE_LOCATION_DIFF_METERS;
import static net.wigle.wigleandroid.listener.GNSSListener.MIN_ROUTE_LOCATION_DIFF_TIME;
import static net.wigle.wigleandroid.listener.GNSSListener.MIN_ROUTE_LOCATION_PRECISION_METERS;

/**
 * show a map depicting current position and configurable stumbling progress information.
 */
public final class MappingFragment extends Fragment {

    private final String ROUTE_LINE_TAG = "routePolyline";

    private static class State {
        private boolean locked = true;
        private boolean firstMove = true;
        private LatLng oldCenter = null;
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

    private HeadingManager headingManager;

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
    // ALIBI: 15% is actually pretty acceptable for map orientation.
    private static final float MIN_BEARING_UPDATE_ACCURACY = 54.1f;

    private static final String MAP_TILE_URL_FORMAT =
            "https://wigle.net/clientTile?zoom=%d&x=%d&y=%d&startTransID=%s&endTransID=%s";

    private static final String HIGH_RES_TILE_TRAILER = "&sizeX=512&sizeY=512";
    private static final String ONLY_MINE_TILE_TRAILER = "&onlymine=1";
    private static final String NOT_MINE_TILE_TRAILER = "&notmine=1";

    // parameters for polyline simplification package: https://github.com/hgoebl/simplify-java
    // ALIBI: we could tighten these parameters significantly, but it results in wonky over-
    //   simplifications leading up to the present position (since there are no "subsequent" values
    //   to offset the algo's propensity to over-optimize the "end" cap.)
    // Values chosen not to overburden most modern Android phones capabilities.
    // assume we need to undertake drastic route line complexity if we exceed this many segments
    private static final int POLYLINE_PERF_THRESHOLD_COARSE = 15000;
    // perform minor route line complexity simplification if we exceed this many segments
    private static final int POLYLINE_PERF_THRESHOLD_FINE = 5000;

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
        Logging.info("MAP: onCreate");
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
        // set language
        final Activity a = getActivity();
        if (null != a) {
            MainActivity.setLocale(a);
            a.setVolumeControlStream(AudioManager.STREAM_MUSIC);
        }
        finishing = new AtomicBoolean(false);
        final Configuration conf = getResources().getConfiguration();
        Locale locale = null;
        if (null != conf && null != conf.getLocales()) {
            locale = conf.getLocales().get(0);
        }
        if (null == locale) {
            locale = Locale.US;
        }
        numberFormat = NumberFormat.getNumberInstance(locale);
        numberFormat.setMaximumFractionDigits(2);
        // media volume
        //TODO: almost certainly not like this.
        final SharedPreferences prefs = (null != a)?a.getSharedPreferences(PreferenceKeys.SHARED_PREFS, 0):null;

        if (prefs != null && BuildConfig.DEBUG && HeadingManager.DEBUG && prefs.getBoolean(PreferenceKeys.PREF_MAP_FOLLOW_BEARING, false)) {
            headingManager = new HeadingManager(a);
        }
        setupQuery();
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        final Activity a = getActivity();
        if (null != a) {
            mapView = new MapView(a);
            final int serviceAvailable = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(a);
            if (serviceAvailable == ConnectionResult.SUCCESS) {
                try {
                    mapView.onCreate(savedInstanceState);
                    final SharedPreferences prefs = a.getSharedPreferences(PreferenceKeys.SHARED_PREFS, 0);
                    mapView.getMapAsync(googleMap -> ThemeUtil.setMapTheme(googleMap, mapView.getContext(), prefs, R.raw.night_style_json));
                }
                catch (final SecurityException ex) {
                    Logging.error("security exception oncreateview map: " + ex, ex);
                }
            } else {
                WiGLEToast.showOverFragment(getActivity(), R.string.fatal_pre_message, getString(R.string.map_needs_playservice));
            }
            MapsInitializer.initialize(a);
        }
        final View view = inflater.inflate(R.layout.map, container, false);

        LatLng oldCenter = null;
        int oldZoom = Integer.MIN_VALUE;
        if (state.oldCenter != null) {
            // pry an orientation change, which calls destroy
            oldCenter = state.oldCenter;
        }

        setupMapView(view, oldCenter, oldZoom);
        return view;
    }

    @SuppressLint("MissingPermission")
    private void setupMapView(final View view, final LatLng oldCenter, final int oldZoom) {
        // view
        final RelativeLayout rlView = view.findViewById(R.id.map_rl);

        if (mapView != null) {
            ViewGroup.LayoutParams params = new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            mapView.setLayoutParams(params);
        }

        // conditionally replace the tile source
        final Activity a = getActivity();
        final SharedPreferences prefs = (null != a)?a.getSharedPreferences(PreferenceKeys.SHARED_PREFS, 0):null;
        final boolean visualizeRoute = prefs != null && prefs.getBoolean(PreferenceKeys.PREF_VISUALIZE_ROUTE, false);
        rlView.addView(mapView);
        // guard against not having google play services
        mapView.getMapAsync(googleMap -> {
            final Context c = MappingFragment.this.getContext();
            if (null != c) {
                if (ActivityCompat.checkSelfPermission(c,
                        android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                        || ActivityCompat.checkSelfPermission(MappingFragment.this.getContext(),
                        android.Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                    googleMap.setMyLocationEnabled(true);
                    googleMap.setOnCameraIdleListener(() -> {
                        if (null != prefs && prefs.getBoolean(PreferenceKeys.PREF_MAP_FOLLOW_BEARING, false)) {
                            Float cameraBearing = getBearing(getActivity());
                            Logging.info("Camera Bearing: "+cameraBearing);
                            if (null != cameraBearing) {
                                CameraPosition camPos = CameraPosition
                                        .builder(googleMap.getCameraPosition())
                                        .bearing(cameraBearing)
                                        .build();
                                googleMap.animateCamera(CameraUpdateFactory.newCameraPosition(camPos));
                            }
                        }
                    });
                }
            }
            googleMap.setBuildingsEnabled(true);
            if (null != prefs) {
                final boolean showTraffic = prefs.getBoolean(PreferenceKeys.PREF_MAP_TRAFFIC, true);
                googleMap.setTrafficEnabled(showTraffic);
                final int mapType = prefs.getInt(PreferenceKeys.PREF_MAP_TYPE, MAP_TYPE_NORMAL);
                googleMap.setMapType(mapType);
            } else {
                googleMap.setMapType(MAP_TYPE_NORMAL);
            }
            final Activity a1 = getActivity();
            if (null != a1) {
                mapRender = new MapRender(a1, googleMap, false);
            }

            // Seeing stack overflow crashes on multiple phones in specific locations, based on indoor svcs.
            googleMap.setIndoorEnabled(false);

            googleMap.setOnMyLocationButtonClickListener(() -> {
                if (!state.locked) {

                    state.locked = true;
                    if (menu != null) {
                        MenuItem item = menu.findItem(MENU_TOGGLE_LOCK);
                        String name = state.locked ? getString(R.string.menu_turn_off_lockon) : getString(R.string.menu_turn_on_lockon);
                        item.setTitle(name);
                        Logging.info("on-my-location received - activating lock");
                    }
                }
                return false;
            });

            googleMap.setOnCameraMoveStartedListener(reason -> {
                if (reason == OnCameraMoveStartedListener.REASON_GESTURE) {
                    if (state.locked) {
                        state.locked = false;
                        if (menu != null) {
                            MenuItem item = menu.findItem(MENU_TOGGLE_LOCK);
                            String name = state.locked ? getString(R.string.menu_turn_off_lockon) : getString(R.string.menu_turn_on_lockon);
                            item.setTitle(name);
                        }
                    }
                } else if (reason == OnCameraMoveStartedListener.REASON_API_ANIMATION) {
                    //DEBUG: MainActivity.info("Camera moved due to user tap");
                    //TODO: should we combine this case with REASON_GESTURE?
                } else if (reason == OnCameraMoveStartedListener.REASON_DEVELOPER_ANIMATION) {
                    //MainActivity.info("Camera moved due to app directive");
                }
            });

            // controller
            final LatLng centerPoint = getCenter(getActivity(), oldCenter, previousLocation);
            float zoom = DEFAULT_ZOOM;
            if (oldZoom >= 0) {
                zoom = oldZoom;
            } else {
                if (null != prefs) {
                    zoom = prefs.getFloat(PreferenceKeys.PREF_PREV_ZOOM, zoom);
                }
            }

            final CameraPosition cameraPosition = new CameraPosition.Builder()
                    .target(centerPoint).zoom(zoom).build();
            googleMap.moveCamera(CameraUpdateFactory.newCameraPosition(cameraPosition));


            if (null != prefs && !PreferenceKeys.PREF_MAP_NO_TILE.equals(
                    prefs.getString(PreferenceKeys.PREF_SHOW_DISCOVERED,
                            PreferenceKeys.PREF_MAP_NO_TILE))) {
                final int providerTileRes = MainActivity.isHighDefinition()?512:256;

                //TODO: DRY up token composition vs AbstractApiRequest?
                String ifAuthToken = null;
                try {
                    final String authname = prefs.getString(PreferenceKeys.PREF_AUTHNAME, null);
                    final String token = TokenAccess.getApiToken(prefs);
                    if ((null != authname) && (null != token)) {
                        final String encoded = Base64.encodeToString((authname + ":" + token).getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP);
                        ifAuthToken = "Basic " + encoded;
                    }
                } catch (Exception uoex) {
                    Logging.error("map tiles: unable to access credentials for mine/others", uoex);
                }
                final String authToken = ifAuthToken;

                final String userAgent = WiGLEApiManager.USER_AGENT;


                TileProvider tileProvider = new TileProvider() {
                    @SuppressLint("DefaultLocale")
                    @Override
                    public Tile getTile(int x, int y, int zoom) {
                        if (!checkTileExists(x, y, zoom)) {
                            return null;
                        }

                        final Long since = prefs.getLong(PreferenceKeys.PREF_SHOW_DISCOVERED_SINCE, 2001);
                        int thisYear = Calendar.getInstance().get(Calendar.YEAR);
                        String tileContents = prefs.getString(PreferenceKeys.PREF_SHOW_DISCOVERED,
                                PreferenceKeys.PREF_MAP_NO_TILE);

                        String sinceString = String.format("%d0000-00000", since);
                        String toString = String.format("%d0000-00000", thisYear+1);
                        String s = String.format(MAP_TILE_URL_FORMAT,
                                zoom, x, y, sinceString, toString);

                        if (MainActivity.isHighDefinition()) {
                                s += HIGH_RES_TILE_TRAILER;
                        }

                        // ALIBI: defaults to "ALL"
                        if (PreferenceKeys.PREF_MAP_ONLYMINE_TILE.equals(tileContents)) {
                            s += ONLY_MINE_TILE_TRAILER;
                        } else if (PreferenceKeys.PREF_MAP_NOTMINE_TILE.equals(tileContents)) {
                            s += NOT_MINE_TILE_TRAILER;
                        }

                        //DEBUG: MainActivity.info("map URL: " + s);

                        try {
                            final byte[] data = downloadData(new URL(s), userAgent, authToken);
                            return new Tile(providerTileRes, providerTileRes, data);
                        } catch (MalformedURLException e) {
                            throw new AssertionError(e);
                        }
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
                            Logging.error("Failed while reading bytes from " +
                                    url.toExternalForm() + ": "+ e.getMessage());
                            e.printStackTrace();
                        } finally {
                            if (is != null) {
                                try {
                                    is.close();
                                } catch (IOException ioex) {
                                    Logging.error("Failed while closing InputStream " +
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
                final int mapMode = prefs.getInt(PreferenceKeys.PREF_MAP_TYPE, MAP_TYPE_NORMAL);
                final boolean nightMode = ThemeUtil.shouldUseMapNightMode(getContext(), prefs);
                try (Cursor routeCursor = ListFragment.lameStatic.dbHelper
                        .getCurrentVisibleRouteIterator(prefs)) {
                    if (null == routeCursor) {
                        Logging.info("null route cursor; not mapping");
                    } else {
                        long segmentCount = 0;

                        for (routeCursor.moveToFirst(); !routeCursor.isAfterLast(); routeCursor.moveToNext()) {
                            final double lat = routeCursor.getDouble(0);
                            final double lon = routeCursor.getDouble(1);
                            //final long time = routeCursor.getLong(2);
                            pOptions.add(
                                    new LatLng(lat, lon));

                            pOptions.color(getRouteColorForMapType(mapMode, nightMode));
                            pOptions.width(ROUTE_WIDTH); //DEFAULT: 10.0
                            pOptions.zIndex(10000); //to overlay on traffic data
                            segmentCount++;
                        }
                        Logging.info("Loaded route with " + segmentCount + " segments");
                        routePolyline = googleMap.addPolyline(pOptions);

                        routePolyline.setTag(ROUTE_LINE_TAG);
                    }
                } catch (Exception e) {
                    Logging.error("Unable to add route: ",e);
                }
            }
        });
        Logging.info("done setupMapView.");
    }

    public Float getBearing( final Context context) {
        final Location gpsLocation = safelyGetLast(context, LocationManager.GPS_PROVIDER);
        if (gpsLocation != null) {
            //DEBUG: Logging.info("acc: "+headingManager.getAccuracy());
            final Float bearing = (gpsLocation.hasBearing() && gpsLocation.getBearing() != 0.0f)?gpsLocation.getBearing():null;

            if (null != bearing) {
                //ALIBI: prefer bearing if it's not garbage, because heading almost certainly is.
                if (gpsLocation.hasAccuracy() && (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)) {
                    if (gpsLocation.getBearingAccuracyDegrees() < MIN_BEARING_UPDATE_ACCURACY) {
                        return gpsLocation.getBearing();
                    }
                } else {
                    Logging.warn("have GPS location but no headingManager or accuracy");
                    return bearing;
                }
            }
            //ALIBI: heading is too often completely wrong. This is here for debugging only unless things improve.
            if (null != headingManager && BuildConfig.DEBUG && HeadingManager.DEBUG  && headingManager.getAccuracy() >= 3.0) {
                // if the fusion of accelerometer and magnetic compass claims it doesn't suck (although it probably still does)
                return headingManager.getHeading(gpsLocation);
            }
        }
        return null;
    }

    public static LatLng getCenter( final Context context, final LatLng priorityCenter,
                                    final Location previousLocation ) {

        LatLng centerPoint = DEFAULT_POINT;
        final Location location = ListFragment.lameStatic.location;
        final SharedPreferences prefs = context.getSharedPreferences( PreferenceKeys.SHARED_PREFS, 0 );

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
                float lat = prefs.getFloat( PreferenceKeys.PREF_PREV_LAT, Float.MIN_VALUE );
                float lon = prefs.getFloat( PreferenceKeys.PREF_PREV_LON, Float.MIN_VALUE );
                if ( lat != Float.MIN_VALUE && lon != Float.MIN_VALUE ) {
                    centerPoint = new LatLng( lat, lon );
                }
            }
        }

        return centerPoint;
    }

    @SuppressLint("MissingPermission")
    private static Location safelyGetLast(final Context context, final String provider ) {
        Location retval = null;
        try {
            final LocationManager locationManager = (LocationManager) context.getApplicationContext().getSystemService(Context.LOCATION_SERVICE);
            retval = locationManager.getLastKnownLocation( provider );
        }
        catch ( final IllegalArgumentException | SecurityException ex ) {
            Logging.info("exception getting last known location: " + ex);
        }
        return retval;
    }

    final Runnable mUpdateTimeTask = new MapRunnable();
    private class MapRunnable implements Runnable {
        @Override
        public void run() {
            final View view = getView();
            final Activity a = getActivity();
            final SharedPreferences prefs = a != null?a.getSharedPreferences(PreferenceKeys.SHARED_PREFS, 0):null;
            // make sure the app isn't trying to finish
            if ( ! finishing.get() ) {
                final Location location = ListFragment.lameStatic.location;
                if ( location != null ) {
                    if ( state.locked ) {
                        mapView.getMapAsync(googleMap -> {
                            // Logging.info( "mapping center location: " + location );
                            final LatLng locLatLng = new LatLng(location.getLatitude(), location.getLongitude());
                            float currentZoom = googleMap.getCameraPosition().zoom;
                            Float cameraBearing = null;
                            if (null != prefs && prefs.getBoolean(PreferenceKeys.PREF_MAP_FOLLOW_BEARING, false)) {
                                cameraBearing = getBearing(a);
                            }
                            final CameraUpdate centerUpdate = (state.firstMove || cameraBearing == null) ?
                                    CameraUpdateFactory.newLatLng(locLatLng) :
                                    CameraUpdateFactory.newCameraPosition(
                                        new CameraPosition.Builder().bearing(cameraBearing).zoom(currentZoom).target(locLatLng).build());
                            if (state.firstMove) {
                                googleMap.moveCamera(centerUpdate);
                                state.firstMove = false;
                            } else {
                                googleMap.animateCamera(centerUpdate);
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
                        final boolean showRoute = prefs != null && prefs.getBoolean(PreferenceKeys.PREF_VISUALIZE_ROUTE, false);
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
                                    final int mapMode = prefs.getInt(PreferenceKeys.PREF_MAP_TYPE, MAP_TYPE_NORMAL);
                                    final boolean nightMode = ThemeUtil.shouldUseMapNightMode(getContext(), prefs);
                                    routePolyline.setColor(getRouteColorForMapType(mapMode, nightMode));

                                    if (routePoints.size() > POLYLINE_PERF_THRESHOLD_COARSE) {
                                        Simplify<LatLng> simplify = new Simplify<>(new LatLng[0], latLngPointExtractor);
                                        LatLng[] simplified = simplify.simplify(routePoints.toArray(new LatLng[0]), POLYLINE_TOLERANCE_COARSE, false);
                                        routePolyline.setPoints(Arrays.asList(simplified));
                                        Logging.error("major route simplification: "+routePoints.size()+"->"+simplified.length);
                                    } else if (routePoints.size() > POLYLINE_PERF_THRESHOLD_FINE) {
                                        Simplify<LatLng> simplify = new Simplify<>(new LatLng[0], latLngPointExtractor);
                                        LatLng[] simplified = simplify.simplify(routePoints.toArray(new LatLng[0]), POLYLINE_TOLERANCE_FINE, true);
                                        routePolyline.setPoints(Arrays.asList(simplified));
                                        Logging.error("minor route simplification: "+routePoints.size()+"->"+simplified.length);
                                    } else {
                                        //DEBUG: MainActivity.error("route points: " + routePoints.size());
                                        routePolyline.setPoints(routePoints);
                                    }
                                } else {
                                    Logging.error("route polyline null - this shouldn't happen");
                                }
                                lastLocation = location;
                            } else {
                                //DEBUG:    MainActivity.warn("time/accuracy route update DQ");
                            }
                        }
                    } catch (Exception ex) {
                        Logging.error("Route point update failed: ",ex);
                    }

                    // set if location isn't null
                    previousLocation = location;
                }


                previousRunNets = ListFragment.lameStatic.runNets;

                if (view != null) {
                    TextView tv = view.findViewById(R.id.stats_wifi);
                    tv.setText( UINumberFormat.counterFormat(ListFragment.lameStatic.newWifi) );
                    tv = view.findViewById( R.id.stats_cell );
                    tv.setText( UINumberFormat.counterFormat(ListFragment.lameStatic.newCells)  );
                    tv = view.findViewById( R.id.stats_bt );
                    tv.setText( UINumberFormat.counterFormat(ListFragment.lameStatic.newBt)  );
                    if (null != prefs) {
                        final long unUploaded = StatsUtil.newNetsSinceUpload(prefs);
                        tv = view.findViewById(R.id.stats_unuploaded);
                        tv.setText(UINumberFormat.counterFormat(unUploaded));
                    }
                    tv = view.findViewById(R.id.heading);
                    final Location gpsLocation = safelyGetLast(getContext(), LocationManager.GPS_PROVIDER);
                    if (BuildConfig.DEBUG && HeadingManager.DEBUG) {
                        tv.setText(String.format(Locale.ROOT, "heading: %3.2f", ((headingManager != null) ? headingManager.getHeading(gpsLocation) : -1f)));
                        if (null != ListFragment.lameStatic.location) {
                            tv = view.findViewById(R.id.bearing);
                            if (gpsLocation.hasAccuracy() && (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)) {
                                tv.setText(String.format(Locale.ROOT,"bearing: %3.2f +/- %3.2f", ListFragment.lameStatic.location.getBearing(), ListFragment.lameStatic.location.getBearingAccuracyDegrees()));
                            } else {
                                tv.setText(String.format(Locale.ROOT,"bearing: %3.2f", ListFragment.lameStatic.location.getBearing()));
                            }
                        }
                        tv = view.findViewById(R.id.selectedbh);
                        tv.setText(String.format(Locale.ROOT,"chose: %3.2f", getBearing(getContext())));
                    } else {
                        final View v =view.findViewById(R.id.debug);
                        if (null != v) {
                            v.setVisibility(GONE);
                        }
                    }

                    tv = view.findViewById( R.id.stats_dbnets );
                    tv.setText(UINumberFormat.counterFormat(ListFragment.lameStatic.dbNets));
                    if (prefs != null) {
                        float dist = prefs.getFloat(PreferenceKeys.PREF_DISTANCE_RUN, 0f);
                        final String distString = UINumberFormat.metersToString(prefs,
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
                Logging.info( "finishing mapping timer" );
            }
        }
    }

    private void setupTimer() {
        timer.removeCallbacks( mUpdateTimeTask );
        timer.postDelayed( mUpdateTimeTask, 250 );
    }

    @Override
    public void onDetach() {
        Logging.info( "MAP: onDetach.");
        super.onDetach();
    }

    @Override
    public void onDestroy() {
        Logging.info( "MAP: destroy mapping." );
        finishing.set(true);

        mapView.getMapAsync(googleMap -> {
            // save zoom
            final Activity a = getActivity();
            if (null != a) {
            final SharedPreferences prefs = a.getSharedPreferences(PreferenceKeys.SHARED_PREFS, 0);
            if (null != prefs) {
                final Editor edit = prefs.edit();
                edit.putFloat(PreferenceKeys.PREF_PREV_ZOOM, googleMap.getCameraPosition().zoom);
                edit.apply();
            } else {
                Logging.warn("failed saving map state - unable to get preferences.");
            }
            // save center
            state.oldCenter = googleMap.getCameraPosition().target;
            }
        });
        try {
            mapView.onDestroy();
        } catch (NullPointerException ex) {
            // seen in the wild
            Logging.info("exception in mapView.onDestroy: " + ex, ex);
        }

        super.onDestroy();
    }

    @Override
    public void onPause() {
        Logging.info("MAP: onPause");
        super.onPause();
        try {
            mapView.onPause();
        } catch (final NullPointerException ex) {
            Logging.error("npe on mapview pause: " + ex, ex);
        }
        if (mapRender != null) {
            // save memory
            mapRender.clear();
        }
        if (null != headingManager) {
                headingManager.stopSensor();
        }
    }

    @Override
    public void onResume() {
        Logging.info( "MAP: onResume" );
        super.onResume();
        if (mapRender != null) {
            mapRender.onResume();
        }
        if (null != tileOverlay) {
            //DEBUG: MainActivity.info("clearing tile overlay cache");
            if (null != mapView) {
                //refresh tiles on resume
                mapView.postInvalidate();
            }
        }

        setupTimer();
        final Activity a = getActivity();
        if (null != a) {
            a.setTitle(R.string.mapping_app_name);
        }
        if (null != headingManager) {
            headingManager.startSensor();
        }
        mapView.onResume();
    }

    @Override
    public void onSaveInstanceState(@NonNull final Bundle outState) {
        Logging.info( "MAP: onSaveInstanceState" );
        super.onSaveInstanceState(outState);
        mapView.onSaveInstanceState(outState);
    }

    @Override
    public void onLowMemory() {
        Logging.info( "MAP: onLowMemory" );
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
    public void onCreateOptionsMenu (@NonNull final Menu menu, @NonNull final MenuInflater inflater) {
        Logging.info( "MAP: onCreateOptionsMenu" );
        MenuItem item;
        final Activity a = getActivity();
        if (null != a) {
            final SharedPreferences prefs = a.getSharedPreferences(PreferenceKeys.SHARED_PREFS, 0);
            final boolean showNewDBOnly = prefs.getBoolean(PreferenceKeys.PREF_MAP_ONLY_NEWDB, false);
            final boolean showLabel = prefs.getBoolean(PreferenceKeys.PREF_MAP_LABEL, true);
            final boolean showCluster = prefs.getBoolean(PreferenceKeys.PREF_MAP_CLUSTER, true);
            final boolean showTraffic = prefs.getBoolean(PreferenceKeys.PREF_MAP_TRAFFIC, true);

            String nameLabel = showLabel ? getString(R.string.menu_labels_off) : getString(R.string.menu_labels_on);
            item = menu.add(0, MENU_LABEL, 0, nameLabel);
            item.setIcon(android.R.drawable.ic_dialog_info);

            String nameCluster = showCluster ? getString(R.string.menu_cluster_off) : getString(R.string.menu_cluster_on);
            item = menu.add(0, MENU_CLUSTER, 0, nameCluster);
            item.setIcon(android.R.drawable.ic_menu_add);

            String nameTraffic = showTraffic ? getString(R.string.menu_traffic_off) : getString(R.string.menu_traffic_on);
            item = menu.add(0, MENU_TRAFFIC, 0, nameTraffic);
            item.setIcon(android.R.drawable.ic_menu_directions);

            item = menu.add(0, MENU_MAP_TYPE, 0, getString(R.string.menu_map_type));
            item.setIcon(android.R.drawable.ic_menu_mapmode);
            MenuItemCompat.setShowAsAction(item, MenuItem.SHOW_AS_ACTION_IF_ROOM);

            item = menu.add(0, MENU_FILTER, 0, getString(R.string.settings_map_head));
            item.setIcon(android.R.drawable.ic_menu_search);
            MenuItemCompat.setShowAsAction(item, MenuItem.SHOW_AS_ACTION_IF_ROOM);

            String name = state.locked ? getString(R.string.menu_turn_off_lockon) : getString(R.string.menu_turn_on_lockon);
            item = menu.add(0, MENU_TOGGLE_LOCK, 0, name);
            item.setIcon(android.R.drawable.ic_lock_lock);

            String nameDB = showNewDBOnly ? getString(R.string.menu_show_old) : getString(R.string.menu_show_new);
            item = menu.add(0, MENU_TOGGLE_NEWDB, 0, nameDB);
            item.setIcon(android.R.drawable.ic_menu_edit);
        }
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
    public boolean onOptionsItemSelected(@NonNull final MenuItem item ) {
        final Activity a = getActivity();
        if (null != a) {
            final SharedPreferences prefs = a.getSharedPreferences(PreferenceKeys.SHARED_PREFS, 0);
            switch (item.getItemId()) {
                case MENU_ZOOM_IN: {
                    mapView.getMapAsync(googleMap -> {
                        float zoom = googleMap.getCameraPosition().zoom;
                        zoom++;
                        final CameraUpdate zoomUpdate = CameraUpdateFactory.zoomTo(zoom);
                        googleMap.animateCamera(zoomUpdate);
                    });
                    return true;
                }
                case MENU_ZOOM_OUT: {
                    mapView.getMapAsync(googleMap -> {
                        float zoom = googleMap.getCameraPosition().zoom;
                        zoom--;
                        final CameraUpdate zoomUpdate = CameraUpdateFactory.zoomTo(zoom);
                        googleMap.animateCamera(zoomUpdate);
                    });
                    return true;
                }
                case MENU_TOGGLE_LOCK: {
                    state.locked = !state.locked;
                    String name = state.locked ? getString(R.string.menu_turn_off_lockon) : getString(R.string.menu_turn_on_lockon);
                    item.setTitle(name);
                    return true;
                }
                case MENU_TOGGLE_NEWDB: {
                    final boolean showNewDBOnly = !prefs.getBoolean(PreferenceKeys.PREF_MAP_ONLY_NEWDB, false);
                    Editor edit = prefs.edit();
                    edit.putBoolean(PreferenceKeys.PREF_MAP_ONLY_NEWDB, showNewDBOnly);
                    edit.apply();

                    String name = showNewDBOnly ? getString(R.string.menu_show_old) : getString(R.string.menu_show_new);
                    item.setTitle(name);
                    if (mapRender != null) {
                        mapRender.reCluster();
                    }
                    return true;
                }
                case MENU_LABEL: {
                    final boolean showLabel = !prefs.getBoolean(PreferenceKeys.PREF_MAP_LABEL, true);
                    Editor edit = prefs.edit();
                    edit.putBoolean(PreferenceKeys.PREF_MAP_LABEL, showLabel);
                    edit.apply();

                    String name = showLabel ? getString(R.string.menu_labels_off) : getString(R.string.menu_labels_on);
                    item.setTitle(name);

                    if (mapRender != null) {
                        mapRender.reCluster();
                    }
                    return true;
                }
                case MENU_CLUSTER: {
                    final boolean showCluster = !prefs.getBoolean(PreferenceKeys.PREF_MAP_CLUSTER, true);
                    Editor edit = prefs.edit();
                    edit.putBoolean(PreferenceKeys.PREF_MAP_CLUSTER, showCluster);
                    edit.apply();

                    String name = showCluster ? getString(R.string.menu_cluster_off) : getString(R.string.menu_cluster_on);
                    item.setTitle(name);

                    if (mapRender != null) {
                        mapRender.reCluster();
                    }
                    return true;
                }
                case MENU_TRAFFIC: {
                    final boolean showTraffic = !prefs.getBoolean(PreferenceKeys.PREF_MAP_TRAFFIC, true);
                    Editor edit = prefs.edit();
                    edit.putBoolean(PreferenceKeys.PREF_MAP_TRAFFIC, showTraffic);
                    edit.apply();

                    String name = showTraffic ? getString(R.string.menu_traffic_off) : getString(R.string.menu_traffic_on);
                    item.setTitle(name);
                    mapView.getMapAsync(googleMap -> googleMap.setTrafficEnabled(showTraffic));
                    return true;
                }
                case MENU_FILTER: {
                    final Intent intent = new Intent(getActivity(), MapFilterActivity.class);
                    getActivity().startActivityForResult(intent, UPDATE_MAP_FILTER);
                    return true;
                }
                case MENU_MAP_TYPE: {
                    mapView.getMapAsync(googleMap -> {
                        int newMapType = prefs.getInt(PreferenceKeys.PREF_MAP_TYPE, MAP_TYPE_NORMAL);
                        final Activity a1 = getActivity();
                        switch (newMapType) {
                            case MAP_TYPE_NORMAL:
                                newMapType = MAP_TYPE_SATELLITE;
                                WiGLEToast.showOverActivity(a1, R.string.tab_map, getString(R.string.map_toast_satellite), Toast.LENGTH_SHORT);
                                break;
                            case MAP_TYPE_SATELLITE:
                                newMapType = MAP_TYPE_HYBRID;
                                WiGLEToast.showOverActivity(a1, R.string.tab_map, getString(R.string.map_toast_hybrid), Toast.LENGTH_SHORT);
                                break;
                            case MAP_TYPE_HYBRID:
                                newMapType = MAP_TYPE_TERRAIN;
                                WiGLEToast.showOverActivity(a1, R.string.tab_map, getString(R.string.map_toast_terrain), Toast.LENGTH_SHORT);
                                break;
                            case MAP_TYPE_TERRAIN:
                                newMapType = MAP_TYPE_NORMAL;
                                WiGLEToast.showOverActivity(a1, R.string.tab_map, getString(R.string.map_toast_normal), Toast.LENGTH_SHORT);
                                break;
                            default:
                                Logging.error("unhandled mapType: " + newMapType);
                        }
                        Editor edit = prefs.edit();
                        edit.putInt(PreferenceKeys.PREF_MAP_TYPE, newMapType);
                        edit.apply();
                        googleMap.setMapType(newMapType);
                    });
                }
                case MENU_WAKELOCK: {
                    boolean screenLocked = !MainActivity.isScreenLocked(this);
                    MainActivity.setLockScreen(this, screenLocked);
                    final String wake = screenLocked ? getString(R.string.menu_screen_sleep) : getString(R.string.menu_screen_wake);
                    item.setTitle(wake);
                    return true;
                }
            }
        }
        return false;
    }

    public static class MapDialogFragment extends DialogFragment {
        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container,
                                 Bundle savedInstanceState) {

            Bundle args = getArguments();
            final String prefix = null != args? args.getString(DIALOG_PREFIX):"";

            final Dialog dialog = getDialog();
            final Activity activity = getActivity();
            final View view = inflater.inflate(R.layout.filterdialog, container);
            if (null != dialog) {
                dialog.setTitle(R.string.ssid_filter_head);
            }
            Logging.info("make new dialog. prefix: " + prefix);
            if (null != activity) {
                final SharedPreferences prefs = activity.getSharedPreferences(PreferenceKeys.SHARED_PREFS, 0);
                final EditText regex = view.findViewById(R.id.edit_regex);
                regex.setText(prefs.getString(prefix + PreferenceKeys.PREF_MAPF_REGEX, ""));

                final CheckBox invert = PrefsBackedCheckbox.prefSetCheckBox(activity, view, R.id.showinvert,
                        prefix + PreferenceKeys.PREF_MAPF_INVERT, false, prefs);
                final CheckBox open = PrefsBackedCheckbox.prefSetCheckBox(activity, view, R.id.showopen,
                        prefix + PreferenceKeys.PREF_MAPF_OPEN, true, prefs);
                final CheckBox wep = PrefsBackedCheckbox.prefSetCheckBox(activity, view, R.id.showwep,
                        prefix + PreferenceKeys.PREF_MAPF_WEP, true, prefs);
                final CheckBox wpa = PrefsBackedCheckbox.prefSetCheckBox(activity, view, R.id.showwpa,
                        prefix + PreferenceKeys.PREF_MAPF_WPA, true, prefs);
                final CheckBox cell = PrefsBackedCheckbox.prefSetCheckBox(activity, view, R.id.showcell,
                        prefix + PreferenceKeys.PREF_MAPF_CELL, true, prefs);
                final CheckBox enabled = PrefsBackedCheckbox.prefSetCheckBox(activity, view, R.id.enabled,
                        prefix + PreferenceKeys.PREF_MAPF_ENABLED, true, prefs);

                Button ok = view.findViewById(R.id.ok_button);
                ok.setOnClickListener(buttonView -> {
                    try {
                        final Editor editor = prefs.edit();
                        editor.putString(prefix + PreferenceKeys.PREF_MAPF_REGEX, regex.getText().toString());
                        editor.putBoolean(prefix + PreferenceKeys.PREF_MAPF_INVERT, invert.isChecked());
                        editor.putBoolean(prefix + PreferenceKeys.PREF_MAPF_OPEN, open.isChecked());
                        editor.putBoolean(prefix + PreferenceKeys.PREF_MAPF_WEP, wep.isChecked());
                        editor.putBoolean(prefix + PreferenceKeys.PREF_MAPF_WPA, wpa.isChecked());
                        editor.putBoolean(prefix + PreferenceKeys.PREF_MAPF_CELL, cell.isChecked());
                        editor.putBoolean(prefix + PreferenceKeys.PREF_MAPF_ENABLED, enabled.isChecked());
                        editor.apply();
                        MainActivity.reclusterMap();

                        if (null != dialog) {
                            dialog.dismiss();
                        }
                    } catch (Exception ex) {
                        // guess it wasn't there anyways
                        Logging.info("exception dismissing filter dialog: " + ex);
                    }
                });

                Button cancel = view.findViewById(R.id.cancel_button);
                cancel.setOnClickListener(buttonView -> {
                    try {
                        regex.setText(prefs.getString(prefix + PreferenceKeys.PREF_MAPF_REGEX, ""));
                        PrefsBackedCheckbox.prefSetCheckBox(activity, view, R.id.showinvert,
                                prefix + PreferenceKeys.PREF_MAPF_INVERT, false, prefs);
                        PrefsBackedCheckbox.prefSetCheckBox(activity, view, R.id.showopen,
                                prefix + PreferenceKeys.PREF_MAPF_OPEN, true, prefs);
                        PrefsBackedCheckbox.prefSetCheckBox(activity, view, R.id.showwep,
                                prefix + PreferenceKeys.PREF_MAPF_WEP, true, prefs);
                        PrefsBackedCheckbox.prefSetCheckBox(activity, view, R.id.showwpa,
                                prefix + PreferenceKeys.PREF_MAPF_WPA, true, prefs);
                        PrefsBackedCheckbox.prefSetCheckBox(activity, view, R.id.showcell,
                                prefix + PreferenceKeys.PREF_MAPF_CELL, true, prefs);
                        PrefsBackedCheckbox.prefSetCheckBox(activity, view, R.id.enabled,
                                prefix + PreferenceKeys.PREF_MAPF_ENABLED, true, prefs);

                        if (null != dialog) {
                            dialog.dismiss();
                        }
                    } catch (Exception ex) {
                        // guess it wasn't there anyways
                        Logging.info("exception dismissing filter dialog: " + ex);
                    }
                });
            }
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
        if (ListFragment.lameStatic.dbHelper != null) {
            final int cacheSize = MainActivity.getNetworkCache().size();
            if (cacheSize > (ListFragment.lameStatic.networkCache.maxSize() / 4)) {
                // don't load, there's already networks to show
                Logging.info("cacheSize: " + cacheSize + ", skipping previous networks");
                return;
            }

            final String sql = "SELECT bssid FROM "
                    + DatabaseHelper.LOCATION_TABLE + " ORDER BY _id DESC LIMIT ?";

            final PooledQueryExecutor.Request request = new PooledQueryExecutor.Request( sql,
                new String[]{(ListFragment.lameStatic.networkCache.maxSize() * 2)+""},
                new PooledQueryExecutor.ResultHandler() {
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
                                Logging.info("Cache is full, breaking out of query result handling");
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
            }, ListFragment.lameStatic.dbHelper);
            PooledQueryExecutor.enqueue(request);
        }
    }
    private static final PointExtractor<LatLng> latLngPointExtractor = new PointExtractor<LatLng>() {
        @Override
        public double getX(LatLng point) {
            return point.latitude * 1000000;
        }

        @Override
        public double getY(LatLng point) {
            return point.longitude * 1000000;
        }
    };

    public static int getRouteColorForMapType(final int mapType, final boolean nightMode) {
        if (nightMode) {
                return OVERLAY_LIGHT;
        } else if (mapType != MAP_TYPE_NORMAL && mapType != MAP_TYPE_TERRAIN
                && mapType != MAP_TYPE_NONE) {
            return OVERLAY_LIGHT;
        }
        return OVERLAY_DARK;
    }
}
