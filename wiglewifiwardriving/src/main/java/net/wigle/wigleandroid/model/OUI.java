package net.wigle.wigleandroid.model;

import android.content.res.AssetManager;

import net.wigle.wigleandroid.util.Logging;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Properties;

public final class OUI {
    private final Properties properties = new Properties();
    public OUI(final AssetManager assetManager) {
        try {
            final InputStream stream = assetManager.open("oui.properties");
            Logging.info("oui stream: " + stream);

            InputStreamReader isr = new InputStreamReader(stream, "UTF-8");
            properties.load(isr);
            Logging.info("oui load complete");
        }
        catch (final IOException ex) {
            Logging.error("exception loading oui: " + ex, ex);
        }
    }

    public String getOui(final String partial) {
        return properties.getProperty(partial);
    }
}