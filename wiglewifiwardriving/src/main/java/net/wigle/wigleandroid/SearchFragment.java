package net.wigle.wigleandroid;

import static android.view.View.GONE;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.location.Address;
import android.location.Geocoder;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.SearchView;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.MapView;
import com.google.android.gms.maps.MapsInitializer;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;

import net.wigle.wigleandroid.model.MapBounds;
import net.wigle.wigleandroid.model.QueryArgs;
import net.wigle.wigleandroid.ui.ThemeUtil;
import net.wigle.wigleandroid.util.Logging;

import java.io.IOException;
import java.util.List;

/**
 * The Search activity fragment - look up networks both locally on via the API on WiGLE.net
 * [delete this file for FOSS build]
 * @author bobzilla, arkasha
 */
public class SearchFragment extends AbstractSearchFragment {
    private MapView mapView;
    private MapRender mapRender;

    @Override
    public void onDestroy() {
        if (mapView != null) {
            mapView.onDestroy();
        }
        super.onDestroy();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (null != mapView) {
            mapView.onResume();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (mapView != null) {
            mapView.onPause();
        }
        if (mapRender != null) {
            // save memory
            mapRender.clear();
        }
    }

    @Override
    public void onSaveInstanceState(@NonNull final Bundle outState) {
        super.onSaveInstanceState(outState);
        if (mapView != null) {
            mapView.onSaveInstanceState(outState);
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        final View view = inflater.inflate(R.layout.search_nets, container, false);
        super.onCreateView(inflater, container, savedInstanceState);
        return super.setupView(view, savedInstanceState);
    }

    @SuppressLint("DefaultLocale")
    @Override
    protected void setupMap(final Context context, final View parentView,
                            final net.wigle.wigleandroid.model.LatLng center,
                            final Bundle savedInstanceState, final SharedPreferences prefs) {
        mapView = new MapView( context );
        try {
            mapView.onCreate(savedInstanceState);
            mapView.getMapAsync(googleMap -> ThemeUtil.setMapTheme(googleMap, mapView.getContext(),
                    prefs, R.raw.night_style_json));
            MapsInitializer.initialize(context);
            if ((center != null)) {
                mapView.getMapAsync(googleMap -> {
                    mapRender = new MapRender(context, googleMap, true);
                    final CameraPosition cameraPosition = new CameraPosition.Builder()
                            .target(new LatLng(center.latitude, center.longitude)).zoom(DEFAULT_ZOOM).build();
                    googleMap.moveCamera(CameraUpdateFactory.newCameraPosition(cameraPosition));
                    googleMap.setOnCameraMoveListener(() -> {
                        if (null == ListFragment.lameStatic.queryArgs) {
                            ListFragment.lameStatic.queryArgs = new QueryArgs();
                        }
                        LatLngBounds curScreen = googleMap.getProjection()
                                .getVisibleRegion().latLngBounds;
                        TextView v = parentView.findViewById(R.id.search_lats);
                        ListFragment.lameStatic.queryArgs.setLocationBounds(new MapBounds(
                                new net.wigle.wigleandroid.model.LatLng(curScreen.southwest.latitude, curScreen.southwest.longitude),
                                new net.wigle.wigleandroid.model.LatLng(curScreen.northeast.latitude, curScreen.northeast.longitude)));
                        if (null != v) {
                            //ALIBI: https://xkcd.com/2170/
                            v.setText(String.format("%.4f : %.4f",curScreen.northeast.latitude , curScreen.southwest.latitude));
                        }
                        v = parentView.findViewById(R.id.search_lons);
                        if (null != v) {
                            v.setText(String.format("%.4f : %.4f",curScreen.northeast.longitude, curScreen.southwest.longitude));
                        }

                    });
                });
            }
        } catch (Exception ex) {
            Logging.error("npe in mapView.onCreate: " + ex, ex);
        }

        final RelativeLayout rlView = parentView.findViewById( R.id.map_search );
        rlView.addView( mapView );
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
                String location = searchView.getQuery().toString();
                if (location != null || location.equals("")) {
                    Geocoder geocoder = new Geocoder(context);
                    try {
                        List<Address> addressList = geocoder.getFromLocationName(location, 1);
                        if (null != addressList && addressList.size() > 0) {
                            Address address = addressList.get(0); // ALIBI: taking the first choice. We could also offer the choices in a drop-down.
                            if (null != address) {
                                LatLng latLng = new LatLng(address.getLatitude(), address.getLongitude());
                                mapView.getMapAsync(googleMap -> {
                                    googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 14));
                                });
                                return true;
                            }
                        }
                    } catch (IOException e) {
                        Logging.error("Geocoding failed: ",e);
                    }
                }
                return false;
            }
            @Override
            public boolean onQueryTextChange(String newText) {
                return false;
            }
        });
    }
}
