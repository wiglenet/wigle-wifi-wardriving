package net.wigle.wigleandroid.background;

import android.os.Handler;

import net.wigle.wigleandroid.util.Logging;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Credentials;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * Alternative upload to HttpFileUploader - use of OkHttp directly gives us several benefits:
 * 1. interruptability (cancel button)
 * 2. configurability (we can set all sorts of options and failure handling on OkHttp)
 * 3. ability to get better debugging data and handle gracefully in case of failure. (byzantine failures, SSL problems)
 * Also: "deleted code is debugged code." -orn
 *
 * NOTE: OkHttp is the underlying implementation in HttpUrlConnection beginning in android 4.4, but this is included for compat with android versions below that
 *
 * @author arkasha
 * @date 20180901
 */
public class OkFileUploader {

    /**
     * don't allow construction
     */
    private OkFileUploader() {
    }


    /**
     * simple upload method. Analogous to {@link net.wigle.wigleandroid.background.HttpFileUploader#upload HttpFileUploader.upload(...)}
     * @param urlString the URL string for the target
     * @param filename the file name on-device to upload
     * @param fileParamName parameter name for the file
     * @param params additional parameters ("donate" at time of implementation)
     * @param authUser auth user parameter for Basic authentication
     * @param authToken password parameter for Basic authentication
     * @param handler the callback for progress or completion
     * @return string payload (JSON) from the upload POST
     * @throws IOException
     */
    public static String upload(final String urlString, final String filename, final String fileParamName,
                                final Map<String, String> params,
                                final String authUser, final String authToken,
                                final Handler handler)
            throws IOException {


        // construct multipart body using file reference
        MultipartBody.Builder builder = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart(fileParamName, filename,
                        RequestBody.create(MediaType.parse("application/octet-stream"),
                                new File(filename)));

        // add params if present
        if (!params.isEmpty()) {
            for ( Map.Entry<String, String> entry : params.entrySet() ) {

                builder.addFormDataPart(entry.getKey(), entry.getValue());
            }
        }

        MultipartBody requestBody = builder.build();


        // progress-aware requestBody
        CountingRequestBody countingBody
                = new CountingRequestBody(requestBody, new CountingRequestBody.UploadProgressListener() {
            @Override
            public void onRequestProgress(long bytesWritten, long contentLength) {
                int progress = (int)((bytesWritten*1000) / contentLength );
                Logging.info("progress: "+ progress + "("+bytesWritten +"/"+contentLength+")");
                if ( handler != null && progress >= 0 ) {
                    //TODO: we can improve this, but minimal risk dictates reuse of old technique to start
                    handler.sendEmptyMessage( BackgroundGuiHandler.WRITING_PERCENT_START + progress );
                }
            }
        });

        // authorization if necessary
        Request request;
        if ((authToken != null) && !authToken.isEmpty()) {
            request = new Request.Builder()
                    .url(urlString)
                    .addHeader("Authorization", Credentials.basic(authUser, authToken))
                    .post(countingBody)
                    .build();
        } else {
            request = new Request.Builder()
                    .url(urlString)
                    .post(countingBody)
                    .build();
        }

        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS).build();

        Call call = client.newCall(request);
        Response response = call.execute();

        if (response.code() == 200) {
            return response.body().string();
        } else {
            return null;
        }

    }
}
