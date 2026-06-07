package net.wigle.wigleandroid;

import static android.view.View.GONE;

import static net.wigle.wigleandroid.util.PreferenceKeys.PREF_FOSS_MAPS_VECTOR_TILE_KEY;
import static net.wigle.wigleandroid.util.PreferenceKeys.PREF_FOSS_MAPS_VECTOR_TILE_STYLE;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.location.Address;
import android.location.Geocoder;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.SearchView;

import net.wigle.wigleandroid.model.LatLng;
import net.wigle.wigleandroid.model.MapBounds;
import net.wigle.wigleandroid.model.QueryArgs;
import net.wigle.wigleandroid.util.FossConfigDialogUtil;
import net.wigle.wigleandroid.util.GeocodingUtil;
import net.wigle.wigleandroid.util.Logging;

import org.maplibre.android.MapLibre;
import org.maplibre.android.camera.CameraPosition;
import org.maplibre.android.camera.CameraUpdateFactory;
import org.maplibre.android.geometry.LatLngBounds;
import org.maplibre.android.maps.MapView;

import java.util.List;

public class FossSearchFragment extends AbstractSearchFragment {

    private MapView mapView;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        final Activity a = getActivity();
        if (null != a) {
            // ALIBI: MapLibre must be initialized before R.layout.foss_search_nets (which contains
            // an inline <org.maplibre.android.maps.MapView/>) is inflated; otherwise inflation
            // throws and crashes the app.
            try {
                MapLibre.getInstance(a);
            } catch (Throwable t) {
                Logging.error("Failed to initialize MapLibre for FossSearchFragment: ", t);
            }
        }
        final View view;
        try {
            view = inflater.inflate(R.layout.foss_search_nets, container, false);
        } catch (RuntimeException re) {
            Logging.error("Failed to inflate FOSS search layout: ", re);
            FossConfigDialogUtil.show(a, () -> {
                try {
                    if (isAdded()) {
                        getParentFragmentManager().popBackStack();
                    }
                } catch (Throwable t) {
                    Logging.error("Failed to pop back stack after FOSS inflate failure: ", t);
                }
            });
            return (a != null) ? new View(a) : null;
        }
        super.onCreateView(inflater, container, savedInstanceState);
        return super.setupView(view, savedInstanceState);
    }

    @Override
    public void onDestroy() {
        Logging.info("FOSS Search: onDestroy");
        if (mapView != null) {
            mapView.onDestroy();
        }
        super.onDestroy();
    }

    @Override
    public void onResume() {
        Logging.info("FOSS Search: onResume");
        super.onResume();
        if (mapView != null) {
            mapView.onResume();
        }
    }

    @Override
    public void onPause() {
        Logging.info("FOSS Search: onPause");
        super.onPause();
        if (mapView != null) {
            mapView.onPause();
        }
        /*
        if (mapRender != null) {
            // save memory
            mapRender.clear();
        }
         */
    }

    @Override
    public void onSaveInstanceState(@NonNull final Bundle outState) {
        super.onSaveInstanceState(outState);
        if (mapView != null) {
            mapView.onSaveInstanceState(outState);
        }
    }

    @Override
    protected void setupMap(Context context, View parentView, LatLng center, Bundle savedInstanceState, SharedPreferences prefs) {
        final RelativeLayout rlView = parentView.findViewById(R.id.foss_map_search);
        mapView = rlView.findViewById(R.id.maplibreView);


        if (null != mapView) {
            mapView.getMapAsync(mapLibreMap -> {
                // Use style URL if API key is available, otherwise fall back to demo tiles
                final String mapServerKey = prefs != null ?
                        prefs.getString(PREF_FOSS_MAPS_VECTOR_TILE_KEY, null) : null;
                final String mapServerUrl = prefs != null ?
                        prefs.getString(PREF_FOSS_MAPS_VECTOR_TILE_STYLE, null) : null;

                //TODO: day/night style?
                String styleUrl;
                if (mapServerKey != null && !mapServerKey.isEmpty()) {
                    styleUrl = mapServerUrl + mapServerKey;
                } else {
                    styleUrl = "https://demotiles.maplibre.org/style.json";
                }

                try {
                    mapLibreMap.setStyle(styleUrl);
                    mapLibreMap.setCameraPosition(
                            new CameraPosition.Builder().target(
                                    new org.maplibre.android.geometry.LatLng(0.0, 0.0)).zoom(1.0).build());
                } catch (RuntimeException styleEx) {
                    Logging.error("Failed to apply FOSS search map style '" + styleUrl + "': ", styleEx);
                    FossConfigDialogUtil.show(getActivity(), null);
                }
            });
            if ((center != null)) {
                mapView.getMapAsync(mapLibreMap -> {
                    final CameraPosition cameraPosition = new CameraPosition.Builder()
                            .target(
                                    new org.maplibre.android.geometry.LatLng(
                                            center.latitude, center.longitude))
                            .zoom(DEFAULT_ZOOM).build();
                    mapLibreMap.moveCamera(CameraUpdateFactory.newCameraPosition(cameraPosition));
                    mapLibreMap.addOnCameraMoveListener(() -> {
                        if (null == ListFragment.lameStatic.queryArgs) {
                            ListFragment.lameStatic.queryArgs = new QueryArgs();
                        }
                        LatLngBounds curScreen = mapLibreMap.getProjection()
                                .getVisibleRegion().latLngBounds;
                        TextView v = parentView.findViewById(R.id.search_lats);
                        ListFragment.lameStatic.queryArgs.setLocationBounds(
                                new MapBounds(new LatLng(curScreen.latitudeSouth, curScreen.longitudeWest),
                                    new LatLng(curScreen.latitudeNorth, curScreen.longitudeEast)
                                )
                        );
                        if (null != v) {
                            //ALIBI: https://xkcd.com/2170/
                            v.setText(String.format("%.4f : %.4f",curScreen.latitudeNorth,
                                    curScreen.latitudeSouth));
                        }
                        v = parentView.findViewById(R.id.search_lons);
                        if (null != v) {
                            v.setText(String.format("%.4f : %.4f",curScreen.longitudeEast, curScreen.longitudeWest));
                        }
                    });
                });
            }
        } else {
            Logging.error("Failed to find mapView");
        }
    }

    @Override
    protected void setupAddressSearch(final Context context, final View view) {
        final SearchView searchView = view.findViewById(R.id.address_search_view);
        if (!Geocoder.isPresent()) {
            //ALIBI: no Geocoder present - don't offer search.
            searchView.setVisibility(GONE);
            return;
        }
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                final String location = searchView.getQuery().toString();
                if (location == null || location.isEmpty()) {
                    return false;
                }
                GeocodingUtil.getFromLocationName(context, location, 1, new GeocodingUtil.GeocodeCallback() {
                    @Override
                    public void onResult(@NonNull List<Address> addressList) {
                        if (addressList.isEmpty() || mapView == null) {
                            return;
                        }
                        Address address = addressList.get(0); // ALIBI: taking the first choice. We could also offer the choices in a drop-down.
                        if (null == address) {
                            return;
                        }
                        final LatLng latLng = new LatLng(address.getLatitude(), address.getLongitude());
                        mapView.getMapAsync(mapLibreMap -> {
                            final CameraPosition cameraPosition = new CameraPosition.Builder()
                                    .target(new org.maplibre.android.geometry.LatLng(
                                            latLng.latitude, latLng.longitude))
                                    .zoom(DEFAULT_ZOOM).build();
                            mapLibreMap.moveCamera(CameraUpdateFactory.newCameraPosition(cameraPosition));
                        });
                    }

                    @Override
                    public void onError(@NonNull Exception e) {
                        Logging.error("Geocoding failed: ", e);
                    }
                });
                return true;
            }
            @Override
            public boolean onQueryTextChange(String newText) {
                return false;
            }
        });
    }
}
