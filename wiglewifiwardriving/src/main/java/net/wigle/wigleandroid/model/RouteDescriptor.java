package net.wigle.wigleandroid.model;

import android.location.Location;


import java.util.ArrayList;
import java.util.List;

import static net.wigle.wigleandroid.MappingFragment.getRouteColorForMapType;

/**
 * A utility class to manage routes for internal representation and map rendering
 */
public class RouteDescriptor {
    public static final float DEFAULT_ROUTE_WIDTH = 7.0f; //TODO: dedup with MappingFragment

    private final SegmentRoute polyline;
    private float westExtent;
    private float eastExtent;
    private float northExtent;
    private float southExtent;
    private long segments;
    private float distanceMeters;
    private LatLng lastAdded;

    public RouteDescriptor() {
        distanceMeters = 0;
        polyline = new SegmentRoute();
        polyline.setClickable(false);
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
        polyline.setColor(getRouteColorForMapType(mapMode, nightMode));
        polyline.setWidth(DEFAULT_ROUTE_WIDTH);
        polyline.setZIndex(10000); //to overlay above traffic data
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
    public SegmentRoute getSegmentRoute() {
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
     * Get the north-eastern-most point latitude
     * @return the latitude value
     */
    public float getNELatitude() {
        return northExtent;
    }

    /**
     * Get the north-eastern-most point longitude
     * @return the longitude value
     */
    public float getNELongitude() {
        return eastExtent;
    }

    /**
     * Get the south-western-most point of the route
     * @return the value at the corner
     */
    public LatLng getSWExtent() {
        return new LatLng(southExtent, westExtent);
    }

    /**
     * Get the south-western-most point latitude
     * @return the latitude value
     */
    public float getSWLatitude() {
        return southExtent;
    }

    /**
     * Get the south-western-most point longitude
     * @return the longitude value
     */
    public float getSWLongitude() {
        return westExtent;
    }

    /**
     * Get the route color
     * @return the color value
     */
    public int getRouteColor() {
        return polyline.getColor();
    }

    /**
     * Get the route width
     * @return the width value
     */
    public float getRouteWidth() {
        return polyline.getWidth();
    }

    /**
     * Get all route points as a list of latitude/longitude pairs
     * @return list of points where each point is represented as a double array [latitude, longitude]
     */
    public List<double[]> getRoutePoints() {
        List<double[]> points = new ArrayList<>();
        for (LatLng point : polyline.getPoints()) {
            points.add(new double[]{point.latitude, point.longitude});
        }
        return points;
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
