package net.wigle.wigleandroid.background;

import android.location.Location;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.MappingJsonFactory;

import net.wigle.wigleandroid.DatabaseHelper;
import net.wigle.wigleandroid.ListFragment;
import net.wigle.wigleandroid.MainActivity;
import net.wigle.wigleandroid.WiGLEAuthException;
import net.wigle.wigleandroid.model.Network;
import net.wigle.wigleandroid.model.NetworkType;


import java.io.BufferedReader;
import java.io.IOException;

/**
 * Use the ApiDL infra to get points from the v2 API
 * Created by arkasha on 1/28/17.
 */
public class ObservationImporter extends AbstractProgressApiRequest {

    private Status status;

    public ObservationImporter(final FragmentActivity context,
                               final DatabaseHelper dbHelper, final ApiListener listener) {
        super(context, dbHelper, "HttpDL", "observed-cache.json", MainActivity.OBSERVED_URL, false,
                true, true, false,
                AbstractApiRequest.REQUEST_GET, listener, true);
    }

    @Override
    protected void subRun() throws IOException, InterruptedException {
        status = Status.UNKNOWN;
        final Bundle bundle = new Bundle();
        sendBundledMessage( Status.DOWNLOADING.ordinal(), bundle );

        String result = null;
        try {
            result = doDownload(this.connectionMethod);
        } catch (InterruptedException e) {
            e.printStackTrace();
            status = Status.EXCEPTION;
            bundle.putString(BackgroundGuiHandler.ERROR, "Connection problem: " + e);
        } catch (final Exception e) {
            e.printStackTrace();
            status = Status.EXCEPTION;
            bundle.putString(BackgroundGuiHandler.ERROR, "ERROR: " + e + " (from " + e.getCause()+")");
        } finally {
            MainActivity.info("User observed download result: "+result);
            sendBundledMessage(status.ordinal(), bundle);
        }
    }

    @Override
    protected String getResultString(final BufferedReader reader) throws IOException, InterruptedException {
        Bundle bundle = new Bundle();
        try {
            JsonFactory f = new MappingJsonFactory();
            JsonParser jp = f.createParser(reader);
            JsonToken current;
            current = jp.nextToken();
            if (current != JsonToken.START_OBJECT) {
                System.out.println("Error: root should be object: quiting.");
                return null;
            }
            Integer total = 0;
            while (jp.nextToken() != JsonToken.END_OBJECT) {
                String fieldName = jp.getCurrentName();
                current = jp.nextToken();
                if (fieldName.equals("success")) {
                    if (current.isBoolean()) {
                        if (current == JsonToken.VALUE_TRUE) {
                            MainActivity.info("successful load");
                        } else {
                            MainActivity.error("MyObserved success: false");
                            status = Status.EXCEPTION;
                            bundle.putString(BackgroundGuiHandler.ERROR, "ERROR: success: false");
                        }
                    }
                } else if (fieldName.equals("count")) {
                    total = jp.getIntValue();
                    MainActivity.info("received " + total + " observations");
                    if (total > 0) {
                        status = Status.SUCCESS;
                    }
                } else if (fieldName.equals("results")) {
                    if (current == JsonToken.START_ARRAY) {
                        // For each of the records in the array
                        int i = 0;
                        while (jp.nextToken() != JsonToken.END_ARRAY) {
                            String netId = jp.getValueAsString();
                            //DEBUG: MainActivity.info(netId);
                            final String ssid = "";
                            final int frequency = 0;
                            final String capabilities = "";
                            final int level = 0;
                            final Network network = new Network(netId, ssid, frequency,
                                capabilities, level, NetworkType.WIFI);
                            final Location location = new Location("wigle");
                            final boolean newForRun = true;
                            ListFragment.lameStatic.dbHelper.blockingAddObservation(
                                network, location, newForRun);

                            if ((i % 1000) == 0) {
                                MainActivity.info("lineCount: " + i + " of " + total);
                            }
                            if (total == 0) {
                                total = 1;
                            }
                            final int percentDone = (i * 1000) / total;
                            sendPercentTimesTen(percentDone, bundle);
                            i++;
                        }
                    } else {
                        System.out.println("Error: records should be an array: skipping.");
                        jp.skipChildren();
                    }
                } else {
                    System.out.println("Unprocessed property: " + fieldName);
                    jp.skipChildren();
                }
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
            status = Status.EXCEPTION;
            bundle.putString(BackgroundGuiHandler.ERROR, "Connection problem: " + e);
            throw e;
        } catch (Exception e) {
            e.printStackTrace();
            status = Status.EXCEPTION;
            bundle.putString(BackgroundGuiHandler.ERROR, "ERROR: " + e + " (from " + e.getCause() + ")");
            return status.toString();
        } finally {
            if (status == null) {
                status = Status.FAIL;
            }

            try {
                listener.requestComplete(null, false);
            } catch (WiGLEAuthException waex) {
                MainActivity.error("Unable to download data - authorization failed");
                status = Status.BAD_LOGIN;
            }
        }
        return status.toString();
    }

}
