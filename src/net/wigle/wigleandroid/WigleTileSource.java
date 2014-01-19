package net.wigle.wigleandroid;

import org.osmdroid.ResourceProxy;
import org.osmdroid.tileprovider.tilesource.ITileSource;
import org.osmdroid.tileprovider.tilesource.XYTileSource;


/**
 * a wigle-backed tile server
 */
public class WigleTileSource extends XYTileSource {

    /** wigle-served OSM tiles */
    public static final ITileSource WiGLE = new WigleTileSource(); 
    
    WigleTileSource() {
        super("WiGLE", ResourceProxy.string.unknown, 0, 18, 256, ".png", "https://wigle.net/osmtiles/");
    }
    // this is what the ResourceProxy.string.unkown enum would be used for in the BitmapTileSourceBase
    @Override
    public String localizedName(final ResourceProxy proxy) {
        return "WiGLE";
    }
}