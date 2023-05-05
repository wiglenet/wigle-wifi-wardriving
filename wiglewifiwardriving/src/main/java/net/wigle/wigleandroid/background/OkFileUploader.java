package net.wigle.wigleandroid.background;

import android.os.Build;
import android.os.Handler;

import net.wigle.wigleandroid.util.Logging;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLContext;

import okhttp3.Call;
import okhttp3.ConnectionSpec;
import okhttp3.Credentials;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.TlsVersion;

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

        OkHttpClient.Builder clientBuilder = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS);

        Call call = enableTls12OnPreLollipop(clientBuilder).build().newCall(request);
        Response response = call.execute();

        if (response.code() == 200) {
            return response.body().string();
        } else {
            return null;
        }
    }

    public static OkHttpClient.Builder enableTls12OnPreLollipop(OkHttpClient.Builder builder) {
        if (Build.VERSION.SDK_INT >= 16 && Build.VERSION.SDK_INT < 22) {
            try {
                SSLContext sc = SSLContext.getInstance("TLSv1.2");
                sc.init(null, null, null);
                builder.sslSocketFactory(new Tls12SocketFactory(sc.getSocketFactory()));

                ConnectionSpec cs = new ConnectionSpec.Builder(ConnectionSpec.MODERN_TLS)
                        .tlsVersions(TlsVersion.TLS_1_2)
                        .build();

                List<ConnectionSpec> specs = new ArrayList<>();
                specs.add(cs);
                specs.add(ConnectionSpec.COMPATIBLE_TLS);
                specs.add(ConnectionSpec.CLEARTEXT);

                builder.connectionSpecs(specs);
            } catch (Exception exc) {
                Logging.error("Error while setting TLS 1.2", exc);
            }
        }
        return builder;
    }
}
