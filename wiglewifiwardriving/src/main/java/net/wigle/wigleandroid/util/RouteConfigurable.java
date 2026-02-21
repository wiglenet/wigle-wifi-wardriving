package net.wigle.wigleandroid.util;

import net.wigle.wigleandroid.model.RouteDescriptor;

public interface RouteConfigurable {
    void configureMapForRoute(RouteDescriptor polyRoute);
    void clearCurrentRoute();
}
