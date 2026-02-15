package net.wigle.wigleandroid;

import static com.google.android.gms.maps.GoogleMap.*;

import java.util.Arrays;
import java.util.List;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.location.Location;
import android.os.Bundle;
import android.os.Handler;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.FragmentActivity;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RelativeLayout;
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
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.android.gms.maps.model.TileOverlay;
import com.google.android.gms.maps.model.TileOverlayOptions;
import com.google.android.gms.maps.model.TileProvider;

import net.wigle.wigleandroid.background.PooledQueryExecutor;
import net.wigle.wigleandroid.db.DatabaseHelper;
import net.wigle.wigleandroid.model.ConcurrentLinkedHashMap;
import net.wigle.wigleandroid.model.LatLng;
import net.wigle.wigleandroid.model.Network;
import net.wigle.wigleandroid.net.WiGLEApiManager;
import net.wigle.wigleandroid.ui.ThemeUtil;
import net.wigle.wigleandroid.ui.WiGLEToast;
import net.wigle.wigleandroid.util.Logging;
import net.wigle.wigleandroid.util.MapUtil;
import net.wigle.wigleandroid.util.PreferenceKeys;

/**
 * Show a map depicting current position and configurable stumbling progress information.
 * Google Maps-specific version
 * [delete this file for FOSS build]
 */
public final class MappingFragment extends AbstractMappingFragment {

    private MapView mapView;
    private MapRender mapRender;

    private final Handler timer = new Handler();
    private TileOverlay tileOverlay;
    private Polyline routePolyline;

    final Runnable mUpdateTimeTask = new MappingFragment.MapRunnable();

    @Override
    protected int getLayoutResourceId() {
        return R.layout.map;
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
                } catch (final SecurityException ex) {
                    Logging.error("security exception onCreateView map: " + ex, ex);
                }
            } else {
                final FragmentActivity fa = getActivity();
                if (null != fa) {
                    WiGLEToast.showOverFragment(fa, R.string.fatal_pre_message,
                            fa.getResources().getString(R.string.map_needs_playservice));
                }
            }
            MapsInitializer.initialize(a);
        }
        final View view = inflater.inflate(getLayoutResourceId(), container, false);

        final LatLng oldCenter = state.oldCenter != null ? state.oldCenter : null;
        int oldZoom = Integer.MIN_VALUE;
        setupMapView(view, oldCenter, oldZoom);
        return view;
    }

    @SuppressLint("MissingPermission")
    @Override
    protected void setupMapView(final View view, final LatLng oldCenter, final int oldZoom) {
        setupMapViewContainer(view);

        final Activity a = getActivity();
        final SharedPreferences prefs = (a != null) ? a.getSharedPreferences(PreferenceKeys.SHARED_PREFS, 0) : null;
        final boolean visualizeRoute = prefs != null && prefs.getBoolean(PreferenceKeys.PREF_VISUALIZE_ROUTE, false);

        mapView.getMapAsync(googleMap -> {
            configureMapSettings(googleMap, prefs);
            setupLocationTracking(googleMap, prefs);
            setupCameraListeners(googleMap);
            setupTileOverlay(googleMap, prefs);
            setupRouteVisualization(googleMap, prefs, visualizeRoute);
            initializeCameraPosition(googleMap, oldCenter, oldZoom, prefs);
        });
        Logging.info("done setupMapView.");
    }

    /**
     * Sets up the map view container and adds the map view to it.
     */
    private void setupMapViewContainer(final View view) {
        final RelativeLayout rlView = view.findViewById(R.id.map_rl);
        if (mapView != null) {
            ViewGroup.LayoutParams params = new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            mapView.setLayoutParams(params);
        }
        rlView.addView(mapView);
    }

    /**
     * Configures basic map settings like map type, traffic, buildings, and indoor.
     */
    private void configureMapSettings(final com.google.android.gms.maps.GoogleMap googleMap,
                                      final SharedPreferences prefs) {
        googleMap.setBuildingsEnabled(true);
        // Seeing stack overflow crashes on multiple phones in specific locations, based on indoor svcs.
        googleMap.setIndoorEnabled(false);

        if (prefs != null) {
            final boolean showTraffic = prefs.getBoolean(PreferenceKeys.PREF_MAP_TRAFFIC, true);
            googleMap.setTrafficEnabled(showTraffic);
            final int mapType = prefs.getInt(PreferenceKeys.PREF_MAP_TYPE, MAP_TYPE_NORMAL);
            googleMap.setMapType(mapType);
        } else {
            googleMap.setMapType(MAP_TYPE_NORMAL);
        }

        final Activity a = getActivity();
        if (a != null) {
            mapRender = new MapRender(a, googleMap, false);
        }
    }

    /**
     * Sets up location tracking and my location button functionality.
     */
    @SuppressLint("MissingPermission")
    private void setupLocationTracking(final com.google.android.gms.maps.GoogleMap googleMap,
                                       final SharedPreferences prefs) {
        final Context c = getContext();
        if (c == null) {
            return;
        }
        if (ActivityCompat.checkSelfPermission(c, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || ActivityCompat.checkSelfPermission(c, android.Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            googleMap.setMyLocationEnabled(true);
            googleMap.setOnCameraIdleListener(() -> {
                if (prefs != null && prefs.getBoolean(PreferenceKeys.PREF_MAP_FOLLOW_BEARING, false)) {
                    Float cameraBearing = getBearing(getActivity());
                    Logging.info("Camera Bearing: " + cameraBearing);
                    if (cameraBearing != null) {
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

    public static int getRouteColorForMapType(final int mapType, final boolean nightMode) {
        if (nightMode) {
            return OVERLAY_LIGHT;
        } else if (mapType != MAP_TYPE_NORMAL && mapType != MAP_TYPE_TERRAIN
                && mapType != MAP_TYPE_NONE) {
            return OVERLAY_LIGHT;
        }
        return OVERLAY_DARK;
    }

    /**
     * Sets up camera event listeners for lock state management.
     */
    private void setupCameraListeners(final com.google.android.gms.maps.GoogleMap googleMap) {
        googleMap.setOnMyLocationButtonClickListener(() -> {
            if (!state.locked) {
                state.locked = true;
                updateLockMenuItemTitle();
                Logging.info("on-my-location received - activating lock");
            }
            return false;
        });

        googleMap.setOnCameraMoveStartedListener(reason -> {
            if (reason == OnCameraMoveStartedListener.REASON_GESTURE) {
                if (state.locked) {
                    state.locked = false;
                    updateLockMenuItemTitle();
                }
            } else if (reason == OnCameraMoveStartedListener.REASON_API_ANIMATION) {
                //DEBUG: MainActivity.info("Camera moved due to user tap");
                //TODO: should we combine this case with REASON_GESTURE?
            } else if (reason == OnCameraMoveStartedListener.REASON_DEVELOPER_ANIMATION) {
                //MainActivity.info("Camera moved due to app directive");
            }
        });
    }

    /**
     * Sets up the tile overlay for WiGLE network discovery tiles.
     */
    private void setupTileOverlay(final com.google.android.gms.maps.GoogleMap googleMap,
                                  final SharedPreferences prefs) {
        if (prefs == null || PreferenceKeys.PREF_MAP_NO_TILE.equals(
                prefs.getString(PreferenceKeys.PREF_SHOW_DISCOVERED, PreferenceKeys.PREF_MAP_NO_TILE))) {
            return;
        }

        final int providerTileRes = MainActivity.isHighDefinition() ? 512 : 256;
        final String authToken = MapUtil.createAuthToken(prefs);
        final String userAgent = WiGLEApiManager.USER_AGENT;
        TileProvider tileProvider = new WiGLETileProvider(prefs, userAgent, authToken, providerTileRes);

        tileOverlay = googleMap.addTileOverlay(new TileOverlayOptions()
                .tileProvider(tileProvider).transparency(0.35f));
    }

    /**
     * Sets up route visualization polyline if enabled.
     */
    private void setupRouteVisualization(final com.google.android.gms.maps.GoogleMap googleMap,
                                         final SharedPreferences prefs,
                                         final boolean visualizeRoute) {
        if (prefs == null || !visualizeRoute) {
            return;
        }

        final int mapMode = prefs.getInt(PreferenceKeys.PREF_MAP_TYPE, MAP_TYPE_NORMAL);
        final boolean nightMode = ThemeUtil.shouldUseMapNightMode(getContext(), prefs);

        loadRoutePointsInBackground(prefs, routePoints -> {
            Logging.info("Loaded route with " + routePoints.size() + " segments");
            PolylineOptions pOptions = new PolylineOptions().clickable(false);
            for (LatLng pt : routePoints) {
                pOptions.add(new com.google.android.gms.maps.model.LatLng(pt.latitude, pt.longitude));
            }
            pOptions.color(getRouteColorForMapType(mapMode, nightMode));
            pOptions.width(ROUTE_WIDTH);
            pOptions.zIndex(10000); // to overlay on traffic data
            routePolyline = googleMap.addPolyline(pOptions);
            routePolyline.setTag(ROUTE_LINE_TAG);
        });
    }

    /**
     * Initializes the camera position based on saved state or current location.
     */
    private void initializeCameraPosition(final com.google.android.gms.maps.GoogleMap googleMap,
                                          final LatLng oldCenter,
                                          final int oldZoom,
                                          final SharedPreferences prefs) {
        final LatLng centerPoint = getCenter(getActivity(), oldCenter, previousLocation);
        float zoom = DEFAULT_ZOOM;
        if (oldZoom >= 0) {
            zoom = oldZoom;
        } else if (prefs != null) {
            zoom = prefs.getFloat(PreferenceKeys.PREF_PREV_ZOOM, zoom);
        }

        final CameraPosition cameraPosition = new CameraPosition.Builder()
                .target(new com.google.android.gms.maps.model.LatLng(centerPoint.latitude, centerPoint.longitude))
                .zoom(zoom)
                .build();
        googleMap.moveCamera(CameraUpdateFactory.newCameraPosition(cameraPosition));
    }

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
                                    CameraUpdateFactory.newLatLng(
                                            new com.google.android.gms.maps.model.LatLng(locLatLng.latitude, locLatLng.longitude)) :
                                    CameraUpdateFactory.newCameraPosition(
                                        new CameraPosition.Builder().bearing(cameraBearing).zoom(currentZoom).target(
                                                new com.google.android.gms.maps.model.LatLng(locLatLng.latitude, locLatLng.longitude)).build());
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
                        if (showRoute) {
                            updateRoutePolyline(location, prefs);
                        }
                    } catch (Exception ex) {
                        Logging.error("Route point update failed: ", ex);
                    }

                    // set if location isn't null
                    previousLocation = location;
                }


                previousRunNets = ListFragment.lameStatic.runNets;

                if (view != null) {
                    updateStatsViews(view, prefs);
                }

                final long period = 1000L;
                // info("wifitimer: " + period );
                timer.postDelayed( this, period );
            }
            else {
                Logging.info( "finishing - skipping mapping timed update" );
            }
        }
    }

    /**
     * Updates the route polyline with a new location point if conditions are met.
     *
     * @param location The new location to add to the route
     * @param prefs SharedPreferences for accessing map settings
     */
    private void updateRoutePolyline(final Location location, final SharedPreferences prefs) {
        if (!shouldUpdateRoute(location)) {
            return;
        }

        if (routePolyline == null) {
            Logging.error("route polyline null - this shouldn't happen");
            return;
        }

        final List<com.google.android.gms.maps.model.LatLng> routePoints = routePolyline.getPoints();
        routePoints.add(new com.google.android.gms.maps.model.LatLng(
                location.getLatitude(), location.getLongitude()));

        updateRouteColor(routePolyline, prefs);
        simplifyRouteIfNeeded(routePoints);
        lastLocation = location;
    }

    /**
     * Simplifies the route polyline if it exceeds performance thresholds.
     * Sets the simplified points on the polyline if simplification occurs,
     * otherwise sets the original points.
     *
     * @param routePoints The list of route points to potentially simplify
     */
    private void simplifyRouteIfNeeded(final List<com.google.android.gms.maps.model.LatLng> routePoints) {
        if (routePoints.size() > POLYLINE_PERF_THRESHOLD_COARSE) {
            performCoarseSimplification(routePoints);
        } else if (routePoints.size() > POLYLINE_PERF_THRESHOLD_FINE) {
            performFineSimplification(routePoints);
        } else {
            routePolyline.setPoints(routePoints);
        }
    }

    /**
     * Performs coarse simplification using Radial-Distance algorithm.
     *
     * @param routePoints The route points to simplify
     */
    private void performCoarseSimplification(final List<com.google.android.gms.maps.model.LatLng> routePoints) {
        Simplify<com.google.android.gms.maps.model.LatLng> simplify = new Simplify<>(
                new com.google.android.gms.maps.model.LatLng[0], latLngPointExtractor);
        com.google.android.gms.maps.model.LatLng[] simplified = simplify.simplify(
                routePoints.toArray(new com.google.android.gms.maps.model.LatLng[0]),
                POLYLINE_TOLERANCE_COARSE, false);
        routePolyline.setPoints(Arrays.asList(simplified));
        Logging.error("major route simplification: " + routePoints.size() + "->" + simplified.length);
    }

    /**
     * Performs fine simplification using Douglas-Peucker algorithm.
     *
     * @param routePoints The route points to simplify
     */
    private void performFineSimplification(final List<com.google.android.gms.maps.model.LatLng> routePoints) {
        Simplify<com.google.android.gms.maps.model.LatLng> simplify = new Simplify<>(
                new com.google.android.gms.maps.model.LatLng[0], latLngPointExtractor);
        com.google.android.gms.maps.model.LatLng[] simplified = simplify.simplify(
                routePoints.toArray(new com.google.android.gms.maps.model.LatLng[0]),
                POLYLINE_TOLERANCE_FINE, true);
        routePolyline.setPoints(Arrays.asList(simplified));
        Logging.error("minor route simplification: " + routePoints.size() + "->" + simplified.length);
    }

    /**
     * Updates the route polyline color based on map type and night mode.
     *
     * @param polyline The polyline to update
     * @param prefs SharedPreferences for accessing map settings
     */
    private void updateRouteColor(final Polyline polyline, final SharedPreferences prefs) {
        final int mapMode = prefs.getInt(PreferenceKeys.PREF_MAP_TYPE, MAP_TYPE_NORMAL);
        final boolean nightMode = ThemeUtil.shouldUseMapNightMode(getContext(), prefs);
        polyline.setColor(getRouteColorForMapType(mapMode, nightMode));
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
            state.oldCenter = new LatLng(googleMap.getCameraPosition().target.latitude,
                    googleMap.getCameraPosition().target.longitude);
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

    @Override
    public void addNetwork(final Network network) {
        if (mapRender != null && mapRender.okForMapTab(network)) {
            mapRender.addItem(network);
        }
    }

    private void setupTimer() {
        timer.removeCallbacks(mUpdateTimeTask);
        timer.postDelayed(mUpdateTimeTask, 250);
    }

    @Override
    public void updateNetwork(final Network network) {
        if (mapRender != null) {
            mapRender.updateNetwork(network);
        }
    }

    @Override
    public void reCluster() {
        if (mapRender != null) {
            mapRender.reCluster();
        }
    }


    protected void setupQuery() {
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

    @Override
    public boolean onOptionsItemSelected(@NonNull final MenuItem item) {
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
                    SharedPreferences.Editor edit = prefs.edit();
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
                    SharedPreferences.Editor edit = prefs.edit();
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
                    SharedPreferences.Editor edit = prefs.edit();
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
                    SharedPreferences.Editor edit = prefs.edit();
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
                        SharedPreferences.Editor edit = prefs.edit();
                        edit.putInt(PreferenceKeys.PREF_MAP_TYPE, newMapType);
                        edit.apply();
                        googleMap.setMapType(newMapType);
                    });
                    return true;
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

    private static final PointExtractor<com.google.android.gms.maps.model.LatLng> latLngPointExtractor = new PointExtractor<com.google.android.gms.maps.model.LatLng>() {
        @Override
        public double getX(com.google.android.gms.maps.model.LatLng point) {
            return point.latitude * 1000000;
        }

        @Override
        public double getY(com.google.android.gms.maps.model.LatLng point) {
            return point.longitude * 1000000;
        }
    };

}
