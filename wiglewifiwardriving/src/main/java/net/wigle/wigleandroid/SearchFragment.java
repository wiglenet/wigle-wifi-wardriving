package net.wigle.wigleandroid;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.location.Location;
import android.media.AudioManager;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.RelativeLayout;
import android.widget.Spinner;
import android.widget.TextView;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.MapView;
import com.google.android.gms.maps.MapsInitializer;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;

import net.wigle.wigleandroid.model.NetworkFilterType;
import net.wigle.wigleandroid.ui.NetworkTypeArrayAdapter;
import net.wigle.wigleandroid.ui.ThemeUtil;
import net.wigle.wigleandroid.ui.WiFiSecurityTypeArrayAdapter;
import net.wigle.wigleandroid.ui.WiGLEToast;
import net.wigle.wigleandroid.util.Logging;
import net.wigle.wigleandroid.util.PreferenceKeys;
import net.wigle.wigleandroid.util.SearchUtil;

import java.util.concurrent.atomic.AtomicBoolean;

public class SearchFragment extends Fragment {

    private static final int DEFAULT_ZOOM = 15;
    private AtomicBoolean finishing;
    private MapView mapView;
    private MapRender mapRender;

    private boolean mLocalSearch;

    /** Called when the activity is first created. */
    @Override
    public void onCreate( final Bundle savedInstanceState ) {
        Logging.info("SEARCH: onCreate");
        super.onCreate(savedInstanceState);
        //setHasOptionsMenu(true);
        // set language
        final Activity a = getActivity();
        if (null != a) {
            MainActivity.setLocale(a);
            // media volume
            a.setVolumeControlStream(AudioManager.STREAM_MUSIC);
        }
        finishing = new AtomicBoolean(false);
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
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        final int orientation = getResources().getConfiguration().orientation;
        Logging.info("SEARCH: onCreateView. orientation: " + orientation);
        final View view = inflater.inflate(R.layout.search_nets, container, false);

        if (ListFragment.lameStatic.queryArgs != null) {
            for (final int id : new int[]{R.id.query_address, R.id.query_ssid, R.id.query_bssid}) {
                TextView tv = view.findViewById(id);
                if (id == R.id.query_address && ListFragment.lameStatic.queryArgs.getAddress() != null) {
                    tv.setText(ListFragment.lameStatic.queryArgs.getAddress().toString());
                }
                if (id == R.id.query_ssid && ListFragment.lameStatic.queryArgs.getSSID() != null) {
                    tv.setText(ListFragment.lameStatic.queryArgs.getSSID());
                }
                if (id == R.id.query_bssid && ListFragment.lameStatic.queryArgs.getBSSID() != null) {
                    tv.setText(ListFragment.lameStatic.queryArgs.getBSSID());
                }
            }
        }
        final Spinner networkTypeSpinner = view.findViewById(R.id.type_spinner);
        final Spinner wifiEncryptionSpinner = view.findViewById(R.id.encryption_spinner);

        SearchNetworkTypeArrayAdapter adapter = new SearchNetworkTypeArrayAdapter(getContext());
        networkTypeSpinner.setAdapter(adapter);
        networkTypeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position == 0 || position == 1) {
                    if (null != wifiEncryptionSpinner) {
                        wifiEncryptionSpinner.setClickable(true);
                        wifiEncryptionSpinner.setEnabled(true);
                    } else {
                        Logging.error("Unable to disable the security type spinner");
                    }
                } else {
                    if (null != wifiEncryptionSpinner) {
                        wifiEncryptionSpinner.setSelection(0);
                        wifiEncryptionSpinner.setClickable(false);
                        wifiEncryptionSpinner.setEnabled(false);
                    } else {
                        Logging.error("Unable to disable the security type spinner");
                    }
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                if (null != wifiEncryptionSpinner) {
                    wifiEncryptionSpinner.setClickable(true);
                    wifiEncryptionSpinner.setEnabled(true);
                } else {
                    Logging.error("Unable to disable the security type spinner");
                }
            }
        });

        if (null != ListFragment.lameStatic.queryArgs && ListFragment.lameStatic.queryArgs.getType() != null) {
            networkTypeSpinner.setSelection(ListFragment.lameStatic.queryArgs.getType().ordinal());
        }
        WiFiSecurityTypeArrayAdapter securityAdapter = new WiFiSecurityTypeArrayAdapter(getContext());
        wifiEncryptionSpinner.setAdapter(securityAdapter);
        if (null != ListFragment.lameStatic.queryArgs &&
                ListFragment.lameStatic.queryArgs.getCrypto() != null) {
            if (ListFragment.lameStatic.queryArgs.getType() != null &&
                    (NetworkFilterType.ALL.equals(ListFragment.lameStatic.queryArgs.getType()) ||
                            NetworkFilterType.WIFI.equals(ListFragment.lameStatic.queryArgs.getType()))) {
                wifiEncryptionSpinner.setSelection(ListFragment.lameStatic.queryArgs.getCrypto().ordinal());
            }
        }

        final Activity a = getActivity();
        final SharedPreferences prefs = (null != a)?a.getApplicationContext().
                getSharedPreferences(PreferenceKeys.SHARED_PREFS, 0):null;
        RadioButton rb = view.findViewById(R.id.radio_search_local);
        rb.setOnCheckedChangeListener((buttonView, isChecked) -> {
            //ALIBI: this is unpleasantly complex, but we can't do "ALL" searches against the server
            mLocalSearch = isChecked;
            if (!isChecked && networkTypeSpinner.getSelectedItemPosition() == 0) {
                networkTypeSpinner.setSelection(1);
            }
        });
        if ((null == prefs || prefs.getString(PreferenceKeys.PREF_AUTHNAME, "").isEmpty()) || !TokenAccess.hasApiToken(prefs)) {
            rb.setChecked(true);
            mLocalSearch = true;

            rb = view.findViewById(R.id.radio_search_wigle);
            if (null != rb) {
                rb.setText(getText(R.string.search_wigle) + " " + getText(R.string.must_login));
                rb.setEnabled(false);
            } else {
                Logging.info("unable to get RB");
            }
        } else {
            if ((ListFragment.lameStatic.queryArgs != null) && (ListFragment.lameStatic.queryArgs.searchWiGLE())) {
                rb = view.findViewById(R.id.radio_search_wigle);
                rb.setChecked(true);
                mLocalSearch = false;
            } else {
                rb = view.findViewById(R.id.radio_search_local);
                rb.setChecked(true);
                mLocalSearch = true;
            }
        }


        setupQueryButtons( view );
        final Location location = ListFragment.lameStatic.location;
        LatLng centerPoint = (null == location)?new LatLng(0.0,0.0):new LatLng(location.getLatitude(), location.getLongitude()); //TODO: choose a good default
        setupMap(this.getActivity().getApplicationContext(), view, centerPoint, savedInstanceState, prefs );
        return view;
    }


    private void setupQueryButtons( final View view ) {
        Button button = view.findViewById( R.id.search_button );
        button.setOnClickListener(buttonView -> {

            RadioGroup rbg = view.findViewById(R.id.search_type_group);
            int searchTypeId = rbg.getCheckedRadioButtonId();
            final boolean local = searchTypeId != R.id.radio_search_wigle;

            final String fail = SearchUtil.setupQuery(view, getActivity(), local);

            if (fail != null) {
                WiGLEToast.showOverFragment(getActivity(), R.string.error_general, fail);
            } else {
                ListFragment.lameStatic.queryArgs.setSearchWiGLE(!local);
                final Intent settingsIntent = new Intent(getActivity(), DBResultActivity.class);
                startActivity(settingsIntent);
            }

        });

        button = view.findViewById( R.id.reset_button );
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(final View buttonView) {
                SearchUtil.clearWiFiBtFields(view);
            }
        });
    }

    private void setupMap(final Context context, final View parentView, final LatLng center, final Bundle savedInstanceState, final SharedPreferences prefs ) {
        mapView = new MapView( context );
        try {
            mapView.onCreate(savedInstanceState);
            mapView.getMapAsync(googleMap -> ThemeUtil.setMapTheme(googleMap, mapView.getContext(), prefs, R.raw.night_style_json));
            MapsInitializer.initialize(context);
            if ((center != null)) {
                mapView.getMapAsync(googleMap -> {
                    mapRender = new MapRender(context, googleMap, true);
                    final CameraPosition cameraPosition = new CameraPosition.Builder()
                            .target(center).zoom(DEFAULT_ZOOM).build();
                    googleMap.moveCamera(CameraUpdateFactory.newCameraPosition(cameraPosition));
                    googleMap.setOnCameraMoveListener(() -> {
                        LatLngBounds curScreen = googleMap.getProjection()
                                .getVisibleRegion().latLngBounds;
                        TextView v = parentView.findViewById(R.id.search_lats);
                        if (null != v) {
                            v.setText(String.format("%.5f : %.5f",curScreen.northeast.latitude , curScreen.southwest.latitude));
                        }
                        v = parentView.findViewById(R.id.search_lons);
                        if (null != v) {
                            v.setText(String.format("%.5f : %.5f",curScreen.northeast.longitude, curScreen.southwest.longitude));
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

    private class SearchNetworkTypeArrayAdapter extends NetworkTypeArrayAdapter {

        public SearchNetworkTypeArrayAdapter(final Context context) {
            super(context);
        }

        @Override
        public boolean isEnabled(int position){
            return position != 0 || mLocalSearch;
        }
    }
}
