package net.wigle.wigleandroid.background;

import java.io.IOException;
import java.net.HttpURLConnection;

/**
 * Created by bobzilla on 11/14/15
 */
public interface PreConnectConfigurator {
    void configure(final HttpURLConnection connection) throws IOException;
}
