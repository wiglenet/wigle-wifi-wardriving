package net.wigle.wigleandroid;

import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.os.Bundle;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;

import net.wigle.wigleandroid.ui.WiGLEToast;
import net.wigle.wigleandroid.util.SearchUtil;

import java.util.concurrent.atomic.AtomicBoolean;

public class SearchFragment extends Fragment {

    private AtomicBoolean finishing;

    /** Called when the activity is first created. */
    @Override
    public void onCreate( final Bundle savedInstanceState ) {
        MainActivity.info("SEARCH: onCreate");
        super.onCreate(savedInstanceState);
        //setHasOptionsMenu(true);
        // set language
        MainActivity.setLocale(getActivity());

        // media volume
        getActivity().setVolumeControlStream(AudioManager.STREAM_MUSIC);

        finishing = new AtomicBoolean(false);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        final int orientation = getResources().getConfiguration().orientation;
        MainActivity.info("SEARCH: onCreateView. orientation: " + orientation);
        final ScrollView scrollView = (ScrollView) inflater.inflate(R.layout.search_nets, container, false);

        final SharedPreferences prefs = getActivity().getApplicationContext().
                getSharedPreferences(ListFragment.SHARED_PREFS, 0);

        if (ListFragment.lameStatic.queryArgs != null) {
            for (final int id : new int[]{R.id.query_address, R.id.query_ssid, R.id.query_bssid}) {
                TextView tv = scrollView.findViewById(id);
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

        if ((prefs.getString(ListFragment.PREF_AUTHNAME,"").isEmpty()) || !TokenAccess.hasApiToken(prefs)) {
            RadioButton rb = scrollView.findViewById(R.id.radio_search_local);
            if (null != rb) {
                rb.setChecked(true);
            }

            rb = scrollView.findViewById(R.id.radio_search_wigle);
            if (null != rb) {
                rb.setText(getText(R.string.search_wigle) + " " + getText(R.string.must_login));
                rb.setEnabled(false);
            } else {
                MainActivity.info("unable to get RB");
            }
        } else {
            if ((ListFragment.lameStatic.queryArgs != null) && (ListFragment.lameStatic.queryArgs.searchWiGLE())) {
                RadioButton rb = scrollView.findViewById(R.id.radio_search_wigle);
                rb.setChecked(true);
            } else {
                RadioButton rb = scrollView.findViewById(R.id.radio_search_local);
                rb.setChecked(true);
            }
        }

        setupQueryButtons( scrollView );
        return scrollView;

    }


    private void setupQueryButtons( final View view ) {
        Button button = (Button) view.findViewById( R.id.search_button );
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(final View buttonView) {

                RadioGroup rbg = view.findViewById(R.id.search_type_group);
                int searchTypeId = rbg.getCheckedRadioButtonId();
                final boolean local = (searchTypeId == R.id.radio_search_wigle) ? false: true;

                final String fail = SearchUtil.setupQuery(view, getActivity(), local);

                final FragmentActivity a = getActivity();
                if (fail != null && null != a && !a.isFinishing()) {
                    WiGLEToast.showOverFragment(a, R.string.error_general, fail);
                } else {
                    ListFragment.lameStatic.queryArgs.setSearchWiGLE(!local);
                    final Intent settingsIntent = new Intent(getActivity(), DBResultActivity.class);
                    startActivity(settingsIntent);
                }

            }
        });

        button = (Button) view.findViewById( R.id.reset_button );
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(final View buttonView) {
                SearchUtil.clearWiFiBtFields(view);
            }
        });

    }

}
