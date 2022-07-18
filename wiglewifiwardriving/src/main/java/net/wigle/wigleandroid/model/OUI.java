package net.wigle.wigleandroid.model;

import android.content.res.AssetManager;
import net.wigle.wigleandroid.util.Logging;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Properties;

public final class OUI {
    private final Properties properties = new Properties();
    private boolean propertiesAvailable = false;

    public OUI(final AssetManager assetManager) {
        try {
            final InputStream ouiStream = assetManager.open("oui.properties");
            Logging.info("Oui stream: " + ouiStream);

            InputStreamReader ouiStreamReader = new InputStreamReader(ouiStream, "UTF-8");
            properties.load(ouiStreamReader);
            Logging.info("Oui properties loaded successful");

            propertiesAvailable = true;
        
        } catch (final IOException ex) {
            Logging.error("Oui properties loading failed: " + ex, ex);
            propertiesAvailable = false;
        }
    }

    /**
    * Returns the name of the manufacturer assigned to a given OUI or null value when the OUI does not exist or the data is not available.
    */
    public String getOui(final String partial) {
        if (!propertiesAvailable) return null;
        
        return properties.getProperty(partial);
    }

    /**
    * Returns a boolean value informing whether the OUI data was successfully loaded and whether it is available.
    */
    public boolean isOuiAvailable() {
        return propertiesAvailable;
    }
}