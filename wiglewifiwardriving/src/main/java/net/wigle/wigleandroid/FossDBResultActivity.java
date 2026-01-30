package net.wigle.wigleandroid;


import static net.wigle.wigleandroid.util.PreferenceKeys.PREF_FOSS_MAPS_VECTOR_TILE_KEY;
import static net.wigle.wigleandroid.util.PreferenceKeys.PREF_FOSS_MAPS_VECTOR_TILE_STYLE;

import android.content.SharedPreferences;
import android.os.Bundle;
import androidx.annotation.NonNull;

import android.os.Handler;
import android.widget.RelativeLayout;

import net.wigle.wigleandroid.model.Network;
import net.wigle.wigleandroid.model.api.BtSearchResponse;
import net.wigle.wigleandroid.model.api.CellSearchResponse;
import net.wigle.wigleandroid.model.api.WiFiSearchResponse;
import net.wigle.wigleandroid.util.Logging;

import org.maplibre.android.annotations.MarkerOptions;
import org.maplibre.android.camera.CameraPosition;
import org.maplibre.android.camera.CameraUpdateFactory;
import org.maplibre.android.geometry.LatLng;
import org.maplibre.android.geometry.LatLngBounds;
import org.maplibre.android.maps.MapView;

/**
 * DB/Network query result activity for FOSS Maps
 */
public class FossDBResultActivity extends AbstractDBResultActivity {

    private MapView mapView;

    private FossMapRender mapRender;

    @Override
    protected int getLayoutResourceId() {
        return R.layout.foss_dbresult;
    }

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    protected void setupMap(final net.wigle.wigleandroid.model.LatLng center, final Bundle savedInstanceState, final SharedPreferences prefs) {
        mapView = new MapView( this );
        mapView.onCreate(savedInstanceState);

        mapView.getMapAsync(mapLibreMap -> {
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
            mapLibreMap.setStyle(styleUrl);
            if (center != null) {
                final CameraPosition cameraPosition = new CameraPosition.Builder()
                        .target(new LatLng(center.latitude, center.longitude))
                        .zoom(DEFAULT_ZOOM).build();
                mapLibreMap.moveCamera(CameraUpdateFactory.newCameraPosition(cameraPosition));
            }
        });
        final RelativeLayout rlView = findViewById( R.id.db_map_rl );
        rlView.addView( mapView );
    }

    @Override
    public void updateMap(boolean hasValidPoints, Handler handler) {
        LatLngBounds.Builder builder = new LatLngBounds.Builder();
        if (listAdapter != null) {
            for (Network n : resultList) {
                listAdapter.add(n);
                final net.wigle.wigleandroid.model.LatLng pos = n.getLatLng();
                if (null != pos) {
                    final LatLng ll = new LatLng(pos.latitude, pos.longitude);
                    //noinspection ConstantConditions
                    if (ll != null) {
                        builder.include(ll);
                        hasValidPoints = true;
                    }
                }
            }
        }
        if (hasValidPoints) {
            mapView.getMapAsync(mapLibre -> {
                for (Network n: resultList) {
                    final net.wigle.wigleandroid.model.LatLng pos = n.getLatLng();
                    if (null != pos) {
                        mapLibre.addMarker(
                                new MarkerOptions()
                                        .position(new LatLng(pos.latitude, pos.longitude))
                                        .title(n.getSsid())
                                        .snippet(n.getType() + " " + n.getBssid())
                        );
                    }
                }
                if (resultList.size() > 1) {
                    mapLibre.moveCamera(CameraUpdateFactory.newLatLngBounds(builder.build(), 0));
                } else if (resultList.size() == 1) {
                    mapLibre.moveCamera(CameraUpdateFactory.newLatLng(
                            new LatLng(
                                    resultList.get(0).getLatLng().latitude,
                                    resultList.get(0).getLatLng().longitude)));
                }
            });
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
                    builder.include(new LatLng(n.getPosition().latitude, n.getPosition().longitude));
//1/3: TODO: not working

                }
            }
        } else if (null != btSearchResponse && null != btSearchResponse.getResults()) {
            for (BtSearchResponse.BtNetwork net : btSearchResponse.getResults()) {
                if (null != net) {
                    final Network n = BtSearchResponse.asNetwork(net);
                    listAdapter.add(n);
                    builder.include(new LatLng(n.getPosition().latitude, n.getPosition().longitude));

//2/3: TODO: not working
                }
            }
        } else if (null != cellSearchResponse && null != cellSearchResponse.getResults()) {
            for (CellSearchResponse.CellNetwork net : cellSearchResponse.getResults()) {
                if (null != net) {
                    final Network n = CellSearchResponse.asNetwork(net);
                    listAdapter.add(n);
                    builder.include(new LatLng(n.getPosition().latitude, n.getPosition().longitude));

//3/3: TODO: not working
                }
            }
        }
        if (!listAdapter.isEmpty()) {
            try {
                mapView.getMapAsync(mapLibre -> mapLibre.moveCamera(CameraUpdateFactory.newLatLngBounds(builder.build(), 0)));
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
