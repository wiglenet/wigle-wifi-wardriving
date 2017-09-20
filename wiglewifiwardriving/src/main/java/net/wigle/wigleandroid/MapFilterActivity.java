package net.wigle.wigleandroid;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.v4.app.NavUtils;
import android.support.v7.app.AppCompatActivity;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

import net.wigle.wigleandroid.util.SettingsUtil;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/**
 * Building a filter activity for the network list
 * Created by arkasha on 8/1/17.
 */

@SuppressWarnings("deprecation")
public class MapFilterActivity extends AppCompatActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        final SharedPreferences prefs = this.getSharedPreferences(ListFragment.SHARED_PREFS, 0);
        final SharedPreferences.Editor editor = prefs.edit();
        setContentView(R.layout.mapfilter);

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.HONEYCOMB) {
            final android.support.v7.app.ActionBar actionBar = getSupportActionBar();
            if (actionBar != null) {
                actionBar.setDisplayHomeAsUpEnabled(true);
            }
        }
        View view = findViewById(android.R.id.content);
        MainActivity.info("Filter Fragment Selected");
        final EditText regex = (EditText) findViewById( R.id.edit_regex );
        final String regexKey = MappingFragment.MAP_DIALOG_PREFIX + ListFragment.PREF_MAPF_REGEX;
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
        final String authUser = prefs.getString(ListFragment.PREF_AUTHNAME,"");
        final String authToken = prefs.getString(ListFragment.PREF_TOKEN, "");
        final boolean isAnonymous = prefs.getBoolean( ListFragment.PREF_BE_ANONYMOUS, false);


        final String showDiscovered = prefs.getString( ListFragment.PREF_SHOW_DISCOVERED, ListFragment.PREF_MAP_NO_TILE);
        final boolean isAuthenticated = (!authUser.isEmpty() && !authToken.isEmpty() && !isAnonymous);
        final String[] mapModes = SettingsUtil.getMapModes(isAuthenticated);
        final String[] mapModeName = SettingsUtil.getMapModeNames(isAuthenticated, MapFilterActivity.this);

        if (!ListFragment.PREF_MAP_NO_TILE.equals(showDiscovered)) {
            LinearLayout mainLayout = (LinearLayout) view.findViewById(R.id.show_map_discovered_since);
            mainLayout.setVisibility(View.VISIBLE);
        }

        SettingsUtil.doMapSpinner( R.id.show_discovered, ListFragment.PREF_SHOW_DISCOVERED,
                ListFragment.PREF_MAP_NO_TILE, mapModes, mapModeName, MapFilterActivity.this, view );

        int thisYear = Calendar.getInstance().get(Calendar.YEAR);
        List<Long> yearValueBase = new ArrayList<Long>();
        List<String> yearLabelBase = new ArrayList<String>();
        for (int i = 2001; i <= thisYear; i++) {
            yearValueBase.add((long)(i));
            yearLabelBase.add(Integer.toString(i));
        }
        SettingsUtil.doSpinner( R.id.networks_discovered_since_year, view, ListFragment.PREF_SHOW_DISCOVERED_SINCE,
                2001L, yearValueBase.toArray(new Long[0]),
                yearLabelBase.toArray(new String[0]), MapFilterActivity.this );

        MainActivity.prefBackedCheckBox(this , view, R.id.showinvert,
                MappingFragment.MAP_DIALOG_PREFIX + ListFragment.PREF_MAPF_INVERT, false );
        MainActivity.prefBackedCheckBox( this, view, R.id.showopen,
                MappingFragment.MAP_DIALOG_PREFIX + ListFragment.PREF_MAPF_OPEN, true );
        MainActivity.prefBackedCheckBox( this, view, R.id.showwep,
                MappingFragment.MAP_DIALOG_PREFIX + ListFragment.PREF_MAPF_WEP, true );
        MainActivity.prefBackedCheckBox( this, view, R.id.showwpa,
                MappingFragment.MAP_DIALOG_PREFIX + ListFragment.PREF_MAPF_WPA, true );
        MainActivity.prefBackedCheckBox( this, view, R.id.showcell,
                MappingFragment.MAP_DIALOG_PREFIX + ListFragment.PREF_MAPF_CELL, true );
        MainActivity.prefBackedCheckBox( this, view, R.id.enabled,
                MappingFragment.MAP_DIALOG_PREFIX + ListFragment.PREF_MAPF_ENABLED, true );
    }
}
