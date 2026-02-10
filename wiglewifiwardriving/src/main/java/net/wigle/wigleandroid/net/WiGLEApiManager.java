package net.wigle.wigleandroid.net;

import static net.wigle.wigleandroid.util.UrlConfig.API_DOMAIN;
import static net.wigle.wigleandroid.util.UrlConfig.FILE_POST_URL;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;

import androidx.annotation.NonNull;

import com.appmattus.certificatetransparency.CTInterceptorBuilder;
import com.appmattus.certificatetransparency.CTLogger;
import com.appmattus.certificatetransparency.VerificationResult;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;

import net.wigle.wigleandroid.TokenAccess;
import net.wigle.wigleandroid.background.BackgroundGuiHandler;
import net.wigle.wigleandroid.background.CountingRequestBody;
import net.wigle.wigleandroid.background.Status;
import net.wigle.wigleandroid.model.api.ApiTokenResponse;
import net.wigle.wigleandroid.model.api.BtSearchResponse;
import net.wigle.wigleandroid.model.api.CellSearchResponse;
import net.wigle.wigleandroid.model.api.RankResponse;
import net.wigle.wigleandroid.model.api.UploadReseponse;
import net.wigle.wigleandroid.model.api.UploadsResponse;
import net.wigle.wigleandroid.model.api.UserStats;
import net.wigle.wigleandroid.model.api.WiFiSearchResponse;
import net.wigle.wigleandroid.model.api.WiGLENews;
import net.wigle.wigleandroid.util.FileAccess;
import net.wigle.wigleandroid.util.FileUtility;
import net.wigle.wigleandroid.util.Logging;
import net.wigle.wigleandroid.util.PreferenceKeys;
import net.wigle.wigleandroid.util.UrlConfig;

import org.jetbrains.annotations.NotNull;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.FormBody;
import okhttp3.Interceptor;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * Implementation of most of the WiGLE API as used by the app.
 * @author arkasha
 */
public class WiGLEApiManager {

    public static final int CONN_TIMEOUT_S = 45;
    public static final int WRITE_TIMEOUT_S = 210;
    public static final int READ_TIMEOUT_S = 230;

    private static final int LOCAL_FAILURE_CODE = 999;
    private static final String NEWS_CACHE = "news-cache.json";
    private static final String SITE_STATS_CACHE = "site-stats-cache.json";
    private static final String USER_STATS_CACHE = "user-stats-cache.json";

    public static final String USER_AGENT;
    static {
        String javaVersion = "unknown";
        try {
            javaVersion = System.getProperty("java.vendor") + " " +
                    System.getProperty("java.version") + ", jvm: " +
                    System.getProperty("java.vm.vendor") + " " +
                    System.getProperty("java.vm.name") + " " +
                    System.getProperty("java.vm.version") + " on " +
                    System.getProperty("os.name") + " " +
                    System.getProperty("os.version") +
                    " [" + System.getProperty("os.arch") + "]";
            javaVersion = javaVersion.replaceAll("[^\\x00-\\x7F]", "");
        } catch (RuntimeException e) {
            Logging.error("Unable to get Java version for user agent string: ",e);
        }
        USER_AGENT = "WigleWifi (" + javaVersion + ")";
    }

    private final OkHttpClient authedClient;
    private final OkHttpClient unauthedClient;
    private final Context context;

    private static final CTInterceptorBuilder ctIB = new CTInterceptorBuilder();

    private static final CTLogger ctLogger = new CTLogger() {
        @Override
        public void log(@NonNull String host, @NonNull VerificationResult result) {
            Logging.info("[CERTTRANS] "+result);
        }
    };

    // certificate transparency interceptor
    private final static Interceptor certTransparencyInterceptor = ctIB.includeHost(API_DOMAIN)
            .setLogger(ctLogger).build();

    /**
     * Build a WiGLEApiManager
     * @param prefs the preferences for the app to configure authentication
     * @param context the {@link android.content.Context} object to use in creating the instance.
     */
    public WiGLEApiManager(final SharedPreferences prefs, Context context) {
        super();
        this.context = context;
        //authed connection to WiGLE
        this.authedClient = hasAuthed(prefs) ? new OkHttpClient.Builder()
                .addNetworkInterceptor(certTransparencyInterceptor)
                .addInterceptor(new BasicAuthInterceptor(prefs))
                .addInterceptor(new Interceptor() {
                    @NotNull
                    @Override
                    public Response intercept(@NotNull Chain chain) throws IOException {
                        Request originalRequest = chain.request();
                        Request requestWithUserAgent = originalRequest.newBuilder()
                                .header("User-Agent", USER_AGENT)
                                .build();
                        return chain.proceed(requestWithUserAgent);
                    }
                })
                .connectTimeout(CONN_TIMEOUT_S, TimeUnit.SECONDS)
                .writeTimeout(WRITE_TIMEOUT_S, TimeUnit.SECONDS)
                .readTimeout(READ_TIMEOUT_S, TimeUnit.SECONDS).build():null;
        //un-authed connection to WiGLE
        this.unauthedClient = new OkHttpClient.Builder()
                .addNetworkInterceptor(certTransparencyInterceptor)
                .addInterceptor(new Interceptor() {
                    @NotNull
                    @Override
                    public Response intercept(@NotNull Chain chain) throws IOException {
                        Request originalRequest = chain.request();
                        Request requestWithUserAgent = originalRequest.newBuilder()
                                .header("User-Agent", USER_AGENT)
                                .build();
                        return chain.proceed(requestWithUserAgent);
                    }
                })
                .connectTimeout(CONN_TIMEOUT_S, TimeUnit.SECONDS)
                .writeTimeout(WRITE_TIMEOUT_S, TimeUnit.SECONDS)
                .readTimeout(READ_TIMEOUT_S, TimeUnit.SECONDS).build();
    }

    /**
     * Get the user stats from the URL configured in the {@link net.wigle.wigleandroid.util.UrlConfig} class
     * @param completedListener the completion listener to call with results.
     */
    public void getUserStats(@NotNull final AuthenticatedRequestCompletedListener<UserStats,
            JSONObject> completedListener) {
        Request request = new Request.Builder()
                .url(UrlConfig.USER_STATS_URL)
                .build();
        if (null == authedClient) {
            completedListener.onAuthenticationRequired();
            return;
        }
        authedClient.newCall(request).enqueue(new Callback() {
            final Handler mainHandler = new Handler(Looper.getMainLooper());

            @Override public void onFailure(@NotNull Call call, @NotNull IOException e) {
                final UserStats s = readCached(USER_STATS_CACHE, context,
                        UserStats.class);
                if (null != s) {
                    Logging.warn("Network request for user stats failed, returning cached value");
                    completedListener.onTaskSucceeded(s);
                    mainHandler.post(completedListener::onTaskCompleted);
                    return;
                }
                onCallFailure("Unsuccessful WiGLE user stats request: ", e,
                        completedListener, mainHandler, null);
            }

            @Override public void onResponse(@NotNull Call call, @NotNull Response response) throws IOException {
                try (ResponseBody responseBody = response.body()) {
                    if (!response.isSuccessful()) {
                        completedListener.onTaskFailed(response.code(), null);
                    } else if (response.code() == 401) {
                        completedListener.onAuthenticationRequired();
                        call.cancel();
                    } else {
                        if (null != responseBody) {
                            final String responseBodyString = responseBody.string();
                            cacheResult(responseBodyString, USER_STATS_CACHE, context);
                            completedListener.onTaskSucceeded(new Gson().fromJson(responseBodyString,
                                    UserStats.class));
                        } else {
                            completedListener.onTaskFailed(LOCAL_FAILURE_CODE, null);
                        }
                        call.cancel();
                    }
                }
                mainHandler.post(completedListener::onTaskCompleted);
            }
        });
    }

    /**
     * Get the latest WiGLE front page news from the URL configured in the {@link net.wigle.wigleandroid.util.UrlConfig} class
     * @param completedListener the RequestCompletedListener instance to call on completion
     */
    public void getNews(@NotNull final RequestCompletedListener<WiGLENews,
            JSONObject> completedListener) {
        Request request = new Request.Builder()
                .url(UrlConfig.NEWS_URL)
                .build();
        unauthedClient.newCall(request).enqueue(new Callback() {
            final Handler mainHandler = new Handler(Looper.getMainLooper());

            @Override public void onFailure(@NotNull Call call, @NotNull IOException e) {
                final WiGLENews s = readCached(NEWS_CACHE, context,
                        WiGLENews.class);
                if (null != s) {
                    Logging.warn("Network request for news failed, returning cached value");
                    completedListener.onTaskSucceeded(s);
                    mainHandler.post(completedListener::onTaskCompleted);
                    return;
                }
                onCallFailure("Unsuccessful WiGLE News request: ", e,
                        completedListener, mainHandler, null);
            }

            @Override public void onResponse(@NotNull Call call, @NotNull Response response) throws IOException {
                try (ResponseBody responseBody = response.body()) {
                    if (!response.isSuccessful()) {
                        completedListener.onTaskFailed(response.code(), null);
                    } else {
                        if (null != responseBody) {
                            final String responseBodyString = responseBody.string();
                            cacheResult(responseBodyString, NEWS_CACHE, context);
                            completedListener.onTaskSucceeded(new Gson().fromJson(responseBodyString,
                                    WiGLENews.class));
                        } else {
                            completedListener.onTaskFailed(LOCAL_FAILURE_CODE, null);
                        }
                    }
                }
                mainHandler.post(completedListener::onTaskCompleted);
            }
        });
    }

    /**
     * Get the site stats from the URL configured in the {@link net.wigle.wigleandroid.util.UrlConfig} class
     * @param completedListener the RequestCompletedListener instance to call on completion
     */
    public void getSiteStats(@NotNull final RequestCompletedListener<Map<String,Long>,
            JSONObject> completedListener) {
        Request request = new Request.Builder()
                .url(UrlConfig.SITE_STATS_URL)
                .build();
        unauthedClient.newCall(request).enqueue(new Callback() {
            final Handler mainHandler = new Handler(Looper.getMainLooper());

            @Override public void onFailure(@NotNull Call call, @NotNull IOException e) {
                final Map<String,Long> s = readCached(SITE_STATS_CACHE, context,
                        Map.class);
                if (null != s) {
                    Logging.warn("Network request for site stats failed, returning cached value");
                    completedListener.onTaskSucceeded(s);
                    mainHandler.post(completedListener::onTaskCompleted);
                    return;
                }
                onCallFailure("Unsuccessful WiGLE News request: ", e,
                        completedListener, mainHandler, null);
            }

            @Override public void onResponse(@NotNull Call call, @NotNull Response response) throws IOException {
                try (ResponseBody responseBody = response.body()) {
                    if (!response.isSuccessful()) {
                        completedListener.onTaskFailed(response.code(), null);
                    } else {
                        if (null != responseBody) {
                            final String responseBodyString = responseBody.string();
                            cacheResult(responseBodyString, SITE_STATS_CACHE, context);
                            completedListener.onTaskSucceeded(new Gson().fromJson(responseBodyString,
                                    Map.class));
                        } else {
                            completedListener.onTaskFailed(LOCAL_FAILURE_CODE, null);
                        }
                    }
                }
                mainHandler.post(completedListener::onTaskCompleted);
            }
        });
    }

    /**
     * Given a username and password fetch the ANDROID {@link net.wigle.wigleandroid.model.api.ApiTokenResponse} from the URL configured in the {@link net.wigle.wigleandroid.util.UrlConfig} class
     * @param username The username supplied by the user
     * @param password The password supplied by the user
     * @param completedListener the RequestCompletedListener instance to call on completion
     */
    public void getApiToken(@NotNull String username, @NotNull String password, @NotNull final RequestCompletedListener<ApiTokenResponse,
        JSONObject> completedListener) {
        RequestBody requestBody = new FormBody.Builder()
                .add("credential_0", username)
                .add("credential_1", password)
                .add("type", "ANDROID")
                .build();
        Request request = new Request.Builder()
                    .url(UrlConfig.TOKEN_URL)
                    .post(requestBody)
                    .build();
        unauthedClient.newCall(request).enqueue(new Callback() {
            final Handler mainHandler = new Handler(Looper.getMainLooper());

            @Override public void onFailure(@NotNull Call call, @NotNull IOException e) {
                sendBundledMessage(Status.BAD_LOGIN.ordinal(), new Bundle());
                onCallFailure("Unsuccessful WiGLE Token request: ", e,
                        completedListener, mainHandler, null);
            }
            @Override public void onResponse(@NotNull Call call, @NotNull Response response) {
                try (ResponseBody responseBody = response.body()) {
                    if (!response.isSuccessful()) {
                        completedListener.onTaskFailed(response.code(), null);
                    } else {
                        if (null != responseBody) {
                            try {
                                completedListener.onTaskSucceeded(new Gson().fromJson(responseBody.charStream(),
                                    ApiTokenResponse.class));
                            } catch (JsonSyntaxException e) {
                                //ALIBI: sometimes java.net.SocketTimeoutException manifests as a JSE here?
                                completedListener.onTaskFailed(LOCAL_FAILURE_CODE, null);
                            }
                        } else {
                            completedListener.onTaskFailed(LOCAL_FAILURE_CODE, null);
                        }
                    }
                }
                mainHandler.post(completedListener::onTaskCompleted);
            }
        });
    }

    /**
     * Get the paginated user rank data from the URL configured in the {@link net.wigle.wigleandroid.util.UrlConfig} class
     * @param pageStart the starting page offset
     * @param pageEnd the ending page offset
     * @param sort the sort method ('discovered', 'total', 'monthcount', 'prevmonthcount', 'gendisc', 'gentotal', 'firsttransid', 'lasttransid')
     * @param selected the selected record to highlight
     * @param completedListener the RequestCompletedListener instance to call on completion
     */
    public void getRank(final long pageStart, final long pageEnd,
                        @NotNull final String sort,long selected,
                        @NotNull final RequestCompletedListener<RankResponse,
            JSONObject> completedListener) {
        final String httpUrl = UrlConfig.RANK_STATS_URL + "?pagestart=" + pageStart
                + "&pageend=" + pageEnd + "&sort=" + sort;

        Request request = new Request.Builder()
                .url(httpUrl)
                .build();
        OkHttpClient c = authedClient!=null?authedClient:unauthedClient;
        c.newCall(request).enqueue(new Callback() {
            final Handler mainHandler = new Handler(Looper.getMainLooper());

            @Override public void onFailure(@NotNull Call call, @NotNull IOException e) {
                onCallFailure("Unsuccessful WiGLE Token request: ", e,
                        completedListener, mainHandler, null);
            }
            @Override public void onResponse(@NotNull Call call, @NotNull Response response) {
                try (ResponseBody responseBody = response.body()) {
                    if (!response.isSuccessful()) {
                        completedListener.onTaskFailed(response.code(), null);
                    } else {
                        //TODO caching
                        if (null != responseBody) {
                            try {
                                RankResponse r = new Gson().fromJson(responseBody.charStream(),
                                        RankResponse.class);
                                r.setSelected(selected);
                                completedListener.onTaskSucceeded(r);
                            } catch (JsonSyntaxException e) {
                                //ALIBI: sometimes java.net.SocketTimeoutException manifests as a JSE here?
                                completedListener.onTaskFailed(LOCAL_FAILURE_CODE, null);
                            }
                        } else {
                            completedListener.onTaskFailed(LOCAL_FAILURE_CODE, null);
                        }
                    }
                }
                mainHandler.post(completedListener::onTaskCompleted);
            }
        });
    }

    /**
     * get the paginated list of uploads for the authenticated user from the URL configured in the {@link net.wigle.wigleandroid.util.UrlConfig} class
     * @param pageStart the offset for the start of the request page
     * @param pageEnd the offset for the end of the request page
     * @param completedListener the RequestCompletedListener instance to call on completion
     */
    public void getUploads(final long pageStart, final long pageEnd, @NotNull final AuthenticatedRequestCompletedListener<UploadsResponse,
            JSONObject> completedListener ) {
        if (null == authedClient) {
            completedListener.onAuthenticationRequired();
            return;
        }
        final String httpUrl = UrlConfig.UPLOADS_STATS_URL + "?pagestart=" + pageStart
                + "&pageend=" + pageEnd;
        Request request = new Request.Builder()
                .url(httpUrl)
                .build();
        final Handler mainHandler = new Handler(Looper.getMainLooper());
        authedClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) {
                try (ResponseBody responseBody = response.body()) {
                    if (!response.isSuccessful()) {
                        completedListener.onTaskFailed(response.code(), null);
                    } else if (response.code() == 401) {
                        completedListener.onAuthenticationRequired();
                    } else {
                        if (null != responseBody) {
                            try {
                                UploadsResponse r = new Gson().fromJson(responseBody.charStream(),
                                        UploadsResponse.class);
                                completedListener.onTaskSucceeded(r);
                            } catch (JsonSyntaxException e) {
                                //ALIBI: sometimes java.net.SocketTimeoutException manifests as a JSE here?
                                completedListener.onTaskFailed(LOCAL_FAILURE_CODE, null);
                            }

                        } else {
                            completedListener.onTaskFailed(LOCAL_FAILURE_CODE, null);
                        }
                    }
                }
                mainHandler.post(completedListener::onTaskCompleted);
           }

            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                onCallFailure("Unsuccessful WiGLE Uploads request: ", e,
                        completedListener, mainHandler, null);
            }
        });
    }

    /**
     * Search the WiGLE WiFi database as the authenticated user from the URL configured in the {@link net.wigle.wigleandroid.util.UrlConfig} class
     * @param urlEncodedQueryParams a URL-encoded string including the query parameters //TODO: break these out - direct port of old methods
     * @param completedListener the RequestCompletedListener instance to call on completion
     */
    public void searchWiFi(@NotNull final String urlEncodedQueryParams,
                        @NotNull final AuthenticatedRequestCompletedListener<WiFiSearchResponse,
                                JSONObject> completedListener) {
        if (null == authedClient) {
            completedListener.onAuthenticationRequired();
        }
        final String httpUrl = UrlConfig.SEARCH_WIFI_URL + "?" + urlEncodedQueryParams;

        Request request = new Request.Builder()
                .url(httpUrl)
                .build();
        if (authedClient == null) {
            Logging.warn("searchWifi authedClient is null, returning");
            return;
        }
        authedClient.newCall(request).enqueue(new Callback() {
            final Handler mainHandler = new Handler(Looper.getMainLooper());

            @Override public void onFailure(@NotNull Call call, @NotNull IOException e) {
                onCallFailure("Unsuccessful WiGLE WiFi Search request: ", e,
                        completedListener, mainHandler, null);
            }
            @Override public void onResponse(@NotNull Call call, @NotNull Response response) {
                try (ResponseBody responseBody = response.body()) {
                    if (!response.isSuccessful()) {
                        completedListener.onTaskFailed(response.code(), null);
                    } else if (response.code() == 401) {
                        completedListener.onAuthenticationRequired();
                    } else {
                        //TODO- consider caching implications here
                        if (null != responseBody) {
                            try {
                                WiFiSearchResponse r = new Gson().fromJson(responseBody.charStream(),
                                        WiFiSearchResponse.class);
                                completedListener.onTaskSucceeded(r);
                            } catch (JsonSyntaxException e) {
                                //ALIBI: sometimes java.net.SocketTimeoutException manifests as a JSE here?
                                completedListener.onTaskFailed(LOCAL_FAILURE_CODE, null);
                            }
                        } else {
                            completedListener.onTaskFailed(LOCAL_FAILURE_CODE, null);
                        }
                    }
                }
                mainHandler.post(completedListener::onTaskCompleted);
            }
        });
    }

    /**
     * Search the WiGLE Cell database as the authenticated user from the URL configured in the {@link net.wigle.wigleandroid.util.UrlConfig} class
     * @param urlEncodedQueryParams a URL-encoded string including the query parameters //TODO: break these out - direct port of old methods
     * @param completedListener the RequestCompletedListener instance to call on completion
     */
    public void searchCell(@NotNull final String urlEncodedQueryParams,
                           @NotNull final AuthenticatedRequestCompletedListener<CellSearchResponse,
                                   JSONObject> completedListener) {
        if (null == authedClient) {
            completedListener.onAuthenticationRequired();
        }
        final String httpUrl = UrlConfig.SEARCH_CELL_URL + "?" + urlEncodedQueryParams;

        Request request = new Request.Builder()
                .url(httpUrl)
                .build();
        if (authedClient == null) {
            Logging.warn("searchCell authedClient is null, returning");
            return;
        }
        authedClient.newCall(request).enqueue(new Callback() {
            final Handler mainHandler = new Handler(Looper.getMainLooper());

            @Override public void onFailure(@NotNull Call call, @NotNull IOException e) {
                onCallFailure("Unsuccessful WiGLE Cell Search request: ", e,
                        completedListener, mainHandler, null);
            }
            @Override public void onResponse(@NotNull Call call, @NotNull Response response) {
                try (ResponseBody responseBody = response.body()) {
                    if (!response.isSuccessful()) {
                        completedListener.onTaskFailed(response.code(), null);
                    } else if (response.code() == 401) {
                        completedListener.onAuthenticationRequired();
                    } else {
                        //TODO- consider caching implications here
                        if (null != responseBody) {
                            try {
                                CellSearchResponse r = new Gson().fromJson(responseBody.charStream(),
                                        CellSearchResponse.class);
                                completedListener.onTaskSucceeded(r);
                            } catch (JsonSyntaxException e) {
                                //ALIBI: sometimes java.net.SocketTimeoutException manifests as a JSE here?
                                completedListener.onTaskFailed(LOCAL_FAILURE_CODE, null);
                            }
                        } else {
                            completedListener.onTaskFailed(LOCAL_FAILURE_CODE, null);
                        }
                    }
                }
                mainHandler.post(completedListener::onTaskCompleted);
            }
        });
    }

    /**
     * Search the WiGLE BT database as the authenticated user from the URL configured in the {@link net.wigle.wigleandroid.util.UrlConfig} class
     * @param urlEncodedQueryParams a URL-encoded string including the query parameters //TODO: break these out - direct port of old methods
     * @param completedListener the RequestCompletedListener instance to call on completion
     */
    public void searchBt(@NotNull final String urlEncodedQueryParams,
                           @NotNull final AuthenticatedRequestCompletedListener<BtSearchResponse,
                                   JSONObject> completedListener) {
        if (null == authedClient) {
            completedListener.onAuthenticationRequired();
        }
        final String httpUrl = UrlConfig.SEARCH_BT_URL + "?" + urlEncodedQueryParams;
        Request request = new Request.Builder()
                .url(httpUrl)
                .build();
        if (authedClient == null) {
            Logging.warn("searchBt authedClient is null, returning");
            return;
        }
        authedClient.newCall(request).enqueue(new Callback() {
            final Handler mainHandler = new Handler(Looper.getMainLooper());

            @Override public void onFailure(@NotNull Call call, @NotNull IOException e) {
                onCallFailure("Unsuccessful WiGLE BT Search request: ", e,
                        completedListener, mainHandler, null);
            }
            @Override public void onResponse(@NotNull Call call, @NotNull Response response) {
                try (ResponseBody responseBody = response.body()) {
                    if (!response.isSuccessful()) {
                        completedListener.onTaskFailed(response.code(), null);
                    } else if (response.code() == 401) {
                        completedListener.onAuthenticationRequired();
                    } else {
                        //TODO- consider caching implications here
                        if (null != responseBody) {
                            try {
                                BtSearchResponse r = new Gson().fromJson(responseBody.charStream(),
                                        BtSearchResponse.class);
                                completedListener.onTaskSucceeded(r);
                            } catch (JsonSyntaxException e) {
                                //ALIBI: sometimes java.net.SocketTimeoutException manifests as a JSE here?
                                completedListener.onTaskFailed(LOCAL_FAILURE_CODE, null);
                            }
                        } else {
                            completedListener.onTaskFailed(LOCAL_FAILURE_CODE, null);
                        }
                    }
                }
                mainHandler.post(completedListener::onTaskCompleted);
            }
        });
    }

    /**
     * Upload a (CSV) file
     * @param filename the file name on-device to upload
     * @param fileParamName parameter name for the file
     * @param params additional parameters ("donate" at time of implementation)
     * @param handler the callback for progress or completion
     * @param completedListener the RequestCompletedListener implementation to call on completion
     */
    public void upload(@NotNull final String filename,
                       @NotNull final String fileParamName,
                       @NotNull final Map<String, String> params,
                       final Handler handler,
                       @NotNull final RequestCompletedListener<UploadReseponse,
            JSONObject> completedListener) {
        MultipartBody.Builder builder = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart(fileParamName, filename,
                        RequestBody.create(new File(filename), MediaType.parse("application/octet-stream")));
        // add params if present
        if (!params.isEmpty()) {
            for ( Map.Entry<String, String> entry : params.entrySet() ) {
                builder.addFormDataPart(entry.getKey(), entry.getValue());
            }
        }
        MultipartBody requestBody = builder.build();
        // progress-aware requestBody
        CountingRequestBody countingBody
                = new CountingRequestBody(requestBody, (bytesWritten, contentLength) -> {
            int progress = (int)((bytesWritten*1000) / contentLength );
            Logging.info("progress: "+ progress + "("+bytesWritten +"/"+contentLength+")");
            if ( handler != null && progress >= 0 ) {
                //TODO: we can improve this, but minimal risk dictates reuse of old technique to start
                handler.sendEmptyMessage( BackgroundGuiHandler.WRITING_PERCENT_START + progress );
            }
        });
        OkHttpClient client = unauthedClient;
        if (authedClient != null) {
            client = authedClient;
        }
        Request request = new Request.Builder()
                .url(FILE_POST_URL)
                .post(countingBody)
                .build();

        client.newCall(request).enqueue(new Callback() {
                @Override
                public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                    if (!response.isSuccessful()) {
                        Logging.error("Failed to upload file: " + response.code() + " " + response.message());
                        completedListener.onTaskFailed(response.code(), null);
                    } else {
                        if (null != response.body()) {
                            try (ResponseBody responseBody = response.body()) {
                                final String responseBodyString = responseBody.string();
                                UploadReseponse r =  new Gson().fromJson(responseBodyString,
                                        UploadReseponse.class);
                                completedListener.onTaskSucceeded(r);
                            } catch (JsonSyntaxException e) {
                                //ALIBI: sometimes java.net.SocketTimeoutException manifests as a JSE here?
                                //TODO: deserialize failed response to get error and send to onTaskFailed?
                                completedListener.onTaskFailed(LOCAL_FAILURE_CODE, null);
                            }
                        } else {
                            completedListener.onTaskFailed(LOCAL_FAILURE_CODE, null);
                        }
                    }
                }

                @Override
                public void onFailure(@NonNull Call call, @NonNull IOException e) {
                    if (null != e) {
                        Logging.error("Failed to upload - client exception: "+ e.getClass() + " - " + e.getMessage());
                    } else {
                        Logging.error("Failed to upload - client call failed. (data: "+hasDataConnection(context.getApplicationContext())+")");
                    }
                    completedListener.onTaskFailed(LOCAL_FAILURE_CODE, null);
                }
            }
        );
    }

    /**
     * Check to see if we have a working data connection
     * @param context context to use to get the {@link android.net.ConnectivityManager} used to check for connection
     * @return true if there's an active connection, otherwise false.
     */
    public static boolean hasDataConnection(final Context context) {
        ConnectivityManager connectivityManager
                = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (null == connectivityManager) {
            Logging.error("null ConnectivityManager trying to determine connection info");
            return false;
        }
        final Network n = connectivityManager.getActiveNetwork();
        if (null != n) {
            final NetworkCapabilities cap = connectivityManager.getNetworkCapabilities(n);
            return cap != null && cap.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                    cap.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
        }
        return false;
    }

    /**
     * Generalized call failure handling method
     * @param message the message for the failure
     * @param e an {#link java.io.IOException} if that's the cause of the error.
     * @param completedListener the {@link net.wigle.wigleandroid.net.RequestCompletedListener} instance to call on completion of processing.
     * @param mainHandler an {@link android.os.Handler} instance to which to post any GUI thread consequences
     * @param o a {@link org.json.JSONObject} for any generalized error entity contained in the failure body
     */
    private void onCallFailure(@NotNull final String message, @NotNull IOException e,
                                       @NotNull RequestCompletedListener completedListener,
                                       @NotNull final Handler mainHandler, JSONObject o) {
        Logging.error(message, e);
        completedListener.onTaskFailed(LOCAL_FAILURE_CODE, o);
        mainHandler.post(completedListener::onTaskCompleted);
    }

    private static boolean hasAuthed(final SharedPreferences prefs) {
        final String token = TokenAccess.getApiToken(prefs);
        // get authname second as getApiToken may clear it
        final String authname = prefs.getString(PreferenceKeys.PREF_AUTHNAME, null);
        return (null != authname && !authname.isEmpty() && null != token);
    }


    //TODO: should this be implemented as an interceptor? we'd have to parametereize based on queries...
    private static void cacheResult(final String result, final String outputFileName, final Context context) {
        if (outputFileName == null || result == null || result.isEmpty()) return;

        try (FileOutputStream fos = FileUtility.createFile(context, outputFileName, true)) {
            //DEBUG: Logging.info("writing cache file "+outputFileName);
            FileAccess.writeFos(fos, result);
        } catch (final IOException ex) {
            Logging.error("exception caching result: " + ex, ex);
        }
    }

    private static <T> T readCached(final String cacheFile, Context context, Class<T> classOfT) {
        File file;
        //DEBUG: Logging.info("reading cache for "+cacheFile);
        file = new File(context.getCacheDir(), cacheFile);
        if (!file.exists() || !file.canRead()) {
            Logging.warn("App-internal cache file doesn't exist or can't be read: " + file);
            return null;
        }
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            //DEBUG: Logging.info("read cache for "+cacheFile);
            return new Gson().fromJson(br, classOfT);
        } catch (final Exception ex) {
            Logging.error("Exception reading cache file: " + ex, ex);
        }
        return null;
    }

    //As seen in AbstractBackgroundTask - but accessing the MainLooper directly.
    private void sendBundledMessage(final int what, final Bundle bundle) {
        final Handler mainHandler = new Handler(Looper.getMainLooper());
        final Message msg = new Message();
        msg.what = what;
        msg.setData(bundle);
        mainHandler.sendMessage(msg);
    }
}
