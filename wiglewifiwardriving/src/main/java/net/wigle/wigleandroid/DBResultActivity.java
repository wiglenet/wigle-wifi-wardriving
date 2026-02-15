package net.wigle.wigleandroid;


import android.content.SharedPreferences;
import android.os.Bundle;
import androidx.annotation.NonNull;

import android.os.Handler;
import android.widget.RelativeLayout;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.MapView;
import com.google.android.gms.maps.MapsInitializer;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;

import net.wigle.wigleandroid.model.Network;
import net.wigle.wigleandroid.model.api.BtSearchResponse;
import net.wigle.wigleandroid.model.api.CellSearchResponse;
import net.wigle.wigleandroid.model.api.WiFiSearchResponse;
import net.wigle.wigleandroid.ui.ThemeUtil;
import net.wigle.wigleandroid.util.Logging;

/**
 * Display search results - Google Maps-specific implementation
 * [delete this file for FOSS build]
 */
public class DBResultActivity extends AbstractDBResultActivity {

    private MapView mapView;
    private MapRender mapRender;

    @Override
    protected int getLayoutResourceId() {
        return R.layout.dbresult;
    }

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    protected void setupMap(final net.wigle.wigleandroid.model.LatLng center, final Bundle savedInstanceState, final SharedPreferences prefs) {
        mapView = new MapView( this );
        mapView.onCreate(savedInstanceState);
        mapView.getMapAsync(googleMap -> ThemeUtil.setMapTheme(googleMap, mapView.getContext(), prefs, R.raw.night_style_json));
        MapsInitializer.initialize(this);

        mapView.getMapAsync(googleMap -> {
            mapRender = new MapRender(DBResultActivity.this, googleMap, true);

            if (center != null) {
                final CameraPosition cameraPosition = new CameraPosition.Builder()
                        .target(new LatLng(center.latitude, center.longitude))
                        .zoom(DEFAULT_ZOOM).build();
                googleMap.moveCamera(CameraUpdateFactory.newCameraPosition(cameraPosition));
            }
        });
        final RelativeLayout rlView = findViewById( R.id.db_map_rl );
        rlView.addView( mapView );
    }

    @Override
    public void updateMap(boolean hasValidPoints, Handler handler) {
        LatLngBounds.Builder builder = new LatLngBounds.Builder();
        if (null != mapRender && listAdapter != null) {
            for (Network n : resultList) {
                listAdapter.add(n);
                mapRender.addItem(n);
                final LatLng ll = n.getPosition();
                //noinspection ConstantConditions
                if (ll != null) {
                    builder.include(ll);
                    hasValidPoints = true;
                }
            }
        }
        if (hasValidPoints) {
            mapView.getMapAsync(googleMap -> googleMap.moveCamera(CameraUpdateFactory.newLatLngBounds(builder.build(), 0)));
        } else {
            handler.post(this::handleEmptyResult);
        }
    }

    @Override
    protected void handleResults() {
        if (listAdapter == null) {
            Logging.error("listAdapter is null in handleResults");
            return;
        }
        stopAnimation();
        listAdapter.clear();
        LatLngBounds.Builder builder = new LatLngBounds.Builder();

        if (null != searchResponse && null != searchResponse.getResults()) {
            for (WiFiSearchResponse.WiFiNetwork net : searchResponse.getResults()) {
                if (null != net) {
                    final Network n = WiFiSearchResponse.asNetwork(net);
                    listAdapter.add(n);
                    builder.include(n.getPosition());

                    if (n.getLatLng() != null && mapRender != null) {
                        mapRender.addItem(n);
                    }
                }
            }
        } else if (null != btSearchResponse && null != btSearchResponse.getResults()) {
            for (BtSearchResponse.BtNetwork net : btSearchResponse.getResults()) {
                if (null != net) {
                    final Network n = BtSearchResponse.asNetwork(net);
                    listAdapter.add(n);
                    builder.include(n.getPosition());

                    if (n.getLatLng() != null && mapRender != null) {
                        mapRender.addItem(n);
                    }
                }
            }
        } else if (null != cellSearchResponse && null != cellSearchResponse.getResults()) {
            for (CellSearchResponse.CellNetwork net : cellSearchResponse.getResults()) {
                if (null != net) {
                    final Network n = CellSearchResponse.asNetwork(net);
                    listAdapter.add(n);
                    builder.include(n.getPosition());

                    if (n.getLatLng() != null && mapRender != null) {
                        mapRender.addItem(n);
                    }
                }
            }
        }
        if (!listAdapter.isEmpty()) {
            try {
                mapView.getMapAsync(googleMap -> googleMap.moveCamera(CameraUpdateFactory.newLatLngBounds(builder.build(), 0)));
            } catch (IllegalStateException ise) {
                Logging.error("Illegal state exception on map move: ", ise);
            }
        }
        resultList.clear();
    }

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
    public void onLowMemory() {
        super.onLowMemory();
        if (mapView != null) {
            mapView.onLowMemory();
        }
    }

}
