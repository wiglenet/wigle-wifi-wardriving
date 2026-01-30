package net.wigle.wigleandroid;

import static android.view.View.VISIBLE;

import java.util.Map;

import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;

import androidx.annotation.NonNull;
import android.view.View;
import android.widget.RelativeLayout;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapView;
import com.google.android.gms.maps.MapsInitializer;
import com.google.android.gms.maps.model.BitmapDescriptor;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.CircleOptions;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;

import net.wigle.wigleandroid.model.Network;
import net.wigle.wigleandroid.model.NetworkType;
import net.wigle.wigleandroid.ui.NetworkListUtil;
import net.wigle.wigleandroid.ui.ThemeUtil;
import net.wigle.wigleandroid.util.Logging;
import net.wigle.wigleandroid.util.PreferenceKeys;

/**
 * Display network details - Google Maps-specific implementation
 * [delete this file for FOSS build]
 */
@SuppressWarnings("deprecation")
public class NetworkActivity extends AbstractNetworkActivity {


    private MapView mapView;

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
                Logging.error("Exception saving NetworkActivity instance state: ", bpe);
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

    protected int getLayoutResourceId() {
        return R.layout.network;
    }

    @Override
    protected void setupMap(final Network network, final Bundle savedInstanceState, final SharedPreferences prefs) {
        mapView = new MapView( this );
        try {
            mapView.onCreate(savedInstanceState);
            mapView.getMapAsync(googleMap -> ThemeUtil.setMapTheme(googleMap, mapView.getContext(), prefs, R.raw.night_style_json));
        }
        catch (NullPointerException ex) {
            Logging.error("npe in mapView.onCreate: " + ex, ex);
        }
        MapsInitializer.initialize( this );

        if ((network != null) && (network.getLatLng() != null)) {
            mapView.getMapAsync(googleMap -> {
                final CameraPosition cameraPosition = new CameraPosition.Builder()
                        .target(network.getPosition()).zoom(DEFAULT_ZOOM).build();
                googleMap.moveCamera(CameraUpdateFactory.newCameraPosition(cameraPosition));

                googleMap.addCircle(new CircleOptions()
                        .center(network.getPosition())
                        .radius(5)
                        .fillColor(Color.argb(128, 240, 240, 240))
                        .strokeColor(Color.argb(200, 255, 32, 32))
                        .strokeWidth(3f)
                        .zIndex(100));
            });
        }

        final RelativeLayout rlView = findViewById( R.id.netmap_rl );
        rlView.addView( mapView );
    }

    @Override
    protected void mapObservations() {
        // ALIBI:  assumes all observations belong to one "cluster" w/ a single centroid.
        // we could check and perform multi-cluster here
        // (get arithmetic mean, std-dev, try to do sigma-based partitioning)
        // but that seems less likely w/ one individual's observations
        final net.wigle.wigleandroid.model.LatLng estCentroid = computeBasicLocation(obsMap);
        final int zoomLevel = computeZoom(obsMap, estCentroid);
        mapView.getMapAsync(googleMap -> {
            int count = 0;
            for (Map.Entry<net.wigle.wigleandroid.model.LatLng, Integer> obs : obsMap.entrySet()) {
                final net.wigle.wigleandroid.model.LatLng latLon = obs.getKey();
                final int level = obs.getValue();

                // default to initial position
                final com.google.android.gms.maps.model.LatLng targetLatLon = new com.google.android.gms.maps.model.LatLng(latLon.latitude, latLon.longitude);
                if (count == 0 && network.getLatLng() == null) {
                    final CameraPosition cameraPosition = new CameraPosition.Builder()
                            .target(targetLatLon).zoom(DEFAULT_ZOOM).build();
                    googleMap.moveCamera(CameraUpdateFactory.newCameraPosition(cameraPosition));
                }

                BitmapDescriptor obsIcon = NetworkListUtil.getSignalBitmapDescriptor(
                        getApplicationContext(), level);

                googleMap.addMarker(new MarkerOptions().icon(obsIcon)
                        .position(targetLatLon).zIndex(level));
                count++;
            }
            // if we got a good centroid, display it and center on it
            if (estCentroid.latitude != 0d && estCentroid.longitude != 0d) {
                //TODO: improve zoom based on obs distances?
                final LatLng llCentroid = new LatLng(estCentroid.latitude, estCentroid.longitude);
                final CameraPosition cameraPosition = new CameraPosition.Builder()
                        .target(llCentroid).zoom(zoomLevel).build();
                googleMap.moveCamera(CameraUpdateFactory.newCameraPosition(cameraPosition));
                googleMap.addMarker(new MarkerOptions().position(llCentroid));
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
        mapView.getMapAsync(googleMap -> {
            final LatLng latestObs = new LatLng(latest.latitude, latest.longitude);
            if (network.getLatLng() == null) {
                final CameraPosition cameraPosition = new CameraPosition.Builder()
                        .target(latestObs).zoom(zoomLevel).build();
                googleMap.moveCamera(CameraUpdateFactory.newCameraPosition(cameraPosition));
            }
            BitmapDescriptor obsIcon = NetworkListUtil.getSignalBitmapDescriptor(
                    getApplicationContext(), rssi);
            googleMap.addMarker(new MarkerOptions().icon(obsIcon)
                    .position(latestObs).zIndex(rssi));
            Logging.info("survey observation added");
        });
    }

    @Override
    protected void clearMap() {
        mapView.getMapAsync(GoogleMap::clear);
    }
}
