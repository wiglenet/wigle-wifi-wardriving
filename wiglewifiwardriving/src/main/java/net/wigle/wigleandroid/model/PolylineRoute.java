package net.wigle.wigleandroid.model;

import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.PolylineOptions;

import net.wigle.wigleandroid.MainActivity;

import static net.wigle.wigleandroid.MappingFragment.getRouteColorForMapType;

public class PolylineRoute {
    public static final float DEFAULT_ROUTE_WIDTH = 15.0f; //TODO: dedup with MappingFragment

    private PolylineOptions polyline;
    private float westExtent;
    private float eastExtent;
    private float northExtent;
    private float southExtent;
    private long segments;

    public PolylineRoute() {
        polyline = new PolylineOptions()
                .clickable(false);
        //init to sure - opposites
        westExtent = 180f;
        eastExtent = -180f;
        northExtent = -90f;
        southExtent = 90f;
        segments = 0;
    }

    public void addLatLng(final float latitude, final float longitude, final int mapMode) {
        polyline.add(
                new LatLng(latitude, longitude));
        polyline.color(getRouteColorForMapType(mapMode));
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
        segments++;
    }

    public PolylineOptions getPolyline() {
        return polyline;
    }

    public LatLng getNEExtent() {
        MainActivity.error(eastExtent+"e , "+northExtent+"n");
        return new LatLng(northExtent, eastExtent);
    }

    public LatLng getSWExtent() {
        MainActivity.error(westExtent+"w , "+southExtent+"s");
        return new LatLng(southExtent, westExtent);
    }

    public long getSegments() {
        return segments;
    }

}
