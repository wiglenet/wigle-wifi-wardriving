package net.wigle.wigleandroid.net;

import android.content.SharedPreferences;

import net.wigle.wigleandroid.TokenAccess;
import net.wigle.wigleandroid.util.PreferenceKeys;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;

import okhttp3.Credentials;
import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;

public class BasicAuthInterceptor implements Interceptor {
    private final String credentials;

    public BasicAuthInterceptor(final SharedPreferences prefs) {
        final String authname = prefs.getString(PreferenceKeys.PREF_AUTHNAME, null);
        final String token = TokenAccess.getApiToken(prefs);
        this.credentials = Credentials.basic(authname, token);
    }

    @Override
    public Response intercept(@NotNull Interceptor.Chain chain) throws IOException {
        Request request = chain.request();
        Request authenticatedRequest = request.newBuilder()
                .header("Authorization", credentials).build();
        return chain.proceed(authenticatedRequest);
    }
}
