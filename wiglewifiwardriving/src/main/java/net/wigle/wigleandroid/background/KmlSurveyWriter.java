package net.wigle.wigleandroid.background;

import static net.wigle.wigleandroid.util.FileUtility.KML_EXT;
import static net.wigle.wigleandroid.util.FileUtility.WIWI_PREFIX;

import android.os.Bundle;
import android.os.Parcelable;

import androidx.fragment.app.FragmentActivity;

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
                +"<Style id=\"100_and_up\"><IconStyle><color>ee0000ff</color><scale>0.75</scale><Icon> <href>http://maps.google.com/mapfiles/kml/shapes/shaded_dot.png</href></Icon></IconStyle><LabelStyle><scale>0</scale></LabelStyle></Style>"
                +"<Style id=\"90_to_99\"><IconStyle><color>ee0046fd</color><scale>0.75</scale><Icon> <href>http://maps.google.com/mapfiles/kml/shapes/shaded_dot.png</href></Icon></IconStyle><LabelStyle><scale>0</scale></LabelStyle></Style>"
                +"<Style id=\"80_to_89\"><IconStyle><color>ee0068f7</color><scale>0.75</scale><Icon> <href>http://maps.google.com/mapfiles/kml/shapes/shaded_dot.png</href></Icon></IconStyle><LabelStyle><scale>0</scale></LabelStyle></Style>"
                +"<Style id=\"70_to_79\"><IconStyle><color>ee0085ed</color><scale>0.75</scale><Icon> <href>http://maps.google.com/mapfiles/kml/shapes/shaded_dot.png</href></Icon></IconStyle><LabelStyle><scale>0</scale></LabelStyle></Style>"
                +"<Style id=\"60_to_69\"><IconStyle><color>ee009de0</color><scale>0.75</scale><Icon> <href>http://maps.google.com/mapfiles/kml/shapes/shaded_dot.png</href></Icon></IconStyle><LabelStyle><scale>0</scale></LabelStyle></Style>"
                +"<Style id=\"50_to_59\"><IconStyle><color>ee00b3d2</color><scale>0.75</scale><Icon> <href>http://maps.google.com/mapfiles/kml/shapes/shaded_dot.png</href></Icon></IconStyle><LabelStyle><scale>0</scale></LabelStyle></Style>"
                +"<Style id=\"40_to_49\"><IconStyle><color>ee00c7c2</color><scale>0.75</scale><Icon> <href>http://maps.google.com/mapfiles/kml/shapes/shaded_dot.png</href></Icon></IconStyle><LabelStyle><scale>0</scale></LabelStyle></Style>"
                +"<Style id=\"30_to_39\"><IconStyle><color>ee00daae</color><scale>0.75</scale><Icon> <href>http://maps.google.com/mapfiles/kml/shapes/shaded_dot.png</href></Icon></IconStyle><LabelStyle><scale>0</scale></LabelStyle></Style>"
                +"<Style id=\"20_to_29\"><IconStyle><color>ee00ed92</color><scale>0.75</scale><Icon> <href>http://maps.google.com/mapfiles/kml/shapes/shaded_dot.png</href></Icon></IconStyle><LabelStyle><scale>0</scale></LabelStyle></Style>"
                +"<Style id=\"0_to_19\"><IconStyle><color>ee00ff6f</color><scale>0.75</scale><Icon> <href>http://maps.google.com/mapfiles/kml/shapes/shaded_dot.png</href></Icon></IconStyle><LabelStyle><scale>0</scale></LabelStyle></Style>"
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
		//				<ExtendedData>
		//					<SchemaData>
		//						<SimpleData name="Date">30/11/2016</SimpleData>
		//					</SchemaData>
	    //					</ExtendedData>
        FileAccess.writeFos( fos, "<Point><coordinates>"+observation.getLongitude()+","+observation.getLatitude()+",0</coordinates></Point></Placemark>");
    }
    private String getStyleStringForRssi(final int rssi) {
        if (rssi >= 100) {
            return "#100_and_up";
        } else if (rssi >= 90) {
            return "#90_to_99";
        } else if (rssi >= 80) {
            return "#80_to_89";
        } else if (rssi >= 70) {
            return "#70_to_79";
        } else if (rssi >= 60) {
            return "#60_to_69";
        } else if (rssi >= 50) {
            return "#50_to_59";
        } else if (rssi >= 40) {
            return "#40_to_49";
        } else if (rssi >= 30) {
            return "#30_to_39";
        } else if (rssi >= 20) {
            return "#20_to_29";
        } else {
            return "#0_to_19";
        }
    }
}
