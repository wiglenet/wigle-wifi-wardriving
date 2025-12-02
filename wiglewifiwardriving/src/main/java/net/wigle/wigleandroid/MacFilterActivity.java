package net.wigle.wigleandroid;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ListView;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.graphics.Insets;
import androidx.core.view.OnApplyWindowInsetsListener;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.gson.Gson;

import net.wigle.wigleandroid.ui.ScreenChildActivity;
import net.wigle.wigleandroid.util.Logging;
import net.wigle.wigleandroid.util.PreferenceKeys;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import br.com.sapereaude.maskedEditText.MaskedEditText;

/**
 * General purpose MAC address and OUI filter management
 * Created by rksh on 8/31/17.
 */

public class MacFilterActivity extends ScreenChildActivity {
    private String filterKey;
    ArrayList<String> listItems=new ArrayList<>();
    AddressFilterAdapter filtersAdapter;
    public static final String SCAN_MAC_FILTER_MESSAGE = "net.wigle.wigleandroid.filter.SCAN_MAC_OR_OUI";
    public static final String SCAN_MAC_OUI_LIST = "net.wigle.wigleandroid.filter.MAC_OUI_LIST";
    private ActivityResultLauncher<Intent> startOcrActivity;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        final SharedPreferences prefs = this.getSharedPreferences(PreferenceKeys.SHARED_PREFS, 0);
        Intent intent = getIntent();
        String filterType = intent.getStringExtra(FilterActivity.ADDR_FILTER_MESSAGE);
        if (FilterActivity.INTENT_BLE_MFGR_ID_ALERT.equals(filterType)) {
            setContentView(R.layout.ble_mfgr_filter_settings);
        } else {
            setContentView(R.layout.addressfiltersettings);
        }
        EdgeToEdge.enable(this);
        View addressFilterWrapper = findViewById(R.id.address_filter_wrapper);
        if (null != addressFilterWrapper) {
            ViewCompat.setOnApplyWindowInsetsListener(addressFilterWrapper, new OnApplyWindowInsetsListener() {
                        @Override
                        public @org.jspecify.annotations.NonNull WindowInsetsCompat onApplyWindowInsets(@org.jspecify.annotations.NonNull View v, @org.jspecify.annotations.NonNull WindowInsetsCompat insets) {
                            final Insets innerPadding = insets.getInsets(
                                    WindowInsetsCompat.Type.statusBars() |
                                            WindowInsetsCompat.Type.displayCutout());
                            v.setPadding(
                                    innerPadding.left, innerPadding.top, innerPadding.right, innerPadding.bottom
                            );
                            return insets;
                        }
                    }
            );
        }

        View bottomToolsLayout = findViewById(R.id.address_filter_settings_ok);
        if (null != bottomToolsLayout) {
            ViewCompat.setOnApplyWindowInsetsListener(bottomToolsLayout, new OnApplyWindowInsetsListener() {
                @Override
                public @org.jspecify.annotations.NonNull WindowInsetsCompat onApplyWindowInsets(@org.jspecify.annotations.NonNull View v, @org.jspecify.annotations.NonNull WindowInsetsCompat insets) {
                    final Insets innerPadding = insets.getInsets(
                            WindowInsetsCompat.Type.navigationBars() /*TODO:  | cutouts?*/);
                    v.setPadding(
                            innerPadding.left, innerPadding.top, innerPadding.right, innerPadding.bottom
                    );
                    return insets;
                }
            });
        }
        filterKey = "";
        Logging.info(filterType);
        if (FilterActivity.INTENT_DISPLAY_FILTER.equals(filterType)) {
            filterKey = PreferenceKeys.PREF_EXCLUDE_DISPLAY_ADDRS;
        } else if (FilterActivity.INTENT_LOG_FILTER.equals(filterType)) {
            filterKey = PreferenceKeys.PREF_EXCLUDE_LOG_ADDRS;
        } else if (FilterActivity.INTENT_ALERT_FILTER.equals(filterType)) {
            filterKey = PreferenceKeys.PREF_ALERT_ADDRS;
        } else if (FilterActivity.INTENT_BLE_MFGR_ID_ALERT.equals(filterType)) {
            filterKey = PreferenceKeys.PREF_ALERT_BLE_MFGR_IDS;
        } else {
            filterKey = PreferenceKeys.PREF_EXCLUDE_DISPLAY_ADDRS;
        }

        this.startOcrActivity = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> refreshFilterElements()
        );

        if (!FilterActivity.INTENT_BLE_MFGR_ID_ALERT.equals(filterType)) {
            ImageButton scanText = findViewById(R.id.scanAddress);
            if (null != scanText) {
                scanText.setOnClickListener(view -> {
                    final Intent scanAddressIntent = new Intent(getApplicationContext(), MacFinderActivity.class);
                    scanAddressIntent.putExtra(SCAN_MAC_FILTER_MESSAGE, filterType);
                    scanAddressIntent.putStringArrayListExtra(SCAN_MAC_OUI_LIST, listItems);
                    this.startOcrActivity.launch(scanAddressIntent);
                });
            }
        }
        //TODO: "addr_filter_list_view" maybe not a descriptive name anymore
        ListView lv = findViewById(R.id.addr_filter_list_view);
        Gson gson = new Gson();
        String[] values = gson.fromJson(prefs.getString(filterKey, "[]"), String[].class);
        if (values.length > 0) {
            //ALIBI: the java.util.Arrays.ArrayList version is immutable, so a new ArrayList must be made from it.
            listItems = new ArrayList<>(Arrays.asList(values));
        }
        filtersAdapter = new AddressFilterAdapter(listItems, lv.getId(), this, prefs, filterKey);
        lv.setAdapter(filtersAdapter);
        Button doneButton = findViewById(R.id.finish_address_filter);
        if (doneButton != null) {
            doneButton.setOnClickListener(v -> finish());
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshFilterElements();
    }

    private void refreshFilterElements() {
        final SharedPreferences prefs = this.getSharedPreferences(PreferenceKeys.SHARED_PREFS, 0);
        Gson gson = new Gson();
        String[] values = gson.fromJson(prefs.getString(filterKey, "[]"), String[].class);
        if (values.length > 0) {
            //ALIBI: the java.util.Arrays.ArrayList version is immutable, so a new ArrayList must be made from it.
            listItems = new ArrayList<>(Arrays.asList(values));
            filtersAdapter.clear();
            filtersAdapter.addAll(listItems);
            filtersAdapter.notifyDataSetChanged();
        }
    }

    public void addOui(View v) {
        final MaskedEditText ouiInput = findViewById(R.id.oui_input);
        final String input = ouiInput.getRawText();
        if (input.length() == 6) {
            final SharedPreferences prefs = this.getSharedPreferences(PreferenceKeys.SHARED_PREFS, 0);
            if (addFilterElement(input, true)) {
                ouiInput.setText("");
            }
        }
    }

    public void addMac(View v) {
        final MaskedEditText macInput = findViewById(R.id.mac_address_input);
        final String input = macInput.getRawText();
        if (null != input &&  (input.length() == 12)) {
            if (addFilterElement(input, true)) {
                macInput.setText("");
            }
        }
    }

    public void addBleMfgrId(View v) {
        final MaskedEditText mfgrIdInput = findViewById(R.id.ble_mfgr_input);
        final String input = mfgrIdInput.getRawText();
        if (null != input &&  (input.length() == 4)) {
            if (addFilterElement(input, false)) {
                mfgrIdInput.setText("");
            }
        }
    }

    public boolean addFilterElement(final String rawText, final boolean colonDelimited) {
        final SharedPreferences prefs = this.getSharedPreferences(PreferenceKeys.SHARED_PREFS, 0);
        Gson gson = new Gson();
        //ALIBI: we need to get the current list before adding, since delete doesn't call us back to update.
        String[] values = gson.fromJson(prefs.getString(filterKey, "[]"), String[].class);
        if (values.length > 0) {
            listItems = new ArrayList<>(Arrays.asList(values));
        }
        if (addEntry(listItems, prefs, rawText, filterKey, colonDelimited))  {
            filtersAdapter.notifyDataSetChanged();
            refreshFilterElements();
            return true;
        }
        return false;
    }

    /**
     * update a JSON Mac+Oui filter list in preferences by key
     * TODO: should this be moved to a util class?
     * @param entries list of entries currently in filterKey
     * @param prefs a preferences instance to edit
     * @param rawEntry the raw text of the entry
     * @param filterKey the preferences key
     * @param colonDelimited is the stored value colon-delimited
     * @return true if we've successfully updated preferences, false on existing or fail
     */
    public static boolean addEntry(List<String> entries, SharedPreferences prefs,
                                   final String rawEntry, final String filterKey, final boolean colonDelimited) {
        StringBuilder formatted = new StringBuilder();
        if (colonDelimited) {
            for (int i = 0; i < rawEntry.length(); i++) {
                if ((i % 2 == 0) && ((i + 1) != rawEntry.length()) && (i != 0))
                    formatted.append(":");
                formatted.append(Character.toUpperCase(rawEntry.charAt(i)));
            }
        } else {
            formatted = new StringBuilder(rawEntry.toUpperCase(Locale.ROOT));
        }

        if (!entries.contains(formatted.toString())) {
            Logging.info("Adding: " + formatted);
            entries.add(formatted.toString());

            Gson gson = new Gson();
            String serialized = gson.toJson(entries.toArray());
            Logging.info(serialized);
            final SharedPreferences.Editor editor = prefs.edit();
            editor.putString(filterKey,serialized);
            editor.apply();
            MainActivity m = MainActivity.getMainActivity();
            if (null != m) {
                m.updateAddressFilter(filterKey);
            }
            return true;
        }
        return false;
    }

    public static void updateEntries(List<String> entries, SharedPreferences prefs, final String filterKey) {
        Gson gson = new Gson();
        String serialized = gson.toJson(entries.toArray());
        final SharedPreferences.Editor editor = prefs.edit();
        editor.putString(filterKey,serialized);
        editor.apply();
        MainActivity m = MainActivity.getMainActivity();
        if (null != m) {
            m.updateAddressFilter(filterKey);
        }
    }
}