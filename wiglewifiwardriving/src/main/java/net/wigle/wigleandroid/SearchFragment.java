package net.wigle.wigleandroid;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.location.Address;
import android.location.Geocoder;
import android.media.AudioManager;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.SearchView;
import androidx.core.graphics.Insets;
import androidx.core.view.OnApplyWindowInsetsListener;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
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
import net.wigle.wigleandroid.model.QueryArgs;
import net.wigle.wigleandroid.ui.LayoutUtil;
import net.wigle.wigleandroid.ui.NetworkTypeArrayAdapter;
import net.wigle.wigleandroid.ui.ThemeUtil;
import net.wigle.wigleandroid.ui.WiFiSecurityTypeArrayAdapter;
import net.wigle.wigleandroid.ui.WiGLEToast;
import net.wigle.wigleandroid.util.Logging;
import net.wigle.wigleandroid.util.PreferenceKeys;
import net.wigle.wigleandroid.util.SearchUtil;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The Search activity fragment - look up networks both locally on via the API on WiGLE.net
 * @author bobzilla, arkasha
 */
public class SearchFragment extends Fragment {

    private static final int DEFAULT_ZOOM = 15;
    private AtomicBoolean finishing;
    private MapView mapView;
    private MapRender mapRender;
    private boolean mLocalSearch;

    @Override
    public void onCreate( final Bundle savedInstanceState ) {
        Logging.info("SEARCH: onCreate");
        super.onCreate(savedInstanceState);
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
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        ViewCompat.setOnApplyWindowInsetsListener(view, (v, insets) -> {
            final Insets navBars = insets.getInsets(WindowInsetsCompat.Type.navigationBars());
            v.setPadding(0, 0, 0, navBars.bottom);
            return insets;
        });
        //hack manual padding
        view.post(() -> {
            int navBarHeight = LayoutUtil.getNavigationBarHeight(getActivity(), getResources());
            if (navBarHeight > 0 && view.getPaddingBottom() == 0) {
                view.setPadding(0, 0, 0, navBarHeight);
            }
            if (view.isAttachedToWindow()) {
                ViewCompat.requestApplyInsets(view);
            }
        });
    }
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        final View view = inflater.inflate(R.layout.search_nets, container, false);

        if (ListFragment.lameStatic.queryArgs != null) {
            for (final int id : new int[]{R.id.query_address, R.id.query_ssid, R.id.query_bssid}) {
                TextView tv = view.findViewById(id);
                //ALIBI: excluding address since it's no longer directly used in this view
                if (id == R.id.query_ssid && ListFragment.lameStatic.queryArgs.getSSID() != null) {
                    tv.setText(ListFragment.lameStatic.queryArgs.getSSID());
                }
                if (id == R.id.query_bssid && ListFragment.lameStatic.queryArgs.getBSSID() != null) {
                    tv.setText(ListFragment.lameStatic.queryArgs.getBSSID());
                }
            }
            //ALIBI: not populating the spinners -> also not populating cell, since it can't begin selected.
        }
        final Spinner networkTypeSpinner = view.findViewById(R.id.type_spinner);
        final Spinner wifiEncryptionSpinner = view.findViewById(R.id.encryption_spinner);

        SearchNetworkTypeArrayAdapter adapter = new SearchNetworkTypeArrayAdapter(getContext());
        networkTypeSpinner.setAdapter(adapter);
        networkTypeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View compontentView, int position, long id) {
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
                LinearLayout cell = view.findViewById(R.id.cell_netid_layout);
                TextView macHint =  view.findViewById(R.id.query_bssid_layout);
                EditText maskedMac = view.findViewById(R.id.query_bssid);
                if (position == 3) {
                    cell.setVisibility(VISIBLE);
                    macHint.setVisibility(GONE);
                    maskedMac.setVisibility(GONE);
                    maskedMac.setText("");
                } else {
                    cell.setVisibility(GONE);
                    macHint.setVisibility(VISIBLE);
                    maskedMac.setVisibility(VISIBLE);
                    SearchUtil.clearCellId(view);
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                if (null != wifiEncryptionSpinner) {
                    wifiEncryptionSpinner.setClickable(true);
                    wifiEncryptionSpinner.setEnabled(true);
                    LinearLayout cell = view.findViewById(R.id.cell_netid_layout);
                    TextView macHint =  view.findViewById(R.id.query_bssid_layout);
                    EditText maskedMac = view.findViewById(R.id.query_bssid);
                    cell.setVisibility(GONE);
                    macHint.setVisibility(VISIBLE);
                    maskedMac.setVisibility(VISIBLE);
                    SearchUtil.clearCellId(view);
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
                rb.setText(String.format("%s %s",getText(R.string.search_wigle), getText(R.string.must_login)));
                rb.setEnabled(false);
            } else {
                Logging.info("unable to get RadioButton");
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
        LatLng centerPoint = new LatLng(0.0,0.0);
        if ((ListFragment.lameStatic.queryArgs != null) && (ListFragment.lameStatic.queryArgs.getLocationBounds() != null)) {
            centerPoint = ListFragment.lameStatic.queryArgs.getLocationBounds().getCenter();
        } else if (null != ListFragment.lameStatic.location) {
            centerPoint = new LatLng(ListFragment.lameStatic.location.getLatitude(), ListFragment.lameStatic.location.getLongitude());
        }
        setupMap(this.getActivity().getApplicationContext(), view, centerPoint, savedInstanceState, prefs );
        setupAddressSearch(this.getActivity().getApplicationContext(), view);
        return view;
    }


    private void setupQueryButtons( final View view ) {
        Button button = view.findViewById( R.id.perform_search_button);
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
                SearchUtil.clearSearchFields(view);
            }
        });
    }

    @SuppressLint("DefaultLocale")
    private void setupMap(final Context context, final View parentView, final LatLng center,
                          final Bundle savedInstanceState, final SharedPreferences prefs ) {
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
                            .target(center).zoom(DEFAULT_ZOOM).build();
                    googleMap.moveCamera(CameraUpdateFactory.newCameraPosition(cameraPosition));
                    googleMap.setOnCameraMoveListener(() -> {
                        if (null == ListFragment.lameStatic.queryArgs) {
                            ListFragment.lameStatic.queryArgs = new QueryArgs();
                        }
                        LatLngBounds curScreen = googleMap.getProjection()
                                .getVisibleRegion().latLngBounds;
                        TextView v = parentView.findViewById(R.id.search_lats);
                        ListFragment.lameStatic.queryArgs.setLocationBounds(curScreen);
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

    private void setupAddressSearch(final Context context, final View view) {
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

    /**
     * Extending NetworkTypeArrayAdpater to disable "ALL" for non-WiGLE-searches (WiGLE doesn't offer a universal search, only typed)
     */
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
