package net.wigle.wigleandroid.background;

import android.os.Handler;

import net.wigle.wigleandroid.MainActivity;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Map;

import okhttp3.Call;
import okhttp3.Credentials;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class OkFileUploader {

    /**
     * don't allow construction
     */
    private OkFileUploader() {
    }

    public static String upload(final String urlString, final String filename, final String fileParamName,
                                final Map<String, String> params,
                                final String authUser, final String authToken,
                                final Handler handler)
            throws IOException {


        MultipartBody.Builder builder = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", fileParamName,
                        RequestBody.create(MediaType.parse("application/octet-stream"),
                                new File(filename)));
        if (!params.isEmpty()) {
            for ( Map.Entry<String, String> entry : params.entrySet() ) {

                builder.addFormDataPart(entry.getKey(), entry.getValue());
            }
        }

        MultipartBody requestBody = builder.build();


        CountingRequestBody countingBody
                = new CountingRequestBody(requestBody, new CountingRequestBody.Listener() {
            @Override
            public void onRequestProgress(long bytesWritten, long contentLength) {
                int progress = (int)((bytesWritten*1000) / contentLength );
                MainActivity.info("progress: "+ progress + "("+bytesWritten +"/"+contentLength+")");
                if ( handler != null && progress >= 0 ) {
                    handler.sendEmptyMessage( BackgroundGuiHandler.WRITING_PERCENT_START + progress );
                }
            }
        });

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

        OkHttpClient client = new OkHttpClient.Builder().build();

        Call call = client.newCall(request);
        Response response = call.execute();

        if (response.code() == 200) {
            return response.body().string();
        } else {
            return null;
        }

    }

}

