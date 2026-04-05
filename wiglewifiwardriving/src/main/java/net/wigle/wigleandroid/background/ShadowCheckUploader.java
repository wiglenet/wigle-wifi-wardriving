package net.wigle.wigleandroid.background;

import android.content.SharedPreferences;
import android.os.Bundle;
import androidx.fragment.app.FragmentActivity;

import net.wigle.wigleandroid.MainActivity;
import net.wigle.wigleandroid.WiGLEAuthException;
import net.wigle.wigleandroid.db.DatabaseHelper;
import net.wigle.wigleandroid.util.Logging;
import net.wigle.wigleandroid.util.PreferenceKeys;
import net.wigle.wigleandroid.util.UrlConfig;

import org.json.JSONObject;

import java.io.File;
import java.io.IOException;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * Upload the database to ShadowCheck using the 2-step presigned URL pattern.
 */
public class ShadowCheckUploader extends AbstractProgressApiRequest {

    public ShadowCheckUploader(final FragmentActivity context, final DatabaseHelper dbHelper, final ApiListener listener) {
        super(context, dbHelper, "ShadowUL", null, UrlConfig.SHADOWCHECK_POST_URL, false,
                false, false, false,
                AbstractApiRequest.REQUEST_POST, listener, true);
    }

    @Override
    protected void subRun() throws WiGLEAuthException {
        try {
            doUpload();
        } catch ( final InterruptedException ex ) {
            Logging.info( "shadowcheck upload interrupted" );
        } catch ( final Throwable throwable ) {
            MainActivity.writeError( Thread.currentThread(), throwable, context );
            throw new RuntimeException( "ShadowCheckUploader throwable: " + throwable, throwable );
        }
    }

    private void doUpload() throws InterruptedException {
        final Bundle bundle = new Bundle();
        sendBundledMessage( Status.UPLOADING.ordinal(), bundle );

        final File dbFile = dbHelper.getDbFile();
        if (dbFile == null || !dbFile.exists()) {
            Logging.error("DB file not found for ShadowCheck upload");
            sendBundledMessage( Status.FAIL.ordinal(), bundle );
            return;
        }

        final SharedPreferences prefs = context.getSharedPreferences(PreferenceKeys.SHARED_PREFS, 0);
        final String caseId = prefs.getString(PreferenceKeys.PREF_CASE_ID, "");

        // PHASE A: Request presigned URL
        JSONObject jsonRequest = new JSONObject();
        try {
            jsonRequest.put("fileName", dbFile.getName());
            if (!caseId.isEmpty()) {
                jsonRequest.put("case_id", caseId);
            }
        } catch (Exception e) {
            Logging.error("Error creating upload request JSON: " + e);
            sendBundledMessage(Status.FAIL.ordinal(), bundle);
            return;
        }

        RequestBody requestBody = RequestBody.create(jsonRequest.toString(), MediaType.parse("application/json; charset=utf-8"));
        Request request = new Request.Builder()
                .url(UrlConfig.SHADOWCHECK_POST_URL)
                .addHeader("Authorization", "Bearer " + UrlConfig.SHADOWCHECK_API_KEY)
                .post(requestBody)
                .build();

        OkHttpClient client = new OkHttpClient();
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Logging.error("ShadowCheck upload request failed: " + e);
                sendBundledMessage( Status.FAIL.ordinal(), bundle );
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful()) {
                    try {
                        String responseBody = response.body().string();
                        JSONObject jsonResponse = new JSONObject(responseBody);
                        String uploadUrl = jsonResponse.getString("uploadUrl");
                        String s3Key = jsonResponse.optString("s3Key", "");
                        
                        // PHASE B: PUT binary data to presigned URL
                        doPutUpload(uploadUrl, s3Key, dbFile, bundle);
                    } catch (Exception e) {
                        Logging.error("Error parsing upload response: " + e);
                        sendBundledMessage(Status.FAIL.ordinal(), bundle);
                    }
                } else {
                    Logging.error("ShadowCheck upload request failed: " + response.code());
                    sendBundledMessage( Status.FAIL.ordinal(), bundle );
                }
                response.close();
            }
        });
    }

    private void doPutUpload(String uploadUrl, String s3Key, File dbFile, final Bundle bundle) {
        RequestBody putBody = RequestBody.create(dbFile, MediaType.parse("application/x-sqlite3"));
        CountingRequestBody countingBody = new CountingRequestBody(putBody, (bytesWritten, contentLength) -> {
            int progress = (int) ((bytesWritten * 1000) / contentLength);
            if (progress >= 0) {
                getHandler().sendEmptyMessage(BackgroundGuiHandler.WRITING_PERCENT_START + progress);
            }
        });

        // NOTE: No Authorization header here - presigned URL is self-authenticating
        Request putRequest = new Request.Builder()
                .url(uploadUrl)
                .put(countingBody)
                .build();

        OkHttpClient client = new OkHttpClient();
        client.newCall(putRequest).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Logging.error("ShadowCheck S3 PUT failed: " + e);
                sendBundledMessage( Status.FAIL.ordinal(), bundle );
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful()) {
                    Logging.info("ShadowCheck upload successful: " + s3Key);
                    // PHASE C: Completion
                    bundle.putString(BackgroundGuiHandler.TRANSIDS, s3Key);
                    sendBundledMessage( Status.SUCCESS.ordinal(), bundle );
                } else {
                    Logging.error("ShadowCheck S3 PUT failed: " + response.code());
                    sendBundledMessage( Status.FAIL.ordinal(), bundle );
                }
                response.close();
            }
        });
    }
}
