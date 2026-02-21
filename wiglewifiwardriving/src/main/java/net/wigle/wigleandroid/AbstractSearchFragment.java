package net.wigle.wigleandroid;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;

import net.wigle.wigleandroid.model.LatLng;
import net.wigle.wigleandroid.model.MapBounds;
import net.wigle.wigleandroid.model.NetworkFilterType;
import net.wigle.wigleandroid.ui.LayoutUtil;
import net.wigle.wigleandroid.ui.NetworkTypeArrayAdapter;
import net.wigle.wigleandroid.ui.WiFiSecurityTypeArrayAdapter;
import net.wigle.wigleandroid.ui.WiGLEToast;
import net.wigle.wigleandroid.util.Logging;
import net.wigle.wigleandroid.util.PreferenceKeys;
import net.wigle.wigleandroid.util.SearchUtil;

import java.util.concurrent.atomic.AtomicBoolean;

public abstract class AbstractSearchFragment extends Fragment {
    protected static final int DEFAULT_ZOOM = 15;
    protected AtomicBoolean finishing;
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
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        ViewCompat.setOnApplyWindowInsetsListener(view, (v, insets) -> {
            final Insets navBars = insets.getInsets(WindowInsetsCompat.Type.navigationBars());
            v.setPadding(0, 0, 0, navBars.bottom);
            return insets;
        });
        //hack manual padding
        view.post(() -> {
            final Context context = getContext();
            int navBarHeight = context == null ? 0 : LayoutUtil.getNavigationBarHeight(getActivity(), context.getResources());
            if (navBarHeight > 0 && view.getPaddingBottom() == 0) {
                view.setPadding(0, 0, 0, navBarHeight);
            }
            if (view.isAttachedToWindow()) {
                ViewCompat.requestApplyInsets(view);
            }
        });
    }

    public View setupView(final View view, @Nullable Bundle savedInstanceState) {
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
                TextView macHint = view.findViewById(R.id.query_bssid_layout);
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
                    TextView macHint = view.findViewById(R.id.query_bssid_layout);
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
        final SharedPreferences prefs = (null != a) ? a.getApplicationContext().
                getSharedPreferences(PreferenceKeys.SHARED_PREFS, 0) : null;
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
                rb.setText(String.format("%s %s", getText(R.string.search_wigle), getText(R.string.must_login)));
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

        setupQueryButtons(view, prefs);
        LatLng centerPoint = null;
        if ((ListFragment.lameStatic.queryArgs != null) && (ListFragment.lameStatic.queryArgs.getLocationBounds() != null)) {
            final MapBounds bounds = ListFragment.lameStatic.queryArgs.getLocationBounds();
            centerPoint =
                    new LatLng(bounds.getCenter().latitude, bounds.getCenter().longitude);
        } else if (null != ListFragment.lameStatic.location) {
            centerPoint = new LatLng(ListFragment.lameStatic.location.getLatitude(), ListFragment.lameStatic.location.getLongitude());
        } else {
            centerPoint = MappingFragment.DEFAULT_POINT;
        }
        setupMap(this.getActivity().getApplicationContext(), view, centerPoint, savedInstanceState, prefs);
        setupAddressSearch(this.getActivity().getApplicationContext(), view);
        return view;
    }

    private void setupQueryButtons(final View view, final SharedPreferences prefs) {
        Button button = view.findViewById(R.id.perform_search_button);
        button.setOnClickListener(buttonView -> {
            RadioGroup rbg = view.findViewById(R.id.search_type_group);
            int searchTypeId = rbg.getCheckedRadioButtonId();
            final boolean local = searchTypeId != R.id.radio_search_wigle;

            final String fail = SearchUtil.setupQuery(view, getActivity(), local);

            if (fail != null) {
                WiGLEToast.showOverFragment(getActivity(), R.string.error_general, fail);
            } else {
                ListFragment.lameStatic.queryArgs.setSearchWiGLE(!local);
                final Intent settingsIntent = new Intent(getActivity(),
                        prefs.getBoolean(PreferenceKeys.PREF_USE_FOSS_MAPS, false) ?
                                FossDBResultActivity.class : DBResultActivity.class);
                startActivity(settingsIntent);
            }

        });

        button = view.findViewById(R.id.reset_button);
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(final View buttonView) {
                SearchUtil.clearSearchFields(view);
            }
        });
    }

    @SuppressLint("DefaultLocale")
    protected abstract void setupMap(Context context, View parentView, LatLng center,
                                     Bundle savedInstanceState, SharedPreferences prefs);

    protected abstract void setupAddressSearch(Context context, View view);

    /**
     * Extending NetworkTypeArrayAdpater to disable "ALL" for non-WiGLE-searches (WiGLE doesn't offer a universal search, only typed)
     */
    private class SearchNetworkTypeArrayAdapter extends NetworkTypeArrayAdapter {

        public SearchNetworkTypeArrayAdapter(final Context context) {
            super(context);
        }

        @Override
        public boolean isEnabled(int position) {
            return position != 0 || mLocalSearch;
        }
    }
}
