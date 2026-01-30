package net.wigle.wigleandroid;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.RelativeLayout;

import com.google.android.gms.maps.CameraUpdate;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.MapView;
import com.google.android.gms.maps.MapsInitializer;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Polyline;

import net.wigle.wigleandroid.model.RouteDescriptor;
import net.wigle.wigleandroid.ui.ThemeUtil;
import net.wigle.wigleandroid.util.GMapsConverter;
import net.wigle.wigleandroid.util.Logging;
import net.wigle.wigleandroid.util.PreferenceKeys;

/**
 * Google-maps specific GPX management activity.
 * [delete this file for FOSS build]
 */
public class GpxManagementActivity extends AbstractGpxManagementActivity {
    private MapView mapView;
    private Polyline routePolyline;

    @Override
    protected int getLayoutResourceId() {
        return R.layout.activity_gpx_mgmt;
    }

    @Override
    protected void setupMap(final SharedPreferences prefs) {
        mapView = new MapView( this );
        super.mapView = mapView;
        try {
            mapView.onCreate(null);
            mapView.getMapAsync(googleMap -> ThemeUtil.setMapTheme(googleMap, mapView.getContext(), prefs, R.raw.night_style_json));
        } catch (NullPointerException ex) {
            Logging.error("npe in mapView.onCreate: " + ex, ex);
        }
        MapsInitializer.initialize( this );
        final RelativeLayout rlView = findViewById( R.id.gpx_map_rl );
        rlView.addView( mapView );
    }

    @Override
    protected void configureMapForRouteInternal(final RouteDescriptor routeDescriptor) {
        mapView.getMapAsync(googleMap -> {
            // Clear existing polyline if any
            if (routePolyline != null) {
                routePolyline.remove();
            }

            LatLngBounds.Builder builder = new LatLngBounds.Builder();
            builder.include(new LatLng(routeDescriptor.getNEExtent().latitude, routeDescriptor.getNEExtent().longitude) );
            builder.include(new LatLng(routeDescriptor.getSWExtent().latitude, routeDescriptor.getSWExtent().longitude) );
            LatLngBounds bounds = builder.build();
            final CameraUpdate cu = CameraUpdateFactory.newLatLngBounds(bounds, DEFAULT_MAP_PADDING);
            googleMap.animateCamera(cu);
            routePolyline = googleMap.addPolyline(
                    GMapsConverter.getPolyLineOptionsForRoute(routeDescriptor.getSegmentRoute()));
            routePolyline.setTag(CURRENT_ROUTE_LINE_TAG);
            super.routePolyline = routePolyline;
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