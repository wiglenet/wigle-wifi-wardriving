package net.wigle.wigleandroid.background;

import android.os.Bundle;
import androidx.fragment.app.FragmentActivity;

import net.wigle.wigleandroid.db.DatabaseHelper;
import net.wigle.wigleandroid.MainActivity;
import net.wigle.wigleandroid.WiGLEAuthException;
import net.wigle.wigleandroid.util.FileUtility;
import net.wigle.wigleandroid.util.Logging;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

import static net.wigle.wigleandroid.util.FileUtility.KML_EXT;

/**
 * A KML-upload grabber intended for sharing/viewing via intents
 * Created by arkasha on 10/27/17.
 */

public class KmlDownloader extends AbstractProgressApiRequest {
    private Status status;

    public KmlDownloader(final FragmentActivity context, final DatabaseHelper dbHelper /*TODO: not needed?*/,
                               final String transid, final ApiListener listener) {
        super(context, dbHelper, "KmlDL", transid+KML_EXT, MainActivity.KML_TRANSID_URL_STEM+transid, false,
                true, true, false, AbstractApiRequest.REQUEST_GET, listener, true);
        }

    @Override
    protected void subRun() throws IOException, InterruptedException, WiGLEAuthException {
        status = Status.UNKNOWN;
        final Bundle bundle = new Bundle();
        sendBundledMessage( Status.DOWNLOADING.ordinal(), bundle );

        String result = null;
        try {
            result = doDownload(this.connectionMethod);
            writeSharefile(result, outputFileName);
            final JSONObject json = new JSONObject("{success: " + true + ", file:\"" +
                    FileUtility.getKmlPath(context) + "/" + outputFileName + "\"}");
            sendBundledMessage( Status.SUCCESS.ordinal(), bundle );
            listener.requestComplete(json, false);
        } catch (final WiGLEAuthException waex) {
            // ALIBI: allow auth exception through
            sendBundledMessage( Status.FAIL.ordinal(), bundle );
            throw waex;
        } catch (final JSONException ex) {
            sendBundledMessage( Status.FAIL.ordinal(), bundle );
            Logging.error("ex: " + ex + " result: " + result, ex);
        }
    }

    /**
     * write string data to a file accessible for Intent-based share or view
     * @param result the result of the operation
     * @param filename the filename to write
     * @throws IOException on failure to write
     */
    protected void writeSharefile(final String result, final String filename) throws IOException {

        if (FileUtility.hasSD()) {
            if (outputFileName != null) {
                //DEBUG: FileUtility.printDirContents(new File(MainActivity.getSDPath()));
                //ALIBI: for external-storage, our existing "cache" method is fine to write the file
                cacheResult(result);
                //DEBUG: FileUtility.printDirContents(new File(MainActivity.getSDPath()));
            }
        } else {
            //ALIBI: building a special directory for KML for intents
            // the app files directory might have been enough here, but helps with provider_paths

            //see if KML dir exists
            Logging.info("local storage DL...");

            File kmlPath = new File(FileUtility.getKmlPath(context));
            if (!kmlPath.exists()) {
                //noinspection ResultOfMethodCallIgnored
                kmlPath.mkdir();
            }
            //DEBUG: FileUtility.printDirContents(kmlPath);
            if (kmlPath.exists() && kmlPath.isDirectory()) {
                //DEBUG: MainActivity.info("... file output directory found");
                File kmlFile = new File(kmlPath, filename);
                FileOutputStream out = new FileOutputStream(kmlFile);
                ObservationUploader.writeFos(out, result);
                //DEBUG: FileUtility.printDirContents(kmlPath);
            }
        }
    }
}
