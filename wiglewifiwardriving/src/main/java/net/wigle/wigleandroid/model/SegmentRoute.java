package net.wigle.wigleandroid.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Rendering-tech neutral route description
 */
public class SegmentRoute {
    private int mZIndex;
    private float mWidth ;
    private boolean mClickable;
    private List<LatLng> route;
    private int mColor;

    public void add(final LatLng latLng) {
        if (null == route) {
            route = new ArrayList<LatLng>();
        }
        route.add(latLng);
    }

    public void setColor(final int color) {
        this.mColor = color;
    }

    public void setWidth(final float width) {
        this.mWidth = width;
    }
    public void setZIndex(int zIndex) {
        this.mZIndex = zIndex;
    };

    public void setClickable(final boolean clickable) {
        this.mClickable = clickable;
    }

    public int getZIndex() {
        return mZIndex;
    }

    public float getWidth() {
        return mWidth;
    }

    public boolean getClickable() {
        return mClickable;
    }

    public List<LatLng> getPoints() {
        return route;
    }

    public int getColor() {
        return mColor;
    }
}
