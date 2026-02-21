package net.wigle.wigleandroid.model;

import androidx.annotation.NonNull;

public class MapBounds {
    @NonNull
    public final LatLng southwest;

    @NonNull
    public final LatLng northeast;

    public MapBounds(@NonNull LatLng southwest, @NonNull LatLng northeast) {
        this.southwest = southwest;
        this.northeast = northeast;
    }

    @NonNull
    public LatLng getSouthwest() {
        return southwest;
    }

    @NonNull
    public LatLng getNortheast() {
        return northeast;
    }

    public LatLng getCenter() {
        return new LatLng((southwest.latitude + northeast.latitude )/2.0, (southwest.longitude + northeast.longitude)/2);
    }

    public static final class Builder {
        private double minLat = Double.POSITIVE_INFINITY;
        private double maxLat = Double.NEGATIVE_INFINITY;
        private double minLon = Double.NaN;
        private double maxLon = Double.NaN;

        @NonNull
        public MapBounds.Builder include(@NonNull LatLng point) {
            this.minLat = Math.min(this.minLat, point.latitude);
            this.maxLat = Math.max(this.maxLat, point.latitude);
            double pointLon = point.longitude;
            if (Double.isNaN(this.minLon)) {
                this.minLon = pointLon;
                this.maxLon = pointLon;
            } else {
                double draftMinLon = this.minLon;
                double draftMaxLon = this.maxLon;
                if (draftMinLon <= draftMaxLon) {
                    if (draftMinLon <= pointLon && pointLon <= draftMaxLon) {
                        return this;
                    }
                } else if (draftMinLon <= pointLon || pointLon <= draftMaxLon) {
                    return this;
                }

                double lonOffset = draftMinLon - pointLon;
                draftMinLon = pointLon - this.maxLon + (double) 360.0F;
                if ((lonOffset + (double) 360.0F) % (double) 360.0F < draftMinLon % (double) 360.0F) {
                    this.minLon = pointLon;
                } else {
                    this.maxLon = pointLon;
                }
            }
            return this;
        }

        @NonNull
        public MapBounds build() {
            return new MapBounds(new LatLng(this.minLat, this.minLon), new LatLng(this.maxLat, this.maxLon));
        }
    }
}
