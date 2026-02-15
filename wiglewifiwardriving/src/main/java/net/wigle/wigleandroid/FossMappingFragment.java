package net.wigle.wigleandroid;

import static net.wigle.wigleandroid.util.PreferenceKeys.PREF_FOSS_MAPS_VECTOR_TILE_KEY;
import static net.wigle.wigleandroid.util.PreferenceKeys.PREF_FOSS_MAPS_VECTOR_TILE_STYLE;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;

import com.goebl.simplify.PointExtractor;
import com.goebl.simplify.Simplify;

import net.wigle.wigleandroid.model.LatLng;
import net.wigle.wigleandroid.model.Network;
import net.wigle.wigleandroid.ui.ThemeUtil;
import net.wigle.wigleandroid.util.Logging;
import net.wigle.wigleandroid.util.PreferenceKeys;

import org.maplibre.android.MapLibre;
import org.maplibre.android.annotations.Polyline;
import org.maplibre.android.annotations.PolylineOptions;
import org.maplibre.android.camera.CameraPosition;
import org.maplibre.android.camera.CameraUpdateFactory;
import org.maplibre.android.gestures.MoveGestureDetector;
import org.maplibre.android.location.LocationComponent;
import org.maplibre.android.location.LocationComponentActivationOptions;
import org.maplibre.android.location.LocationComponentOptions;
import org.maplibre.android.location.engine.LocationEngineRequest;
import org.maplibre.android.location.modes.CameraMode;
import org.maplibre.android.maps.MapLibreMap;
import org.maplibre.android.maps.MapView;
import org.maplibre.android.maps.Style;
import org.maplibre.android.style.layers.Layer;
import org.maplibre.android.style.layers.RasterLayer;
import org.maplibre.android.style.layers.Property;
import org.maplibre.android.style.layers.PropertyFactory;
import org.maplibre.android.style.sources.RasterSource;

import java.util.Arrays;
import java.util.List;

public class FossMappingFragment extends AbstractMappingFragment {

    private static final float ZOOM_MODIFIER = 1; //ALIBI: Google maps and MapLibe have an off-by-one zoom difference
    private MapView mapView;

    private FossMapRender mapRender;

    private Polyline routePolyline;

    private final Handler timer = new Handler();

    final Runnable mUpdateMapTask = new MapUpdateRunnable();

    @Override
    protected int getLayoutResourceId() {
        return R.layout.foss_map;
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        final Activity a = getActivity();
        if (null != a) {
            MapLibre.getInstance(a);
            final View view = inflater.inflate(getLayoutResourceId(), container, false);
            mapView = view.findViewById(R.id.mapLibreMap);
            if (mapView != null) {
                try {
                    mapView.onCreate(savedInstanceState);
                } catch (Exception ex) {
                    Logging.error("FossMappingFragment mapView.onCreate failed: " + ex, ex);
                }
            }
            //TODO: theming for MapLibre
            final LatLng oldCenter = state.oldCenter != null ? state.oldCenter : null;
            int oldZoom = Integer.MIN_VALUE;
            setupMapView(view, oldCenter, oldZoom);
            return view;
        }
        return null;
    }

    @Override
    protected void setupQuery() {

    }

    @SuppressLint("MissingPermission")
    @Override
    protected void setupMapView(final View view, final LatLng oldCenter, final int oldZoom) {
        final Activity a = getActivity();
        final SharedPreferences prefs = (a != null) ? a.getSharedPreferences(PreferenceKeys.SHARED_PREFS, 0) : null;
        final boolean visualizeRoute = prefs != null && prefs.getBoolean(PreferenceKeys.PREF_VISUALIZE_ROUTE, false);

        mapView.getMapAsync(mapLibreMap -> {
            final String mapServerKey = prefs != null ?
                    prefs.getString(PREF_FOSS_MAPS_VECTOR_TILE_KEY, null) : null;
            final String mapServerUrl = prefs != null ?
                    prefs.getString(PREF_FOSS_MAPS_VECTOR_TILE_STYLE, null) : null;

            //TODO: day/night style?
            String styleUrl;
            if (mapServerKey != null && !mapServerKey.isEmpty()) {
                styleUrl = mapServerUrl + mapServerKey;
                //e.g. "https://api.maptiler.com/maps/streets-v2/style.json?key=" + mapServerKey;
            } else {
                styleUrl = "https://demotiles.maplibre.org/style.json";
            }
            mapLibreMap.setStyle(styleUrl, style -> {
                final Activity activity = getActivity();
                if (activity != null) {
                    mapRender = new FossMapRender(activity, mapLibreMap, false);
                }
                configureMapSettings(mapLibreMap, prefs, style);
                setupLocationTracking(mapLibreMap, prefs, style);
                setupCameraListeners(mapLibreMap, prefs, style);
                setupTileOverlay(mapLibreMap, prefs, style);
                setupRouteVisualization(mapLibreMap, prefs, visualizeRoute);
                initializeCameraPosition(mapLibreMap, oldCenter, oldZoom, prefs);
            });
        });
        setupCenterLocationButton(view);
    }

    private void configureMapSettings(final MapLibreMap map,
                                      final SharedPreferences prefs, final Style style) {
        // TODO: see what we can achieve compared to
        //buildings
        //traffic
        //map types: normal, satellite, hybrid, terrain
    }

    /**
     * Sets up location tracking and my location button functionality.
     */
    @SuppressLint("MissingPermission")
    private void setupLocationTracking(final MapLibreMap map,
                                       final SharedPreferences prefs, final Style style) {
        final Context c = getContext();
        if (c == null) {
            return;
        }
        if (ActivityCompat.checkSelfPermission(c, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || ActivityCompat.checkSelfPermission(c, android.Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                final LocationComponent locationComponent = map.getLocationComponent();
            LocationComponentOptions locationComponentOptions =
                LocationComponentOptions.builder(c).build();
            LocationComponentActivationOptions locationComponentActivationOptions =
                    buildLocationComponentActivationOptions(c, style, locationComponentOptions);
            locationComponent.activateLocationComponent(locationComponentActivationOptions);
            locationComponent.setLocationComponentEnabled(true);
            locationComponent.setCameraMode(CameraMode.TRACKING);
            locationComponent.forceLocationUpdate(lastLocation);

            map.addOnCameraIdleListener(() -> {
                if (prefs != null && prefs.getBoolean(PreferenceKeys.PREF_MAP_FOLLOW_BEARING, false)) {
                    Float cameraBearing = getBearing(getActivity());
                    //DEBUG: Logging.info("Camera Bearing: " + cameraBearing);
                    if (cameraBearing != null) {
                        CameraPosition camPos = new CameraPosition
                                .Builder(map.getCameraPosition())
                                .bearing(cameraBearing)
                                .build();
                        map.animateCamera(CameraUpdateFactory.newCameraPosition(camPos));
                    }
                }
            });
        }
    }

    /**
     * Sets up camera event listeners for lock state management.
     */
    private void setupCameraListeners(final MapLibreMap map, final SharedPreferences prefs, final Style style) {
        map.addOnMoveListener(new MapLibreMap.OnMoveListener() {
            @Override
            public void onMoveBegin(@NonNull MoveGestureDetector moveGestureDetector) {
                if (state.locked) {
                    state.locked = false;
                    updateLockMenuItemTitle();
                }
            }

            @Override
            public void onMove(@NonNull MoveGestureDetector moveGestureDetector) {
                //n/a
            }

            @Override
            public void onMoveEnd(@NonNull MoveGestureDetector moveGestureDetector) {
                //n/a
            }
        });
    }

    /**
     * Sets up the center location button to re-center the map on the user's current location.
     */
    @SuppressLint("MissingPermission")
    private void setupCenterLocationButton(final View view) {
        final android.widget.ImageButton centerButton = view.findViewById(R.id.center_location_button);
        if (centerButton == null) {
            return;
        }

        centerButton.setOnClickListener(v -> {
            final Location location = ListFragment.lameStatic.location;
            if (location == null) {
                Logging.info("No location available to center on");
                return;
            }
            if (!state.locked) {
                state.locked = true;
                updateLockMenuItemTitle();
                Logging.info("on-my-location received - activating lock");
            }

            mapView.getMapAsync(map -> {
                final Activity a = getActivity();
                final SharedPreferences prefs = (a != null) ?
                        a.getSharedPreferences(PreferenceKeys.SHARED_PREFS, 0) : null;

                final LatLng locLatLng = new LatLng(location.getLatitude(), location.getLongitude());
                double currentZoom = map.getCameraPosition().zoom;
                Float cameraBearing = null;
                if (null != prefs && prefs.getBoolean(PreferenceKeys.PREF_MAP_FOLLOW_BEARING, false)) {
                    cameraBearing = getBearing(a);
                }

                final org.maplibre.android.camera.CameraUpdate centerUpdate =
                        (cameraBearing == null) ?
                                CameraUpdateFactory.newLatLng(
                                        new org.maplibre.android.geometry.LatLng(
                                                locLatLng.latitude,
                                                locLatLng.longitude))
                                :
                                CameraUpdateFactory.newCameraPosition(
                                        new CameraPosition.Builder().bearing(cameraBearing)
                                                .zoom(currentZoom)
                                                .target(new org.maplibre.android.geometry.LatLng(
                                                        locLatLng.latitude,
                                                        locLatLng.longitude)).build());

                map.animateCamera(centerUpdate);
                Logging.info("Centered map on current location");
            });
        });
    }

    /**
     * Sets up the tile overlay for WiGLE network discovery tiles using RasterSource.
     * This implementation uses the same tile data source and options as WiGLETileProvider
     * for Google Maps, but adapted for MapLibre's RasterSource API.
     */
    private void setupTileOverlay(final MapLibreMap map,
                                  final SharedPreferences prefs, final Style style) {
        if (prefs == null || PreferenceKeys.PREF_MAP_NO_TILE.equals(
                prefs.getString(PreferenceKeys.PREF_SHOW_DISCOVERED, PreferenceKeys.PREF_MAP_NO_TILE))) {
            return;
        }

        try {
            // configured WiGLE auth + User-Agent
            WiGLELibreTileProvider.configureAuthentication(prefs);

            RasterSource rasterSource = WiGLELibreTileProvider.createRasterSource(
                    "WiGLE-network-overlay-source", prefs);

            RasterLayer rasterLayer = new RasterLayer(
                    "WiGLE-network-overlay-layer",
                    "WiGLE-network-overlay-source");

            rasterLayer.setProperties(
                    PropertyFactory.rasterOpacity(0.65f),
                    PropertyFactory.visibility(Property.VISIBLE)
            );

            // This is purely superstition, but removing style and layer before adding
            if (style.getSource("WiGLE-network-overlay-source") != null) {
                style.removeSource("WiGLE-network-overlay-source");
            }
            if (style.getLayer("WiGLE-network-overlay-layer") != null) {
                style.removeLayer("WiGLE-network-overlay-layer");
            }

            style.addSource(rasterSource);

            if (!style.getLayers().isEmpty()) {
                // add over the top layer
                final List<Layer> layers = style.getLayers();
                String topLayerId = layers.get(layers.size()-1).getId();
                style.addLayerAbove(rasterLayer, topLayerId);
                Logging.info("RasterLayer added above layer: " + topLayerId);
            } else {
                style.addLayer(rasterLayer);
                Logging.info("RasterLayer added (no existing layers)");
            }
        } catch (Exception e) {
            Logging.error("Failed to setup WiGLE tile overlay: " + e.getMessage(), e);
        }
    }

    /**
     * Sets up route visualization polyline if enabled.
     */
    private void setupRouteVisualization(final MapLibreMap map,
                                         final SharedPreferences prefs,
                                         final boolean visualizeRoute) {
        if (prefs == null || !visualizeRoute) {
            return;
        }

        if (routePolyline != null) {
            routePolyline.remove();
        }

        // TODO: no-clickable?
        // TODO: theme
        // TODO: final int mapMode = prefs.getInt(PreferenceKeys.PREF_MAP_TYPE, MAP_TYPE_NORMAL);
        final boolean nightMode = ThemeUtil.shouldUseMapNightMode(getContext(), prefs);

        loadRoutePointsInBackground(prefs, routePoints -> {
            Logging.info("Loaded route with " + routePoints.size() + " segments");
            PolylineOptions polylineOptions = new PolylineOptions();
            for (LatLng pt : routePoints) {
                polylineOptions.add(new org.maplibre.android.geometry.LatLng(pt.latitude, pt.longitude));
            }
            polylineOptions.color(getRouteColorForMapType(
                    1/*TODO: we don't have types yet*/,
                    nightMode));
            polylineOptions.width(ROUTE_WIDTH/2f);
            routePolyline = map.addPolyline(polylineOptions);
        });
    }

    /**
     * Initializes the camera position based on saved state or current location.
     */
    private void initializeCameraPosition(final MapLibreMap map,
                                          final LatLng oldCenter,
                                          final int oldZoom,
                                          final SharedPreferences prefs) {
        final LatLng centerPoint = getCenter(getActivity(), oldCenter, previousLocation);
        float zoom = DEFAULT_ZOOM;
        if (oldZoom >= 0) {
            zoom = oldZoom;
        } else if (prefs != null) {
            zoom = prefs.getFloat(PreferenceKeys.PREF_PREV_ZOOM, zoom) - ZOOM_MODIFIER; //ALIBI: google zoom vs mapLibre zoom (1/2)
        }
        map.setCameraPosition(
                new CameraPosition.Builder().target(
                        new org.maplibre.android.geometry.LatLng(
                            centerPoint.latitude, centerPoint.longitude))
                    .zoom(zoom).build());
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

        final List<org.maplibre.android.geometry.LatLng> routePoints = routePolyline.getPoints();
        routePoints.add(new org.maplibre.android.geometry.LatLng(
                location.getLatitude(), location.getLongitude()));

        updateRouteColor(routePolyline, prefs);
        simplifyRouteIfNeeded(routePoints);
        lastLocation = location;
    }


    @Override
    public void addNetwork(Network network) {
        if (mapRender != null && mapRender.okForMapTab(network)) {
            //DEBUG: Logging.info("Adding network to map: " + network.getBssid());
            mapRender.addItem(network);
        }
    }

    protected void setupTimer() {
        timer.removeCallbacks(mUpdateMapTask);
        timer.postDelayed(mUpdateMapTask, 250);
    }


    @Override
    public void updateNetwork(Network network) {
        if (mapRender != null) {
            Logging.info("Updating network on map: " + network.getBssid());
            mapRender.updateNetwork(network);
        }
    }

    @Override
    public void reCluster() {
        if (mapRender != null) {
            Logging.info("Re-clustering map");
            mapRender.reCluster();
        }
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        return false;
    }

    /**
     * Backing thread for camera and route updates
     */
    private class MapUpdateRunnable implements Runnable {
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
                        // Check if mapView is still valid before calling getMapAsync
                        if (mapView != null && !finishing.get()) {
                            mapView.getMapAsync(map -> {
                                // Defensive check: verify fragment is still alive before accessing map
                                if (finishing.get() || getActivity() == null) {
                                    return;
                                }
                                try {
                                    // Logging.info( "mapping center location: " + location );
                                    final LatLng locLatLng = new LatLng(location.getLatitude(), location.getLongitude());
                                    double currentZoom = map.getCameraPosition().zoom;
                                    Float cameraBearing = null;
                                    if (null != prefs && prefs.getBoolean(PreferenceKeys.PREF_MAP_FOLLOW_BEARING, false)) {
                                        cameraBearing = getBearing(a);
                                    }
                                    final org.maplibre.android.camera.CameraUpdate centerUpdate =
                                            (state.firstMove || cameraBearing == null) ?
                                                CameraUpdateFactory.newLatLng(
                                                    new org.maplibre.android.geometry.LatLng(
                                                            locLatLng.latitude,
                                                            locLatLng.longitude))
                                            :
                                                CameraUpdateFactory.newCameraPosition(
                                                    new CameraPosition.Builder().bearing(cameraBearing)
                                                            .zoom(currentZoom)
                                                            .target(new org.maplibre.android.geometry.LatLng(
                                                                    locLatLng.latitude,
                                                                    locLatLng.longitude)).build());
                                    if (state.firstMove) {
                                        map.moveCamera(centerUpdate);
                                        state.firstMove = false;
                                    } else {
                                        map.animateCamera(centerUpdate);
                                    }
                                } catch (Exception e) {
                                    // Map may have been destroyed, ignore
                                    Logging.info("Exception accessing map in MapUpdateRunnable callback: " + e.getMessage());
                                }
                            });
                        }
                    } else if ( previousLocation == null || previousLocation.getLatitude() != location.getLatitude()
                            || previousLocation.getLongitude() != location.getLongitude()
                            || previousRunNets != ListFragment.lameStatic.runNets) {
                        // location or nets have changed, update the view
                        if (mapView != null) {
                            mapView.postInvalidate();
                        }
                    }

                    try {
                        final boolean showRoute = prefs != null && prefs.getBoolean(
                                PreferenceKeys.PREF_VISUALIZE_ROUTE, false);
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
            } else {
                Logging.info( "finishing - not execuring mapping timed refresh" );
            }
        }
    }

    /**
     * Simplifies the route polyline if it exceeds performance thresholds.
     * Sets the simplified points on the polyline if simplification occurs,
     * otherwise sets the original points.
     *
     * @param routePoints The list of route points to potentially simplify
     */
    private void simplifyRouteIfNeeded(final List<org.maplibre.android.geometry.LatLng> routePoints) {
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
    private void performCoarseSimplification(final List<org.maplibre.android.geometry.LatLng> routePoints) {
        Simplify<org.maplibre.android.geometry.LatLng> simplify = new Simplify<>(
                new org.maplibre.android.geometry.LatLng[0], latLngPointExtractor);
        org.maplibre.android.geometry.LatLng[] simplified = simplify.simplify(
                routePoints.toArray(new org.maplibre.android.geometry.LatLng[0]),
                POLYLINE_TOLERANCE_COARSE, false);
        routePolyline.setPoints(Arrays.asList(simplified));
        Logging.error("major route simplification: " + routePoints.size() + "->" + simplified.length);
    }

    /**
     * Performs fine simplification using Douglas-Peucker algorithm.
     *
     * @param routePoints The route points to simplify
     */
    private void performFineSimplification(final List<org.maplibre.android.geometry.LatLng> routePoints) {
        Simplify<org.maplibre.android.geometry.LatLng> simplify = new Simplify<>(
                new org.maplibre.android.geometry.LatLng[0], latLngPointExtractor);
        org.maplibre.android.geometry.LatLng[] simplified = simplify.simplify(
                routePoints.toArray(new org.maplibre.android.geometry.LatLng[0]),
                POLYLINE_TOLERANCE_FINE, true);
        routePolyline.setPoints(Arrays.asList(simplified));
        Logging.error("minor route simplification: " + routePoints.size() + "->" + simplified.length);
    }

    private void updateRouteColor(final Polyline polyline, final SharedPreferences prefs) {
        //final int mapMode = prefs.getInt(PreferenceKeys.PREF_MAP_TYPE, MAP_TYPE_NORMAL);
        final boolean nightMode = ThemeUtil.shouldUseMapNightMode(getContext(), prefs);
        polyline.setColor(getRouteColorForMapType(1, nightMode));
    }

    @Override
    public void onDetach() {
        Logging.info( "FOSS MAP: onDetach.");
        super.onDetach();
    }

    @Override
    public void onDestroy() {
        Logging.info( "MAP: destroy mapping." );
        finishing.set(true);

        // Cancel any pending timer callbacks to prevent new map access attempts
        timer.removeCallbacks(mUpdateMapTask);

        if (mapRender != null) {
            mapRender.destroy();
            mapRender = null;
        }

        // Save map state before destroying the mapView
        if (mapView != null) {
            try {
                mapView.getMapAsync(map -> {
                    // Check if we're still finishing (defensive check)
                    if (finishing.get()) {
                        // save zoom
                        final Activity a = getActivity();
                        if (null != a) {
                            final SharedPreferences prefs = a.getSharedPreferences(PreferenceKeys.SHARED_PREFS, 0);
                            try {
                                final CameraPosition pos = map.getCameraPosition();
                                if (null != prefs) {
                                    final SharedPreferences.Editor edit = prefs.edit();
                                    edit.putFloat(PreferenceKeys.PREF_PREV_ZOOM, (float) (pos.zoom + ZOOM_MODIFIER)); //ALIBI: google zoom vs mapLibre zoom (2/2)
                                    edit.apply();
                                } else {
                                    Logging.warn("failed saving map state - unable to get preferences.");
                                }
                                // save center
                                org.maplibre.android.geometry.LatLng target = pos.target;
                                if (null != target) {
                                    state.oldCenter = new LatLng(target.getLatitude(),
                                            target.getLongitude());
                                }
                            } catch (Exception e) {
                                // Map may already be destroyed, ignore
                                Logging.info("Exception accessing map in onDestroy callback: " + e.getMessage());
                            }
                        }
                    }
                });
            } catch (Exception e) {
                Logging.info("Exception calling getMapAsync in onDestroy: " + e.getMessage());
            }

            try {
                mapView.onDestroy();
            } catch (NullPointerException ex) {
                Logging.info("exception in mapView.onDestroy: " + ex, ex);
            }
        }
        super.onDestroy();
    }

    @Override
    public void onPause() {
        Logging.info("FOSS MAP: onPause");
        super.onPause();
        try {
            mapView.onPause();
        } catch (final NullPointerException ex) {
            Logging.error("npe on mapview pause: " + ex, ex);
        }
        if (mapView != null) {
            mapView.onPause();
        }
        //TODO:
        /*if (map != null) {
            // save memory
            mapRender.clear();
        }*/
        if (null != headingManager) {
            headingManager.stopSensor();
        }
    }

    @Override
    public void onResume() {
        Logging.info( "FOSS MAP: onResume" );
        super.onResume();
        if (mapView != null) {
            mapView.onResume();
            mapView.postInvalidate();
        }
        // Sync network markers from cache when returning to map tab (covers late style load or tab switch)
        if (mapRender != null) {
            mapRender.reCluster();
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
        Logging.info( "FOSS MAP: onSaveInstanceState" );
        super.onSaveInstanceState(outState);
        mapView.onSaveInstanceState(outState);
    }

    @Override
    public void onLowMemory() {
        Logging.info( "FOSS MAP: onLowMemory" );
        super.onLowMemory();
        mapView.onLowMemory();
    }

    private LocationComponentActivationOptions buildLocationComponentActivationOptions(
            final Context context,
            final Style style,
            final LocationComponentOptions locationComponentOptions) {
        return LocationComponentActivationOptions
                .builder(context, style)
                .locationComponentOptions(locationComponentOptions)
                .useDefaultLocationEngine(true)
                .locationEngineRequest(
                    new LocationEngineRequest.Builder(750)
                            .setFastestInterval(750)
                            .setPriority(LocationEngineRequest.PRIORITY_HIGH_ACCURACY)
                            .build()
                )
                .build();
    }

    public static int getRouteColorForMapType(final int mapType, final boolean nightMode) {
        if (nightMode) {
            return OVERLAY_LIGHT;
        }
        return OVERLAY_DARK;
    }

    private static final PointExtractor<org.maplibre.android.geometry.LatLng> latLngPointExtractor = new PointExtractor<org.maplibre.android.geometry.LatLng>() {
        @Override
        public double getX(org.maplibre.android.geometry.LatLng point) {
            return point.getLatitude() * 1000000;
        }

        @Override
        public double getY(org.maplibre.android.geometry.LatLng point) {
            return point.getLatitude() * 1000000;
        }
    };

}
