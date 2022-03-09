package net.wigle.wigleandroid.model;

import android.graphics.Color;
import android.location.Location;

import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.PolylineOptions;

import net.wigle.wigleandroid.MainActivity;

import static net.wigle.wigleandroid.MappingFragment.getRouteColorForMapType;

/**
 * A utility class to manage routes for internal represntation and map rendering
 */
public class PolylineRoute {
    public static final float DEFAULT_ROUTE_WIDTH = 15.0f; //TODO: dedup with MappingFragment

    private PolylineOptions polyline;
    private float westExtent;
    private float eastExtent;
    private float northExtent;
    private float southExtent;
    private long segments;
    private float distanceMeters;
    private LatLng lastAdded;

    public PolylineRoute() {
        distanceMeters = 0;
        polyline = new PolylineOptions()
                .clickable(false);
        //init to sure - opposites
        westExtent = 180f;
        eastExtent = -180f;
        northExtent = -90f;
        southExtent = 90f;
        segments = 0;
        lastAdded = null;
        distanceMeters = 0.0f;
    }

    /**
     * add a lat/lon pair to the polyline and stats. Assumes points added in order of time.
     * @param latitude latitude
     * @param longitude longitude
     * @param mapMode map mode dictates line color choice (google map mode id)
     * @param nightMode specify night mode
     */
    public void addLatLng(final float latitude, final float longitude, final int mapMode, final boolean nightMode) {
        final LatLng newPoint = new LatLng(latitude, longitude);
        polyline.add(newPoint);
        polyline.color(getRouteColorForMapType(mapMode, nightMode));
        polyline.width(DEFAULT_ROUTE_WIDTH);
        polyline.zIndex(10000); //to overlay above traffic data
        if (latitude > northExtent) {
            northExtent = latitude;
        }
        if (latitude < southExtent) {
            southExtent = latitude;
        }
        if (longitude < westExtent) {
            westExtent = longitude;
        }
        if (longitude > eastExtent) {
            eastExtent = longitude;
        }
        if (lastAdded != null) {
            distanceMeters += getDistanceMetersBetween(lastAdded, newPoint);
        }
        lastAdded = newPoint;
        segments++;
    }

    /**
     * Get the google maps polyline for the route
     * @return the corresponding polyline to render
     */
    public PolylineOptions getPolyline() {
        return polyline;
    }

    /**
     * Get the north-eastern-most point of the route
     * @return the value at the corner
     */
    public LatLng getNEExtent() {
        return new LatLng(northExtent, eastExtent);
    }

    /**
     * Get the south-western-most point of the route
     * @return the value at the corner
     */
    public LatLng getSWExtent() {
        return new LatLng(southExtent, westExtent);
    }

    /**
     * get the total number of segments in the route
     * @return
     */
    public long getSegments() {
        return segments;
    }

    /**
     * get the total distance comprised by the route
     * @return the distance in meters
     */
    public float getDistanceMeters() {
        return distanceMeters;
    }

    private float getDistanceMetersBetween(LatLng last, LatLng next) {
        float[] results = new float[1];
        Location.distanceBetween(last.latitude, last.longitude, next.latitude, next.longitude, results);
        return results[0];
    }
}
