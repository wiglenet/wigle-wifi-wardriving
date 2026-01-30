package net.wigle.wigleandroid;

import static net.wigle.wigleandroid.util.PreferenceKeys.PREF_FOSS_MAPS_VECTOR_TILE_KEY;
import static net.wigle.wigleandroid.util.PreferenceKeys.PREF_FOSS_MAPS_VECTOR_TILE_STYLE;

import android.content.SharedPreferences;
import android.widget.RelativeLayout;

import net.wigle.wigleandroid.model.RouteDescriptor;
import net.wigle.wigleandroid.util.Logging;

import org.maplibre.android.MapLibre;
import org.maplibre.android.annotations.Polyline;
import org.maplibre.android.annotations.PolylineOptions;
import org.maplibre.android.camera.CameraPosition;
import org.maplibre.android.camera.CameraUpdate;
import org.maplibre.android.camera.CameraUpdateFactory;
import org.maplibre.android.geometry.LatLng;
import org.maplibre.android.geometry.LatLngBounds;
import org.maplibre.android.maps.MapView;

import java.util.ArrayList;
import java.util.List;

public class FossGpxManagementActivity extends AbstractGpxManagementActivity {
    private MapView mapView;
    private Polyline routePolyline;

    @Override
    protected void initializeMapLibrary() {
        MapLibre.getInstance(this);
    }

    @Override
    protected int getLayoutResourceId() {
        return R.layout.activity_foss_gps_mgmt;
    }

    @Override
    protected void setupMap(SharedPreferences prefs) {
        final RelativeLayout rlView = findViewById( R.id.ml_gpx_map_rl );
        mapView = rlView.findViewById(R.id.maplibreView);
        super.mapView = mapView;
        if (null != mapView) {
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
                mapLibreMap.setStyle(styleUrl);
                mapLibreMap.setCameraPosition(
                        new CameraPosition.Builder().target(
                                new LatLng(0.0, 0.0)).zoom(1.0).build());
            });
        } else {
            Logging.error("Failed to find mapView");
        }
    }

    @Override
    protected void configureMapForRouteInternal(RouteDescriptor routeDescriptor) {
        mapView.getMapAsync(mapLibreMap -> {
            // Clear existing polyline if any
            if (routePolyline != null) {
                routePolyline.remove();
            }

            // Get points from PolylineRoute and convert to MapLibre LatLng
            List<double[]> routePoints = routeDescriptor.getRoutePoints();

            if (routePoints != null && !routePoints.isEmpty()) {
                // Convert points to MapLibre LatLng
                List<LatLng> maplibrePoints = new ArrayList<>();
                for (double[] point : routePoints) {
                    maplibrePoints.add(new LatLng(point[0], point[1]));
                }

                // Create MapLibre PolylineOptions
                PolylineOptions polylineOptions = new PolylineOptions();
                polylineOptions.addAll(maplibrePoints);
                polylineOptions.color(routeDescriptor.getRouteColor());
                polylineOptions.width(routeDescriptor.getRouteWidth());

                // Add polyline to map
                routePolyline = mapLibreMap.addPolyline(polylineOptions);
                super.routePolyline = routePolyline;

                // Set camera bounds to show the route
                LatLngBounds.Builder boundsBuilder = new LatLngBounds.Builder();
                boundsBuilder.include(new LatLng(routeDescriptor.getNEExtent().latitude, routeDescriptor.getNEExtent().longitude));
                boundsBuilder.include(new LatLng(routeDescriptor.getSWExtent().latitude, routeDescriptor.getSWExtent().longitude));
                LatLngBounds bounds = boundsBuilder.build();
                
                CameraUpdate cameraUpdate = CameraUpdateFactory.newLatLngBounds(
                        bounds, DEFAULT_MAP_PADDING);
                mapLibreMap.animateCamera(cameraUpdate);
            }
        });
    }

    @Override
    protected void clearRoutePolyline() {
        if (routePolyline != null ) {
            routePolyline.remove();
        }
    }

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
            setupMap(prefs);
        }
    }

    @Override
    protected void pauseMapView() {
        if (mapView != null) {
            mapView.onPause();
        }
    }
}