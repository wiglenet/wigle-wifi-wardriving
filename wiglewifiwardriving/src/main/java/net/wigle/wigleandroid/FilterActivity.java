package net.wigle.wigleandroid;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.v7.app.ActionBarActivity;

import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;

/**
 * Building a filter activity for the network list
 * Created by arkasha on 8/1/17.
 */

@SuppressWarnings("deprecation")
public class FilterActivity extends ActionBarActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        final SharedPreferences prefs = this.getSharedPreferences(ListFragment.SHARED_PREFS, 0);
        final SharedPreferences.Editor editor = prefs.edit();
        setContentView(R.layout.filtersettings);

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.HONEYCOMB) {
            final android.support.v7.app.ActionBar actionBar = getSupportActionBar();
            if (actionBar != null) {
                actionBar.setDisplayHomeAsUpEnabled(true);
            }
        }
        View view = findViewById(android.R.id.content);
        MainActivity.info("Filter Fragment Selected");
        final EditText regex = (EditText) findViewById( R.id.edit_regex );
        final String regexKey = ListFragment.FILTER_PREF_PREFIX + ListFragment.PREF_MAPF_REGEX;
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

        MainActivity.prefBackedCheckBox(this , view, R.id.showinvert,
                ListFragment.FILTER_PREF_PREFIX + ListFragment.PREF_MAPF_INVERT, false );
        MainActivity.prefBackedCheckBox( this, view, R.id.showopen,
                ListFragment.FILTER_PREF_PREFIX + ListFragment.PREF_MAPF_OPEN, true );
        MainActivity.prefBackedCheckBox( this, view, R.id.showwep,
                ListFragment.FILTER_PREF_PREFIX + ListFragment.PREF_MAPF_WEP, true );
        MainActivity.prefBackedCheckBox( this, view, R.id.showwpa,
                ListFragment.FILTER_PREF_PREFIX + ListFragment.PREF_MAPF_WPA, true );
        MainActivity.prefBackedCheckBox( this, view, R.id.showcell,
                ListFragment.FILTER_PREF_PREFIX + ListFragment.PREF_MAPF_CELL, true );
        MainActivity.prefBackedCheckBox( this, view, R.id.enabled,
                ListFragment.FILTER_PREF_PREFIX + ListFragment.PREF_MAPF_ENABLED, true );
    }
}
