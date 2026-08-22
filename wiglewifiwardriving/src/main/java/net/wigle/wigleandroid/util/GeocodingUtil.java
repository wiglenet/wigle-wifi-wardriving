package net.wigle.wigleandroid.util;

import android.content.Context;
import android.location.Address;
import android.location.Geocoder;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Asynchronous geocoding utility.
 * @author arkasha
 */
public final class GeocodingUtil {

    /** 
     * Callback format
     */
    public interface GeocodeCallback {
        void onResult(@NonNull List<Address> addresses);
        void onError(@NonNull Exception e);
    }

    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        final Thread t = new Thread(r, "wigle-geocoder");
        t.setDaemon(true);
        return t;
    });

    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());

    private GeocodingUtil() {}

    /**
     * Asynchronously resolve a location name to {@link Address} results.
     * @param context The application context
     * @param locationName The location name to resolve
     * @param maxResults The maximum number of results to return
     * @param callback The callback to invoke with the results
     */
    public static void getFromLocationName(@NonNull final Context context,
                                           @NonNull final String locationName,
                                           final int maxResults,
                                           @NonNull final GeocodeCallback callback) {
        final Geocoder geocoder = new Geocoder(context);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getFromLocationNameTiramisu(geocoder, locationName, maxResults, callback);
            return;
        }
        EXECUTOR.execute(() -> {
            try {
                @SuppressWarnings("deprecation")
                final List<Address> result = geocoder.getFromLocationName(locationName, maxResults);
                final List<Address> safe = (result == null) ? Collections.emptyList() : result;
                MAIN_HANDLER.post(() -> callback.onResult(safe));
            } catch (final Exception e) {
                MAIN_HANDLER.post(() -> callback.onError(e));
            }
        });
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private static void getFromLocationNameTiramisu(@NonNull final Geocoder geocoder,
                                                    @NonNull final String locationName,
                                                    final int maxResults,
                                                    @NonNull final GeocodeCallback callback) {
        geocoder.getFromLocationName(locationName, maxResults, new Geocoder.GeocodeListener() {
            @Override
            public void onGeocode(@NonNull List<Address> addresses) {
                MAIN_HANDLER.post(() -> callback.onResult(addresses));
            }

            @Override
            public void onError(@Nullable String errorMessage) {
                MAIN_HANDLER.post(() -> callback.onError(new IOException(
                        errorMessage != null ? errorMessage : "Geocoder error")));
            }
        });
    }
}
