package net.wigle.wigleandroid;

import static android.view.View.GONE;

import android.content.SharedPreferences;
import android.os.Bundle;

import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;

import net.wigle.wigleandroid.ui.PrefsBackedCheckbox;
import net.wigle.wigleandroid.ui.ScreenChildActivity;
import net.wigle.wigleandroid.util.Logging;
import net.wigle.wigleandroid.util.PreferenceKeys;
import net.wigle.wigleandroid.util.SettingsUtil;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/**
 * Building a filter activity for the network list
 * Created by arkasha on 8/1/17.
 */

public class MapFilterActivity extends ScreenChildActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        final SharedPreferences prefs = this.getSharedPreferences(PreferenceKeys.SHARED_PREFS, 0);
        final SharedPreferences.Editor editor = prefs.edit();
        setContentView(R.layout.mapfilter);

        //ALIBI: the map view tools reuses the filter options, which includes alert-on.
        Button alerts = findViewById(R.id.alert_filter_button);
        if (alerts != null) {
            alerts.setVisibility(GONE);
        }

        final androidx.appcompat.app.ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }
        View view = findViewById(android.R.id.content);
        Logging.info("Filter Fragment Selected");
        final EditText regex = findViewById( R.id.edit_regex );
        final String regexKey = MappingFragment.MAP_DIALOG_PREFIX + PreferenceKeys.PREF_MAPF_REGEX;
        regex.setText( prefs.getString(regexKey, "") );

        regex.addTextChangedListener( new SettingsFragment.SetWatcher() {
            @Override
            public void onTextChanged( final String s ) {
                //DEBUG: MainActivity.info("regex update: "+s);
                String currentValue = prefs.getString(regexKey, "");
                if (currentValue.equals(s.trim())) {
                    return;
                }
                if (s.trim().isEmpty()) {
                    //ALIBI: empty values should unset
                    editor.remove(regexKey);
                } else {
                    editor.putString(regexKey, s.trim());
                }
                editor.apply();
            }
        });

        //TODO: DRY up with SettingsFragment
        final String authUser = prefs.getString(PreferenceKeys.PREF_AUTHNAME,"");
        final String authToken = prefs.getString(PreferenceKeys.PREF_TOKEN, "");
        final boolean isAnonymous = prefs.getBoolean( PreferenceKeys.PREF_BE_ANONYMOUS, false);


        final String showDiscovered = prefs.getString( PreferenceKeys.PREF_SHOW_DISCOVERED, PreferenceKeys.PREF_MAP_NO_TILE);
        final boolean isAuthenticated = (!authUser.isEmpty() && !authToken.isEmpty() && !isAnonymous);
        final String[] mapModes = SettingsUtil.getMapModes(isAuthenticated);
        final String[] mapModeName = SettingsUtil.getMapModeNames(isAuthenticated, MapFilterActivity.this);

        if (!PreferenceKeys.PREF_MAP_NO_TILE.equals(showDiscovered)) {
            LinearLayout mainLayout = view.findViewById(R.id.show_map_discovered_since);
            mainLayout.setVisibility(View.VISIBLE);
        }

        SettingsUtil.doMapSpinner( R.id.show_discovered, PreferenceKeys.PREF_SHOW_DISCOVERED,
                PreferenceKeys.PREF_MAP_NO_TILE, mapModes, mapModeName, MapFilterActivity.this, view );

        int thisYear = Calendar.getInstance().get(Calendar.YEAR);
        List<Long> yearValueBase = new ArrayList<>();
        List<String> yearLabelBase = new ArrayList<>();
        for (int i = 2001; i <= thisYear; i++) {
            yearValueBase.add((long)(i));
            yearLabelBase.add(Integer.toString(i));
        }
        SettingsUtil.doSpinner( R.id.networks_discovered_since_year, view, PreferenceKeys.PREF_SHOW_DISCOVERED_SINCE,
                2001L, yearValueBase.toArray(new Long[0]),
                yearLabelBase.toArray(new String[0]), MapFilterActivity.this );

        PrefsBackedCheckbox.prefBackedCheckBox(this , view, R.id.showinvert,
                MappingFragment.MAP_DIALOG_PREFIX + PreferenceKeys.PREF_MAPF_INVERT, false );
        PrefsBackedCheckbox.prefBackedCheckBox( this, view, R.id.showopen,
                MappingFragment.MAP_DIALOG_PREFIX + PreferenceKeys.PREF_MAPF_OPEN, true );
        PrefsBackedCheckbox.prefBackedCheckBox( this, view, R.id.showwep,
                MappingFragment.MAP_DIALOG_PREFIX + PreferenceKeys.PREF_MAPF_WEP, true );
        PrefsBackedCheckbox.prefBackedCheckBox( this, view, R.id.showwpa,
                MappingFragment.MAP_DIALOG_PREFIX + PreferenceKeys.PREF_MAPF_WPA, true );
        PrefsBackedCheckbox.prefBackedCheckBox( this, view, R.id.showcell,
                MappingFragment.MAP_DIALOG_PREFIX + PreferenceKeys.PREF_MAPF_CELL, true );
        PrefsBackedCheckbox.prefBackedCheckBox( this, view, R.id.showbt,
                MappingFragment.MAP_DIALOG_PREFIX + PreferenceKeys.PREF_MAPF_BT, true );
        PrefsBackedCheckbox.prefBackedCheckBox( this, view, R.id.showbtle,
                MappingFragment.MAP_DIALOG_PREFIX + PreferenceKeys.PREF_MAPF_BTLE, true );
        PrefsBackedCheckbox.prefBackedCheckBox( this, view, R.id.enabled,
                MappingFragment.MAP_DIALOG_PREFIX + PreferenceKeys.PREF_MAPF_ENABLED, true );

        final Button finishButton = view.findViewById(R.id.finish_map_filter);
        if (null != finishButton) {
            finishButton.setOnClickListener(v -> {
                finish();
            });
        }

    }
}
