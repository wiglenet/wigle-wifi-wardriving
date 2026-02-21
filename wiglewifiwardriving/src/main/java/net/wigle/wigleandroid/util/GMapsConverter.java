package net.wigle.wigleandroid.util;

import com.google.android.gms.maps.model.PolylineOptions;

import net.wigle.wigleandroid.model.LatLng;
import net.wigle.wigleandroid.model.SegmentRoute;

/**
 * Utility class to convert internal route class to google-friendly
 * @author arkasha
 */
public class GMapsConverter {
    public static PolylineOptions getPolyLineOptionsForRoute(final SegmentRoute route) {
        PolylineOptions options = new PolylineOptions().clickable(route.getClickable());
        options.color(route.getColor());
        options.zIndex(route.getZIndex());
        options.width(route.getWidth());
        for (LatLng point: route.getPoints()) {
            options.add(new com.google.android.gms.maps.model.LatLng(point.latitude, point.longitude));
        }
        return options;
    }
}
