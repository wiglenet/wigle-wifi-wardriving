package net.wigle.wigleandroid.util;

import net.wigle.wigleandroid.model.PolylineRoute;

public interface PolyRouteConfigurable {
    void configureMapForRoute(PolylineRoute polyRoute);
    void clearCurrentRoute();
}
