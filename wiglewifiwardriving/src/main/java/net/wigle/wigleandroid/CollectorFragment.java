package net.wigle.wigleandroid;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import net.wigle.wigleandroid.WiGLEAuthException;
import net.wigle.wigleandroid.background.ApiListener;
import net.wigle.wigleandroid.background.ShadowCheckUploader;
import net.wigle.wigleandroid.model.Network;
import net.wigle.wigleandroid.util.Logging;
import net.wigle.wigleandroid.util.PreferenceKeys;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class CollectorFragment extends Fragment implements ApiListener {

    private final Handler handler = new Handler();
    private SharedPreferences prefs;
    private TextView wifiCount;
    private TextView btCount;
    private TextView cellCount;
    private TextView statusText;
    private TextView caseDisplay;
    private Button uploadButton;
    private ListView liveList;
    private ArrayAdapter<String> listAdapter;
    private final List<String> networkDisplayList = new ArrayList<>();

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        final View view = inflater.inflate(R.layout.collector, container, false);
        prefs = requireContext().getSharedPreferences(PreferenceKeys.SHARED_PREFS, 0);

        wifiCount = view.findViewById(R.id.sc_wifi_count);
        btCount = view.findViewById(R.id.sc_bt_count);
        cellCount = view.findViewById(R.id.sc_cell_count);
        statusText = view.findViewById(R.id.sc_status);
        caseDisplay = view.findViewById(R.id.sc_case_display);
        uploadButton = view.findViewById(R.id.sc_upload_button);
        liveList = view.findViewById(R.id.sc_live_list);

        listAdapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_list_item_1, networkDisplayList);
        liveList.setAdapter(listAdapter);

        uploadButton.setOnClickListener(v -> {
            MainActivity m = MainActivity.getMainActivity();
            if (m != null) {
                m.setTransferring();
                ShadowCheckUploader uploader = new ShadowCheckUploader(m, MainActivity.getStaticState().dbHelper, this);
                uploader.start();
            }
        });

        setupTimer();
        return view;
    }

    private void setupTimer() {
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                updateUI();
                handler.postDelayed(this, 2000L);
            }
        }, 1000L);
    }

    private void updateUI() {
        if (isAdded()) {
            wifiCount.setText(getString(R.string.sc_nets_wifi, ListFragment.lameStatic.runNets));
            btCount.setText(getString(R.string.sc_nets_bt, ListFragment.lameStatic.runBt));
            cellCount.setText(getString(R.string.sc_nets_cell, ListFragment.lameStatic.runCells));
            
            boolean scanning = MainActivity.isScanning(requireContext());
            statusText.setText(scanning ? R.string.sc_status_active : R.string.sc_status_paused);
            statusText.setTextColor(scanning ? 0xFF22c55e : 0xFFef4444);

            String caseId = prefs.getString(PreferenceKeys.PREF_CASE_ID, "UNSET");
            caseDisplay.setText("Case: " + (caseId.isEmpty() ? "UNSET" : caseId));

            // Update live feed
            updateLiveFeed();
        }
    }

    private void updateLiveFeed() {
        if (ListFragment.lameStatic.networkCache != null) {
            List<Network> networks = new ArrayList<>(ListFragment.lameStatic.networkCache.values());
            // Sort by level/signal or just show last seen? WiGLE cache is ordered.
            Collections.reverse(networks); // Show newest first
            
            networkDisplayList.clear();
            int count = 0;
            for (Network n : networks) {
                if (count >= 10) break;
                String ssid = n.getSsid();
                if (ssid == null || ssid.isEmpty()) ssid = "<Hidden>";
                networkDisplayList.add(n.getType().getCode() + ": " + ssid + " [" + n.getBssid() + "] " + n.getLevel() + "dBm");
                count++;
            }
            listAdapter.notifyDataSetChanged();
        }
    }

    @Override
    public void requestComplete(final JSONObject json, final boolean isCache) throws WiGLEAuthException {
        MainActivity m = MainActivity.getMainActivity();
        if (m != null) {
            m.transferComplete();
        }
    }
}