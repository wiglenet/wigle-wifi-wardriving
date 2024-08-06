package net.wigle.wigleandroid.background;

import static net.wigle.wigleandroid.util.FileUtility.KML_EXT;
import static net.wigle.wigleandroid.util.FileUtility.WIWI_PREFIX;

import android.os.Bundle;
import android.os.Parcelable;

import androidx.annotation.ColorInt;
import androidx.fragment.app.FragmentActivity;

import net.wigle.wigleandroid.R;
import net.wigle.wigleandroid.WiGLEAuthException;
import net.wigle.wigleandroid.db.DatabaseHelper;
import net.wigle.wigleandroid.model.Observation;
import net.wigle.wigleandroid.util.FileAccess;
import net.wigle.wigleandroid.util.FileUtility;
import net.wigle.wigleandroid.util.Logging;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.Date;
import java.util.Locale;
import java.util.Set;

/**
 * can we build a KML heatmap like this? who knows?
 * @author rksh
 */
public class KmlSurveyWriter extends AbstractBackgroundTask {
    final Bundle bundle = new Bundle();
    final String bssid;
    final Collection<Observation> observations;
    public KmlSurveyWriter(FragmentActivity context, DatabaseHelper dbHelper, String name, boolean showProgress, final String networkBssid, final Collection<Observation> observations) {
        super(context, dbHelper, name, showProgress);
        this.bssid = networkBssid;
        this.observations = observations;
    }

    @Override
    protected void subRun() throws IOException, InterruptedException, WiGLEAuthException {
        final SimpleDateFormat fileDateFormat = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US);
        final String filename = WIWI_PREFIX +"_SITE_" + bssid+"_"+ fileDateFormat.format(new Date()) + KML_EXT;
        Status status = null;


        final FileOutputStream fos = FileUtility.createFile(context, filename, false);
        FileAccess.writeFos( fos, "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<kml xmlns=\"http://www.opengis.net/kml/2.2\"><Document>"
                +"<Style id=\"r_100_up\"><IconStyle><color>cc0000ff</color><scale>0.75</scale><Icon> <href>http://maps.google.com/mapfiles/kml/shapes/shaded_dot.png</href></Icon></IconStyle><LabelStyle><scale>0</scale></LabelStyle></Style>"
                +"<Style id=\"r_90_99\"><IconStyle><color>cc0055ff</color><scale>0.75</scale><Icon> <href>http://maps.google.com/mapfiles/kml/shapes/shaded_dot.png</href></Icon></IconStyle><LabelStyle><scale>0</scale></LabelStyle></Style>"
                +"<Style id=\"r_80_89\"><IconStyle><color>cc00aaff</color><scale>0.75</scale><Icon> <href>http://maps.google.com/mapfiles/kml/shapes/shaded_dot.png</href></Icon></IconStyle><LabelStyle><scale>0</scale></LabelStyle></Style>"
                +"<Style id=\"r_70_79\"><IconStyle><color>cc00ffff</color><scale>0.75</scale><Icon> <href>http://maps.google.com/mapfiles/kml/shapes/shaded_dot.png</href></Icon></IconStyle><LabelStyle><scale>0</scale></LabelStyle></Style>"
                +"<Style id=\"r_60_69\"><IconStyle><color>cc00ffaa</color><scale>0.75</scale><Icon> <href>http://maps.google.com/mapfiles/kml/shapes/shaded_dot.png</href></Icon></IconStyle><LabelStyle><scale>0</scale></LabelStyle></Style>"
                +"<Style id=\"r_50_59\"><IconStyle><color>cc00ff55</color><scale>0.75</scale><Icon> <href>http://maps.google.com/mapfiles/kml/shapes/shaded_dot.png</href></Icon></IconStyle><LabelStyle><scale>0</scale></LabelStyle></Style>"
                +"<Style id=\"r_0_49\"><IconStyle><color>cc00ff00</color><scale>0.75</scale><Icon> <href>http://maps.google.com/mapfiles/kml/shapes/shaded_dot.png</href></Icon></IconStyle><LabelStyle><scale>0</scale></LabelStyle></Style>"
                + "<Folder><name>"+bssid+"</name>\n" );

        for (Observation o : observations) {
            writeHeatmapPlacemark(fos, o);
        }
        FileAccess.writeFos( fos, "</Folder>\n</Document></kml>" );
        fos.close();
        bundle.putString( BackgroundGuiHandler.FILEPATH, FileUtility.getSDPath() + filename );
        bundle.putString( BackgroundGuiHandler.FILENAME, filename );
        Logging.info( "done with kml export" );
        status = Status.WRITE_SUCCESS;

        if ( status != null ) {
            sendBundledMessage( status.ordinal(), bundle );
        } else {
            Logging.warn("null status - not sharing export.");
        }
    }

    private void writeHeatmapPlacemark(final OutputStream fos, final Observation observation) throws IOException {
        FileAccess.writeFos( fos, "<Placemark><name>"+observation.getFormattedTime()+observation.getRssi()+"</name><styleUrl>");
        FileAccess.writeFos( fos, getStyleStringForRssi(observation.getRssi()));
        FileAccess.writeFos( fos, "</styleUrl>");
        FileAccess.writeFos( fos, "<ExtendedData><SchemaData><SimpleData name=\"signal\">"+observation.getRssi()+"</SimpleData></SchemaData></ExtendedData>");
        FileAccess.writeFos( fos, "<Point><coordinates>"+observation.getLongitude()+","+observation.getLatitude()+","+ observation.getElevationMeters()+"</coordinates></Point></Placemark>");
    }

    private String getStyleStringForRssi(final int rssi) {
        if (rssi <= -100) {
            return "#r_100_up";
        } else if (rssi <= -90) {
            return "#r_90_99";
        } else if (rssi <= -80) {
            return "#r_80_89";
        } else if (rssi <= -70) {
            return "#r_70_79";
        } else if (rssi <= -60) {
            return "#r_60_69";
        } else if (rssi <= -50) {
            return "#r_50_59";
        } else if (rssi <= -40){
            return "#r_0_49";
        } else {
            return "#r_0_49";
        }
    }
}
