package net.wigle.wigleandroid;

import static android.view.View.VISIBLE;

import static net.wigle.wigleandroid.ui.NetworkListUtil.getSignalBitmap;
import static net.wigle.wigleandroid.util.PreferenceKeys.PREF_FOSS_MAPS_VECTOR_TILE_KEY;
import static net.wigle.wigleandroid.util.PreferenceKeys.PREF_FOSS_MAPS_VECTOR_TILE_STYLE;

import java.util.Map;

import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;

import androidx.annotation.NonNull;
import android.view.View;
import android.widget.RelativeLayout;

import net.wigle.wigleandroid.model.Network;
import net.wigle.wigleandroid.model.NetworkType;
import net.wigle.wigleandroid.util.FossConfigDialogUtil;
import net.wigle.wigleandroid.util.Logging;
import net.wigle.wigleandroid.util.PreferenceKeys;

import org.maplibre.android.MapLibre;
import org.maplibre.android.annotations.Icon;
import org.maplibre.android.annotations.IconFactory;
import org.maplibre.android.annotations.MarkerOptions;
import org.maplibre.android.camera.CameraPosition;
import org.maplibre.android.camera.CameraUpdateFactory;
import org.maplibre.android.maps.MapLibreMap;
import org.maplibre.android.maps.MapView;
import org.maplibre.android.maps.Style;
import org.maplibre.android.style.layers.CircleLayer;
import org.maplibre.android.style.layers.PropertyFactory;
import org.maplibre.android.style.sources.GeoJsonSource;
import org.maplibre.geojson.Point;

@SuppressWarnings("deprecation")
public class FossNetworkActivity extends AbstractNetworkActivity {


    private MapView mapView;

    private boolean inflationFailed = false;

    @Override
    protected void destroyMapView() {
        if (mapView != null) {
            mapView.onDestroy();
        }
    }

    @Override
    protected void resumeMapView() {
        if (mapView != null) {
            mapView.onResume();
        } else {
            final SharedPreferences prefs = getSharedPreferences(PreferenceKeys.SHARED_PREFS, 0);
            setupMap(network, null, prefs);
        }
    }

    @Override
    protected void pauseMapView() {
        if (mapView != null) {
            mapView.onPause();
        }
    }

    @Override
    protected void saveMapViewState(@NonNull final Bundle outState) {
        if (mapView != null) {
            try {
                mapView.onSaveInstanceState(outState);
            } catch (android.os.BadParcelableException bpe) {
                Logging.error("Exception saving FossNetworkActivity instance state: ", bpe);
                //this is really low-severity, since we can restore all state anyway
            }
        }
    }

    @Override
    protected void onLowMemoryMapView() {
        if (mapView != null) {
            mapView.onLowMemory();
        }
    }

    @Override
    protected int getLayoutResourceId() {
        return R.layout.foss_network;
    }

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        // ALIBI: MapLibre must be initialized before AbstractNetworkActivity#onCreate runs
        // setContentView() on a layout that contains <org.maplibre.android.maps.MapView>;
        // otherwise inflation throws and crashes the app.
        try {
            MapLibre.getInstance(this);
        } catch (Throwable t) {
            Logging.error("Failed to initialize MapLibre for FossNetworkActivity: ", t);
        }
        try {
            super.onCreate(savedInstanceState);
        } catch (RuntimeException re) {
            // Most commonly an android.view.InflateException for the inline MapView when the
            // user's FOSS map style/key configuration is invalid or MapLibre cannot start.
            inflationFailed = true;
            Logging.error("Failed to inflate FOSS network layout: ", re);
            FossConfigDialogUtil.show(this, () -> {
                if (!isFinishing()) {
                    finish();
                }
            });
        }
    }

    @Override
    protected void setupMap(final Network network, final Bundle savedInstanceState, final SharedPreferences prefs) {
        if (inflationFailed) {
            return;
        }
        final RelativeLayout rlView = findViewById(R.id.foss_network_map_layout);
        mapView = (rlView != null) ? rlView.findViewById(R.id.mapLibreNetworkMap) : null;
        if (mapView == null) {
            Logging.error("FOSS network mapView not found; FOSS map config likely invalid.");
            inflationFailed = true;
            FossConfigDialogUtil.show(this, () -> {
                if (!isFinishing()) {
                    finish();
                }
            });
            return;
        }

        try {
            mapView.onCreate(savedInstanceState);
            mapView.getMapAsync(mapLibreMap -> {
                final String mapServerKey = prefs != null ?
                        prefs.getString(PREF_FOSS_MAPS_VECTOR_TILE_KEY, null) : null;
                final String mapServerUrl = prefs != null ?
                        prefs.getString(PREF_FOSS_MAPS_VECTOR_TILE_STYLE, null) : null;

                String styleUrl;
                if (mapServerKey != null && !mapServerKey.isEmpty()) {
                    styleUrl = mapServerUrl + mapServerKey;
                } else {
                    styleUrl = "https://demotiles.maplibre.org/style.json";
                }
                try {
                    mapLibreMap.setStyle(styleUrl, style -> {
                        if ((network != null) && (network.getLatLng() != null)) {
                            final org.maplibre.android.geometry.LatLng focusOn =
                                    new org.maplibre.android.geometry.LatLng(
                                            network.getLatLng().latitude, network.getLatLng().longitude);
                            final CameraPosition cameraPosition = new CameraPosition.Builder()
                                    .target(focusOn).zoom(DEFAULT_ZOOM).build();
                            mapLibreMap.moveCamera(CameraUpdateFactory.newCameraPosition(cameraPosition));

                            final String sourceId = "center-source";
                            final GeoJsonSource source = new GeoJsonSource(
                                    sourceId, Point.fromLngLat(focusOn.getLongitude(), focusOn.getLatitude()));
                            style.addSource(source);
                            final String layerId = "circle-layer";
                            final CircleLayer circleLayer = new CircleLayer(layerId, sourceId);
                            circleLayer.setProperties(
                                    PropertyFactory.circleColor(Color.argb(128, 245, 245, 245)),
                                    PropertyFactory.circleStrokeColor(Color.argb(200, 255, 32, 32)),
                                    PropertyFactory.circleStrokeWidth(2f),
                                    PropertyFactory.circleRadius(10.0f)
                            );
                            style.addLayer(circleLayer);
                        }
                    });
                } catch (RuntimeException styleEx) {
                    Logging.error("Failed to apply FOSS map style '" + styleUrl + "': ", styleEx);
                    FossConfigDialogUtil.show(FossNetworkActivity.this, null);
                }
            });
            //TODO: day/night equivalent?
            //ThemeUtil.setMapTheme(mapLibre, mapView.getContext(), prefs, R.raw.night_style_json));
        } catch (NullPointerException ex) {
            Logging.error("npe in mapView.onCreate: " + ex, ex);
        } catch (RuntimeException ex) {
            Logging.error("Runtime exception setting up FOSS network map: ", ex);
            FossConfigDialogUtil.show(this, null);
        }
    }

    @Override
    protected void mapObservations() {
        // ALIBI:  assumes all observations belong to one "cluster" w/ a single centroid.
        // we could check and perform multi-cluster here
        // (get arithmetic mean, std-dev, try to do sigma-based partitioning)
        // but that seems less likely w/ one individual's observations
        final net.wigle.wigleandroid.model.LatLng estCentroid = computeBasicLocation(obsMap);
        final int zoomLevel = computeZoom(obsMap, estCentroid);
        mapView.getMapAsync(mapLibre -> {
            int count = 0;
            for (Map.Entry<net.wigle.wigleandroid.model.LatLng, Integer> obs : obsMap.entrySet()) {
                final net.wigle.wigleandroid.model.LatLng latLon = obs.getKey();
                final int level = obs.getValue();

                // default to initial position
                final org.maplibre.android.geometry.LatLng targetLatLon =
                        new org.maplibre.android.geometry.LatLng(latLon.latitude, latLon.longitude);
                if (count == 0 && network.getLatLng() == null) {
                    final CameraPosition cameraPosition = new CameraPosition.Builder()
                            .target(targetLatLon).zoom(DEFAULT_ZOOM).build();
                    mapLibre.moveCamera(CameraUpdateFactory.newCameraPosition(cameraPosition));
                }

                IconFactory iconFactory = IconFactory.getInstance(getApplicationContext());
                Icon obsIcon = iconFactory.fromBitmap(getSignalBitmap(getApplicationContext(), level));
                mapLibre.addMarker(new MarkerOptions().icon(obsIcon)
                        .position(targetLatLon));
                        //.zIndex(level));
                count++;
            }
            // if we got a good centroid, display it and center on it
            if (estCentroid.latitude != 0d && estCentroid.longitude != 0d) {
                //TODO: improve zoom based on obs distances?
                final org.maplibre.android.geometry.LatLng llCentroid =
                        new org.maplibre.android.geometry.LatLng(estCentroid.latitude, estCentroid.longitude);
                final CameraPosition cameraPosition = new CameraPosition.Builder()
                        .target(llCentroid).zoom(zoomLevel).build();
                mapLibre.moveCamera(CameraUpdateFactory.newCameraPosition(cameraPosition));
                mapLibre.addMarker(new MarkerOptions().position(llCentroid));
            }
            Logging.info("observation count: " + count);
            if ( NetworkType.WIFI.equals(network.getType()) ) {
                View v = findViewById(R.id.survey);
                v.setVisibility(VISIBLE);
            }
        });
    }

    @Override
    protected void mapWifiSeen(Network network, int zoomLevel, net.wigle.wigleandroid.model.LatLng latest, int rssi) {
        mapView.getMapAsync(mapLibre -> {
            final org.maplibre.android.geometry.LatLng latestObs =
                    new org.maplibre.android.geometry.LatLng(latest.latitude, latest.longitude);
            if (network.getLatLng() == null) {
                final CameraPosition cameraPosition = new CameraPosition.Builder()
                        .target(latestObs).zoom(zoomLevel).build();
                mapLibre.moveCamera(CameraUpdateFactory.newCameraPosition(cameraPosition));
            }
            IconFactory iconFactory = IconFactory.getInstance(getApplicationContext());
            Icon obsIcon = iconFactory.fromBitmap(getSignalBitmap(getApplicationContext(), rssi));

            mapLibre.addMarker(new MarkerOptions().icon(obsIcon)
                    .position(latestObs));
            //.zIndex(rssi));
            Logging.info("survey observation added");
        });
    }

    @Override
    protected void clearMap() {
        mapView.getMapAsync(MapLibreMap::clear);
    }
}
