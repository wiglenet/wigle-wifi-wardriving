package net.wigle.wigleandroid;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.ListView;

import com.google.gson.Gson;

import net.wigle.wigleandroid.ui.ScreenChildActivity;
import net.wigle.wigleandroid.util.Logging;
import net.wigle.wigleandroid.util.PreferenceKeys;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import br.com.sapereaude.maskedEditText.MaskedEditText;

/**
 * Created by arkasha on 8/31/17.
 */

public class MacFilterActivity extends ScreenChildActivity {

    private String filterKey;
    List<String> listItems=new ArrayList<>();
    AddressFilterAdapter filtersAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        final SharedPreferences prefs = this.getSharedPreferences(PreferenceKeys.SHARED_PREFS, 0);
        setContentView(R.layout.addressfiltersettings);


        Intent intent = getIntent();
        String filterType = intent.getStringExtra(FilterActivity.ADDR_FILTER_MESSAGE);
        filterKey = "";
        Logging.info(filterType);
        if (FilterActivity.INTENT_DISPLAY_FILTER.equals(filterType)) {
            filterKey = PreferenceKeys.PREF_EXCLUDE_DISPLAY_ADDRS;
        } else if (FilterActivity.INTENT_LOG_FILTER.equals(filterType)) {
            filterKey = PreferenceKeys.PREF_EXCLUDE_LOG_ADDRS;
        } else {
            filterKey = PreferenceKeys.PREF_EXCLUDE_DISPLAY_ADDRS;
        }

        //DEBUG: MainActivity.info(filterKey);
        Gson gson = new Gson();
        String[] values = gson.fromJson(prefs.getString(filterKey, "[]"), String[].class);
        if (values.length > 0) {
            //ALIBI: the java.util.Arrays.ArrayList version is immutable, so a new ArrayList must be made from it.
            listItems = new ArrayList<>(Arrays.asList(values));
        }

        ListView lv = findViewById(R.id.addr_filter_list_view);

        filtersAdapter = new AddressFilterAdapter(listItems, this, prefs, filterKey);

        lv.setAdapter(filtersAdapter);
    }

    public void addOui(View v) {
        final MaskedEditText ouiInput = findViewById(R.id.oui_input);
        final String input = ouiInput.getRawText();
        if (input.length() == 6) {
            final SharedPreferences prefs = this.getSharedPreferences(PreferenceKeys.SHARED_PREFS, 0);
            if (addEntry(listItems, prefs, input, filterKey)) {
                ouiInput.setText("");
                filtersAdapter.notifyDataSetChanged();
            }
        }
    }

    public void addMac(View v) {
        final MaskedEditText macInput = findViewById(R.id.mac_address_input);
        final String input = macInput.getRawText();
        if (null != input &&  (input.length() == 12)) {
            final SharedPreferences prefs = this.getSharedPreferences(PreferenceKeys.SHARED_PREFS, 0);
            if (addEntry(listItems, prefs, input, filterKey))  {
                macInput.setText("");
                filtersAdapter.notifyDataSetChanged();
            }

        }
    }

    /**
     * update a JSON Mac+Oui filter list in preferences by key
     * TODO: should this be moved to a util class?
     * @param entries list of entries currently in filterKey
     * @param prefs a preferences instance to edit
     * @param rawEntry the raw text of the entry
     * @param filterKey the preferences key
     * @return true if we've successfully updated preferences, false on existing or fail
     */
    public static boolean addEntry(List<String> entries, SharedPreferences prefs,
                                   final String rawEntry, final String filterKey) {
        String formatted = "";
        for (int i = 0; i < rawEntry.length(); i++) {
            if ((i % 2 == 0) && ((i+1) != rawEntry.length())  && (i != 0)) formatted += ":";
            formatted += Character.toUpperCase(rawEntry.charAt(i));
        }
        if (!entries.contains(formatted)) {
            Logging.info("Adding: " + formatted);
            entries.add(formatted);

            Gson gson = new Gson();
            String serialized = gson.toJson(entries.toArray());
            Logging.info(serialized);
            final SharedPreferences.Editor editor = prefs.edit();
            editor.putString(filterKey,serialized);
            editor.apply();
            MainActivity m = MainActivity.getMainActivity();
            if (null != m) {
                //TODO: should we also update on Suspend/Dispose?
                m.updateAddressFilter(filterKey);
            }
            return true;
        }
        return false;
    }
}