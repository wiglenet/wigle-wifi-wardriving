package net.wigle.wigleandroid.background;

import android.os.Bundle;

import androidx.fragment.app.FragmentActivity;

import net.wigle.wigleandroid.MainActivity;
import net.wigle.wigleandroid.WiGLEAuthException;
import net.wigle.wigleandroid.db.DatabaseHelper;
import net.wigle.wigleandroid.util.FileUtility;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

import static net.wigle.wigleandroid.util.FileUtility.CSV_GZ_EXT;

/**
 * A CSV-upload grabber intended for re-import of CSV files
 * Created by arkasha on 01/25/20.
 */

public class CsvDownloader extends AbstractProgressApiRequest {
    private Status status;

    public CsvDownloader(final FragmentActivity context, final DatabaseHelper dbHelper /*TODO: not needed?*/,
                         final String transid, final ApiListener listener) {
        super(context, dbHelper, "CsvDL", transid+CSV_GZ_EXT, MainActivity.CSV_TRANSID_URL_STEM+transid, false,
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

            //TODO: process file?!

            final JSONObject json = new JSONObject("{success: " + true + ", file:\"" +
                    FileUtility.getCsvGzFile(context, outputFileName) + "\"}");
            sendBundledMessage( Status.SUCCESS.ordinal(), bundle );
            listener.requestComplete(json, false);
        } catch (final WiGLEAuthException waex) {
            // ALIBI: allow auth exception through
            sendBundledMessage( Status.FAIL.ordinal(), bundle );
            throw waex;
        } catch (final JSONException ex) {
            sendBundledMessage( Status.FAIL.ordinal(), bundle );
            MainActivity.error("ex: " + ex + " result: " + result, ex);
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

            //TODO: ALL THIS MUST CHANGE
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
            MainActivity.info("local storage DL...");

            File csvFile = FileUtility.getCsvGzFile(context, filename);
            FileOutputStream out = new FileOutputStream(csvFile);
            ObservationUploader.writeFos(out, result);
        }
    }
}
